package com.complextalents.impl.elementalmage;

import com.complextalents.elemental.ElementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persistent data for the Elemental Mage origin.
 * Manages Prismatic Echoes, Apex Element, and Harmony Multipliers.
 */
public class PlayerElementalMageData implements IPlayerElementalMageData {

    private Player player;
    private final List<ElementType> echoes = new CopyOnWriteArrayList<>();
    private ElementType lastDamageElement = null;
    private long lastDamageTick = 0L;
    private ElementType apexElement = null;
    private float lockedHarmonyMultiplier = 0.0f;
    private float convergenceCritChance = 0.0f;
    private float convergenceCritDamage = 0.0f;

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public List<ElementType> getEchoes() {
        return Collections.unmodifiableList(echoes);
    }

    @Override
    public boolean addEcho(ElementType element) {
        if (element == null) return false;

        // Block echo accumulation if Convergence is active (locked)
        if (lockedHarmonyMultiplier > 0.0f || (player != null && player.hasEffect(com.complextalents.elemental.effects.ElementalEffects.HARMONIC_CONVERGENCE.get()))) {
            return false;
        }

        // 1. Check if duplicated with the latest element (top / index 0)
        ElementType latestElement = echoes.isEmpty() ? null : echoes.get(0);
        if (latestElement != null && latestElement == element) {
            return false;
        }

        // 2. Push element into combo array at the top (index 0)
        echoes.add(0, element);

        // 3. Check if stack count exceeds 2 for this element -> remove oldest stack of this element (last occurrence)
        if (getEchoCountOf(element) > 2) {
            int oldestIndex = echoes.lastIndexOf(element);
            if (oldestIndex != -1) {
                echoes.remove(oldestIndex);
            }
        }

        // 4. Check if total combo is greater than 6 -> remove oldest combo stack (last element)
        if (echoes.size() > 6) {
            echoes.remove(echoes.size() - 1);
        }

        this.lastDamageElement = element;

        if (player != null && !player.level().isClientSide) {
            sync();
            markDirty();
        }
        return true;
    }

    @Override
    public void clearEchoes() {
        echoes.clear();
        if (player != null && !player.level().isClientSide) {
            sync();
            markDirty();
        }
    }

    @Override
    public void setEchoes(List<ElementType> newEchoes) {
        echoes.clear();
        if (newEchoes != null) {
            echoes.addAll(newEchoes);
        }
    }

    @Override
    public int getEchoCount() {
        return echoes.size();
    }

    @Override
    public int getEchoCountOf(ElementType element) {
        int count = 0;
        for (ElementType type : echoes) {
            if (type == element) count++;
        }
        return count;
    }

    @Override
    public ElementType getLastDamageElement() {
        return lastDamageElement;
    }

    @Override
    public void setLastDamageElement(ElementType element) {
        this.lastDamageElement = element;
    }

    @Override
    public long getLastDamageTick() {
        return lastDamageTick;
    }

    @Override
    public void setLastDamageTick(long tick) {
        this.lastDamageTick = tick;
    }

    @Override
    public ElementType getApexElement() {
        if (lockedHarmonyMultiplier <= 0.0f) {
            return null;
        }
        return apexElement;
    }

    @Override
    public void setApexElement(ElementType element) {
        this.apexElement = element;
        markDirty();
    }

    @Override
    public float getLockedHarmonyMultiplier() {
        return lockedHarmonyMultiplier;
    }

    @Override
    public void setLockedHarmonyMultiplier(float multiplier) {
        this.lockedHarmonyMultiplier = multiplier;
        markDirty();
    }

    @Override
    public float getLiveHarmonyMultiplier() {
        int count = echoes.size();
        return switch (count) {
            case 0, 1 -> 0.5f;
            case 2 -> 0.7f;
            case 3 -> 0.9f;
            case 4 -> 1.1f;
            case 5 -> 1.3f;
            default -> 1.5f; // 6+ echoes
        };
    }

    @Override
    public float getEffectiveHarmonyMultiplier() {
        if (lockedHarmonyMultiplier > 0.0f) {
            return lockedHarmonyMultiplier;
        }
        return getLiveHarmonyMultiplier();
    }

    @Override
    public float getConvergenceCritChance() {
        return convergenceCritChance;
    }

    @Override
    public void setConvergenceCritChance(float chance) {
        this.convergenceCritChance = chance;
        markDirty();
    }

    @Override
    public float getConvergenceCritDamage() {
        return convergenceCritDamage;
    }

    @Override
    public void setConvergenceCritDamage(float damage) {
        this.convergenceCritDamage = damage;
        markDirty();
    }

    @Override
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            ElementalMageData.syncToClient(serverPlayer);
        }
    }

    private void markDirty() {
        if (player instanceof ServerPlayer serverPlayer) {
            com.complextalents.persistence.PlayerPersistentData.get(serverPlayer.getServer()).setDirty();
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag echoList = new ListTag();
        for (ElementType type : echoes) {
            echoList.add(StringTag.valueOf(type.name()));
        }
        tag.put("prismatic_echoes", echoList);

        if (lastDamageElement != null) {
            tag.putString("last_damage_element", lastDamageElement.name());
        }
        tag.putLong("last_damage_tick", lastDamageTick);

        if (apexElement != null) {
            tag.putString("apex_element", apexElement.name());
        }
        tag.putFloat("locked_harmony_multiplier", lockedHarmonyMultiplier);
        tag.putFloat("convergence_crit_chance", convergenceCritChance);
        tag.putFloat("convergence_crit_damage", convergenceCritDamage);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        echoes.clear();
        if (nbt.contains("prismatic_echoes", Tag.TAG_LIST)) {
            ListTag echoList = nbt.getList("prismatic_echoes", Tag.TAG_STRING);
            for (int i = 0; i < echoList.size(); i++) {
                try {
                    echoes.add(ElementType.valueOf(echoList.getString(i)));
                } catch (Exception ignored) {}
            }
        }

        if (nbt.contains("last_damage_element")) {
            try {
                this.lastDamageElement = ElementType.valueOf(nbt.getString("last_damage_element"));
            } catch (Exception ignored) {
                this.lastDamageElement = null;
            }
        } else {
            this.lastDamageElement = null;
        }

        this.lastDamageTick = nbt.getLong("last_damage_tick");

        if (nbt.contains("apex_element")) {
            try {
                this.apexElement = ElementType.valueOf(nbt.getString("apex_element"));
            } catch (Exception ignored) {
                this.apexElement = null;
            }
        } else {
            this.apexElement = null;
        }

        this.lockedHarmonyMultiplier = nbt.getFloat("locked_harmony_multiplier");
        this.convergenceCritChance = nbt.getFloat("convergence_crit_chance");
        this.convergenceCritDamage = nbt.getFloat("convergence_crit_damage");
    }
}
