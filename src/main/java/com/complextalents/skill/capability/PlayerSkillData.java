package com.complextalents.skill.capability;

import com.complextalents.network.PacketHandler;
import com.complextalents.skill.Skill;
import com.complextalents.skill.SkillRegistry;
import com.complextalents.skill.network.SkillCooldownSyncPacket;
import com.complextalents.skill.network.SkillDataSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of player skill data capability.
 */
public class PlayerSkillData implements IPlayerSkillData, net.minecraftforge.common.util.INBTSerializable<CompoundTag> {

    private ServerPlayer player;
    private final ResourceLocation[] skillSlots = new ResourceLocation[5];
    private final Map<ResourceLocation, Long> activeCooldowns = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Long> passiveCooldowns = new ConcurrentHashMap<>();
    private final Set<ResourceLocation> activeToggles = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<ResourceLocation, Long> toggleActivationTimes = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Integer> skillLevels = new ConcurrentHashMap<>();
    private ResourceLocation activeForm = null;
    private long formExpiration = 0;

    public PlayerSkillData() {
        Arrays.fill(skillSlots, null);
    }

    public PlayerSkillData(ServerPlayer player) {
        this();
        this.player = player;
    }

    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    @Nullable
    public ResourceLocation getSkillInSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 5) return null;
        return skillSlots[slotIndex];
    }

    @Override
    public void setSkillInSlot(int slotIndex, @Nullable ResourceLocation skillId) {
        if (slotIndex < 0 || slotIndex >= 5) return;
        skillSlots[slotIndex] = skillId;
        sync();
    }

    @Override
    public ResourceLocation[] getAssignedSlots() {
        return Arrays.copyOf(skillSlots, 5);
    }

    @Override
    public boolean isOnCooldown(ResourceLocation skillId) {
        if (player == null) return false;
        Long expiration = activeCooldowns.get(skillId);
        if (expiration == null) return false;
        if (player.level().getGameTime() >= expiration) {
            activeCooldowns.remove(skillId);
            return false;
        }
        return true;
    }

    @Override
    public double getCooldown(ResourceLocation skillId) {
        if (player == null) return 0;
        Long expiration = activeCooldowns.get(skillId);
        if (expiration == null) return 0;
        long remaining = expiration - player.level().getGameTime();
        if (remaining <= 0) {
            activeCooldowns.remove(skillId);
            return 0;
        }
        return remaining / 20.0;
    }

    @Override
    public void setCooldown(ResourceLocation skillId, double seconds) {
        if (player == null) return;
        activeCooldowns.put(skillId, player.level().getGameTime() + (long)(seconds * 20));
        syncCooldowns();
    }

    @Override
    public void clearCooldown(ResourceLocation skillId) {
        activeCooldowns.remove(skillId);
        syncCooldowns();
    }

    @Override
    public Long getCooldownExpiration(ResourceLocation skillId) {
        if (player == null) return null;
        Long expiration = activeCooldowns.get(skillId);
        if (expiration == null) return null;
        if (player.level().getGameTime() >= expiration) {
            activeCooldowns.remove(skillId);
            return null;
        }
        return expiration;
    }

    @Override
    public boolean isPassiveOnCooldown(ResourceLocation skillId) {
        if (player == null) return false;
        Long expiration = passiveCooldowns.get(skillId);
        if (expiration == null) return false;
        if (player.level().getGameTime() >= expiration) {
            passiveCooldowns.remove(skillId);
            return false;
        }
        return true;
    }

    @Override
    public double getPassiveCooldown(ResourceLocation skillId) {
        if (player == null) return 0;
        Long expiration = passiveCooldowns.get(skillId);
        if (expiration == null) return 0;
        long remaining = expiration - player.level().getGameTime();
        if (remaining <= 0) {
            passiveCooldowns.remove(skillId);
            return 0;
        }
        return remaining / 20.0;
    }

    @Override
    public void setPassiveCooldown(ResourceLocation skillId, double seconds) {
        if (player == null) return;
        passiveCooldowns.put(skillId, player.level().getGameTime() + (long)(seconds * 20));
    }

    @Override
    public void clearPassiveCooldown(ResourceLocation skillId) {
        passiveCooldowns.remove(skillId);
    }

    @Override
    public boolean isToggleActive(ResourceLocation skillId) {
        return activeToggles.contains(skillId);
    }

    @Override
    public void setToggleActive(ResourceLocation skillId, boolean active) {
        if (active) {
            activeToggles.add(skillId);
            if (player != null) toggleActivationTimes.put(skillId, player.level().getGameTime());
        } else {
            activeToggles.remove(skillId);
            toggleActivationTimes.remove(skillId);
        }
        sync();
    }

    @Override
    public long getToggleActivationTime(ResourceLocation skillId) {
        return toggleActivationTimes.getOrDefault(skillId, 0L);
    }

    @Override
    public void setToggleActivationTime(ResourceLocation skillId, long gameTime) {
        toggleActivationTimes.put(skillId, gameTime);
    }

    @Override
    public void tick() {
        if (player == null || player.level().isClientSide) return;
        // Basic tick logic for toggles and forms
        long currentTime = player.level().getGameTime();
        
        for (ResourceLocation skillId : activeToggles) {
            Skill skill = SkillRegistry.getInstance().getSkill(skillId);
            if (skill == null) continue;
            
            // Implementation of toggle cost/duration would go here, 
            // but for now we keep it simple to ensure compilation.
        }

        if (activeForm != null && currentTime >= formExpiration) {
            com.complextalents.skill.form.SkillFormManager.deactivateForm(player);
        }
    }

    @Override
    public void sync() {
        if (player != null) {
            PacketHandler.sendTo(new SkillDataSyncPacket(player.getUUID(), getAssignedSlots(), new HashMap<>(skillLevels)), player);
        }
    }

    public void syncCooldowns() {
        if (player != null) {
            PacketHandler.sendTo(new SkillCooldownSyncPacket(new HashMap<>(activeCooldowns), player.level().getGameTime()), player);
        }
    }

    @Override
    public void clear() {
        Arrays.fill(skillSlots, null);
        activeCooldowns.clear();
        passiveCooldowns.clear();
        activeToggles.clear();
        toggleActivationTimes.clear();
        skillLevels.clear();
        activeForm = null;
        formExpiration = 0;
        sync();
    }

    @Override
    public int getSkillLevel(ResourceLocation skillId) {
        return skillLevels.getOrDefault(skillId, 0);
    }

    @Override
    public void setSkillLevel(ResourceLocation skillId, int level) {
        if (level <= 0) skillLevels.remove(skillId);
        else skillLevels.put(skillId, level);
        sync();
    }

    @Override
    public ResourceLocation getActiveForm() { return activeForm; }

    @Override
    public void setActiveForm(ResourceLocation formSkillId) {
        this.activeForm = formSkillId;
        sync();
    }

    @Override
    public long getFormExpiration() { return formExpiration; }

    @Override
    public void setFormExpiration(long expirationTime) { this.formExpiration = expirationTime; }

    @Override
    public java.util.Set<ResourceLocation> getAllLearnedSkills() {
        return skillLevels.keySet();
    }

    @Override
    public void copyFrom(IPlayerSkillData other) {
        ResourceLocation[] otherSlots = other.getAssignedSlots();
        System.arraycopy(otherSlots, 0, skillSlots, 0, Math.min(skillSlots.length, otherSlots.length));
        
        if (other instanceof PlayerSkillData o) {
            this.skillLevels.clear();
            this.skillLevels.putAll(o.skillLevels);
        }
        sync();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag slotsList = new ListTag();
        for (int i = 0; i < 5; i++) {
            if (skillSlots[i] != null) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("slot", i);
                slotTag.putString("skill", skillSlots[i].toString());
                slotsList.add(slotTag);
            }
        }
        tag.put("slots", slotsList);

        ListTag levelsList = new ListTag();
        for (var entry : skillLevels.entrySet()) {
            CompoundTag levelTag = new CompoundTag();
            levelTag.putString("skill", entry.getKey().toString());
            levelTag.putInt("level", entry.getValue());
            levelsList.add(levelTag);
        }
        tag.put("skillLevels", levelsList);

        if (activeForm != null) {
            tag.putString("activeForm", activeForm.toString());
            tag.putLong("formExpiration", formExpiration);
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        Arrays.fill(skillSlots, null);
        if (tag.contains("slots")) {
            ListTag list = tag.getList("slots", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag s = list.getCompound(i);
                int slot = s.getInt("slot");
                if (slot >= 0 && slot < 5) skillSlots[slot] = ResourceLocation.tryParse(s.getString("skill"));
            }
        }

        skillLevels.clear();
        if (tag.contains("skillLevels")) {
            ListTag list = tag.getList("skillLevels", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag l = list.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(l.getString("skill"));
                if (id != null) skillLevels.put(id, l.getInt("level"));
            }
        }

        if (tag.contains("activeForm")) {
            activeForm = ResourceLocation.tryParse(tag.getString("activeForm"));
            formExpiration = tag.getLong("formExpiration");
        }
    }
}
