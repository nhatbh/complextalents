package com.complextalents.impl.elementalmage;

import com.complextalents.elemental.ElementalReaction;
import com.complextalents.elemental.ElementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Persistent data for the Elemental Mage origin.
 * Manages Prismatic Echoes, Apex Element, and Convergence parameters.
 */
public class PlayerElementalMageData implements IPlayerElementalMageData {

    private Player player;
    private int echoCount = 0;
    private ElementalReaction lastReaction = null;
    private ElementType apexElement = null;
    private float lockedHarmonyMultiplier = 0.0f;
    private float convergenceCritChance = 0.0f;
    private float convergenceCritDamage = 0.0f;

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public boolean addEcho(ElementalReaction reaction, ElementType element) {
        // Block echo accumulation if Convergence is active (locked)
        if (lockedHarmonyMultiplier > 0.0f || (player != null && player.hasEffect(com.complextalents.elemental.effects.ElementalEffects.HARMONIC_CONVERGENCE.get()))) {
            return false;
        }

        if (reaction != null && reaction != lastReaction) {
            this.lastReaction = reaction;
            if (this.echoCount < 3) {
                this.echoCount++;
            }
            if (player != null && !player.level().isClientSide) {
                sync();
                markDirty();
            }
            return true;
        }

        if (player != null && !player.level().isClientSide) {
            sync();
            markDirty();
        }
        return false;
    }

    @Override
    public void clearEchoes() {
        this.echoCount = 0;
        this.lastReaction = null;
        if (player != null && !player.level().isClientSide) {
            sync();
            markDirty();
        }
    }

    @Override
    public int getEchoCount() {
        return echoCount;
    }

    @Override
    public void setEchoCount(int count) {
        this.echoCount = Math.max(0, Math.min(3, count));
    }

    @Override
    public ElementalReaction getLastReaction() {
        return lastReaction;
    }

    @Override
    public void setLastReaction(ElementalReaction reaction) {
        this.lastReaction = reaction;
    }

    @Override
    public ElementType getApexElement() {
        return apexElement;
    }

    @Override
    public void setApexElement(ElementType element) {
        boolean isConvergenceActive = lockedHarmonyMultiplier > 0.0f || (player != null && player.hasEffect(com.complextalents.elemental.effects.ElementalEffects.HARMONIC_CONVERGENCE.get()));
        if (isConvergenceActive && element != null) {
            return;
        }
        if (this.apexElement != element) {
            this.apexElement = element;
            if (player != null && !player.level().isClientSide) {
                sync();
                markDirty();
            }
        }
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
    public float getEffectiveHarmonyMultiplier() {
        if (lockedHarmonyMultiplier > 0.0f) {
            return lockedHarmonyMultiplier;
        }
        return 1.0f; // No passive multiplier while holding Echoes
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
        tag.putInt("echo_count", echoCount);
        if (lastReaction != null) {
            tag.putString("last_reaction", lastReaction.name());
        }
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
        this.echoCount = nbt.getInt("echo_count");
        if (nbt.contains("last_reaction")) {
            try {
                this.lastReaction = ElementalReaction.valueOf(nbt.getString("last_reaction"));
            } catch (Exception ignored) {
                this.lastReaction = null;
            }
        } else {
            this.lastReaction = null;
        }

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
