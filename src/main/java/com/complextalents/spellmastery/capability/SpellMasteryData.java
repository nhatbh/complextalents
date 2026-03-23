package com.complextalents.spellmastery.capability;

import com.complextalents.network.PacketHandler;
import com.complextalents.spellmastery.network.SpellMasterySyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;
import com.complextalents.util.UUIDHelper;

/**
 * Implementation of the Spell Mastery capability.
 */
public class SpellMasteryData implements ISpellMasteryData {

    private Player player;
    private final Map<ResourceLocation, Integer> learnedSpells = new HashMap<>(); // SpellID -> Max Level Learned
    private final Map<ResourceLocation, Integer> purchasedMasteryLevels = new HashMap<>(); // SchoolID -> Tier
    private int totalSPSpentOnMastery = 0;
    private static final UUID MASTERY_SP_REWARD_UUID = UUIDHelper.generateAttributeModifierUUID("spell_mastery", "mastery_buyup_reward");

    public SpellMasteryData() {
        // Default constructor for global storage
    }

    public SpellMasteryData(Player player) {
        this.player = player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public int getMasteryLevel(ResourceLocation schoolId) {
        int commonCount = 0;
        int uncommonCount = 0;
        int rareCount = 0;
        int epicCount = 0;

        for (Map.Entry<ResourceLocation, Integer> entry : learnedSpells.entrySet()) {
            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry
                    .getSpell(entry.getKey());
            if (spell != null && spell.getSchoolType().getId().equals(schoolId)) {
                io.redspace.ironsspellbooks.api.spells.SpellRarity maxRarity = spell.getRarity(entry.getValue());
                if (maxRarity.getValue() >= io.redspace.ironsspellbooks.api.spells.SpellRarity.COMMON.getValue())
                    commonCount++;
                if (maxRarity.getValue() >= io.redspace.ironsspellbooks.api.spells.SpellRarity.UNCOMMON.getValue())
                    uncommonCount++;
                if (maxRarity.getValue() >= io.redspace.ironsspellbooks.api.spells.SpellRarity.RARE.getValue())
                    rareCount++;
                if (maxRarity.getValue() >= io.redspace.ironsspellbooks.api.spells.SpellRarity.EPIC.getValue())
                    epicCount++;
            }
        }

        int calculated = 0;
        if (commonCount >= 3) {
            if (uncommonCount >= 3) {
                if (rareCount >= 2) {
                    if (epicCount >= 2) {
                        calculated = 4; // Legendary unlocked
                    } else {
                        calculated = 3; // Epic unlocked
                    }
                } else {
                    calculated = 2; // Rare unlocked
                }
            } else {
                calculated = 1; // Uncommon unlocked
            }
        }
        
        return Math.max(calculated, purchasedMasteryLevels.getOrDefault(schoolId, 0));
    }

    @Override
    public void setMasteryLevel(ResourceLocation schoolId, int level) {
        // No longer used for dynamic calculation, but kept for interface compatibility
        if (player != null && !player.level().isClientSide) {
            sync();
        }
    }

    @Override
    public boolean isSpellLearned(ResourceLocation spellId, int level) {
        return learnedSpells.getOrDefault(spellId, 0) >= level;
    }

    @Override
    public void learnSpell(ResourceLocation spellId, int level) {
        int currentMax = learnedSpells.getOrDefault(spellId, 0);
        if (level > currentMax) {
            learnedSpells.put(spellId, level);
            if (player != null && !player.level().isClientSide) {
                sync();
            }
        }
    }

    @Override
    public void forgetSpell(ResourceLocation spellId) {
        learnedSpells.remove(spellId);
        if (player != null && !player.level().isClientSide) {
            sync();
        }
    }

    @Override
    public Set<ResourceLocation> getLearnedSpells() {
        return new HashSet<>(learnedSpells.keySet());
    }

    @Override
    public int getPurchasedMastery(ResourceLocation schoolId) {
        return purchasedMasteryLevels.getOrDefault(schoolId, 0);
    }

    @Override
    public void purchaseMastery(ResourceLocation schoolId, int tier, int cost) {
        purchasedMasteryLevels.put(schoolId, tier);
        totalSPSpentOnMastery += cost;
        if (player != null && !player.level().isClientSide) {
            applySpellPowerReward();
            sync();
        }
    }

    @Override
    public int getTotalSPSpentOnMastery() {
        return totalSPSpentOnMastery;
    }

    private void applySpellPowerReward() {
        if (player == null || player.level().isClientSide) return;
        
        Attribute spellPowerAttr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
        if (spellPowerAttr != null) {
            AttributeInstance instance = player.getAttribute(spellPowerAttr);
            if (instance != null) {
                instance.removeModifier(MASTERY_SP_REWARD_UUID);
                double reward = totalSPSpentOnMastery * 0.02;
                if (reward > 0) {
                    instance.addTransientModifier(new AttributeModifier(MASTERY_SP_REWARD_UUID, "Mastery Buy-up Reward", reward, AttributeModifier.Operation.ADDITION));
                }
            }
        }
    }

    @Override
    public Map<ResourceLocation, Integer> getAllMasteryLevels() {
        Map<ResourceLocation, Integer> allMastery = new HashMap<>();
        // Note: This could be slow if there are many schools, but usually there are <
        // 20
        io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValues().forEach(school -> {
            int level = getMasteryLevel(school.getId());
            if (level > 0)
                allMastery.put(school.getId(), level);
        });
        return allMastery;
    }

    @Override
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketHandler.sendTo(new SpellMasterySyncPacket(serializeNBT()), serverPlayer);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        
        CompoundTag spellsNbt = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : learnedSpells.entrySet()) {
            spellsNbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("LearnedSpellsMap", spellsNbt);
        
        CompoundTag purchasedNbt = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : purchasedMasteryLevels.entrySet()) {
            purchasedNbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("PurchasedMasteryMap", purchasedNbt);
        
        nbt.putInt("TotalSPSpentOnMastery", totalSPSpentOnMastery);
        
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        learnedSpells.clear();
        if (nbt.contains("LearnedSpellsMap")) {
            CompoundTag spellsNbt = nbt.getCompound("LearnedSpellsMap");
            for (String key : spellsNbt.getAllKeys()) {
                learnedSpells.put(ResourceLocation.parse(key), spellsNbt.getInt(key));
            }
        } else if (nbt.contains("LearnedSpells")) {
            // Migration from simple Set
            ListTag legacySpells = nbt.getList("LearnedSpells", Tag.TAG_STRING);
            for (int i = 0; i < legacySpells.size(); i++) {
                learnedSpells.put(ResourceLocation.parse(legacySpells.getString(i)), 1); // Assume min level 1
            }
        }
        
        purchasedMasteryLevels.clear();
        if (nbt.contains("PurchasedMasteryMap")) {
            CompoundTag purchasedNbt = nbt.getCompound("PurchasedMasteryMap");
            for (String key : purchasedNbt.getAllKeys()) {
                purchasedMasteryLevels.put(ResourceLocation.parse(key), purchasedNbt.getInt(key));
            }
        }
        
        totalSPSpentOnMastery = nbt.getInt("TotalSPSpentOnMastery");
        
        if (player != null && !player.level().isClientSide) {
            applySpellPowerReward();
        }
    }
}
