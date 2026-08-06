package com.complextalents.impl.elementalmage;

import com.complextalents.elemental.ElementalReaction;
import com.complextalents.elemental.ElementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Interface for Elemental Mage Prismatic Echoes and Convergence data capability.
 */
public interface IPlayerElementalMageData extends INBTSerializable<CompoundTag> {

    /**
     * Attempts to add a new Echo stack upon reaction activation:
     * - Adds +1 Echo stack (max 3) if reaction != lastReaction
     * - Updates apexElement to element
     * - Does not add stacks if Convergence is active (locked)
     *
     * @return true if an Echo stack was gained
     */
    boolean addEcho(ElementalReaction reaction, ElementType element);

    void clearEchoes();

    int getEchoCount();

    void setEchoCount(int count);

    ElementalReaction getLastReaction();

    void setLastReaction(ElementalReaction reaction);

    ElementType getApexElement();

    void setApexElement(ElementType element);

    float getLockedHarmonyMultiplier();

    void setLockedHarmonyMultiplier(float multiplier);

    /**
     * Returns locked Harmony Multiplier if > 0 (during Convergence), else 1.0f (no passive multiplier).
     */
    float getEffectiveHarmonyMultiplier();

    float getConvergenceCritChance();

    void setConvergenceCritChance(float chance);

    float getConvergenceCritDamage();

    void setConvergenceCritDamage(float damage);

    void sync();
}
