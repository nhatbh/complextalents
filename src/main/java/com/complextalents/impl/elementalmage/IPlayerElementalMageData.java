package com.complextalents.impl.elementalmage;

import com.complextalents.elemental.ElementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.List;

/**
 * Interface for Elemental Mage Prismatic Echoes and Convergence data capability.
 */
public interface IPlayerElementalMageData extends INBTSerializable<CompoundTag> {

    List<ElementType> getEchoes();

    /**
     * Attempts to add a new Echo upon reaction activation:
     * - Pushed to the top (index 0) if not duplicated with the latest element (index 0)
     * - Max 2 Echoes of the same element (removes oldest stack of this element if > 2)
     * - Max 6 total Echoes (removes oldest combo stack if > 6)
     *
     * @return true if an Echo was successfully added
     */
    boolean addEcho(ElementType element);

    void clearEchoes();

    void setEchoes(List<ElementType> echoes);

    int getEchoCount();

    int getEchoCountOf(ElementType element);

    ElementType getLastDamageElement();

    void setLastDamageElement(ElementType element);

    long getLastDamageTick();

    void setLastDamageTick(long tick);

    ElementType getApexElement();

    void setApexElement(ElementType element);

    float getLockedHarmonyMultiplier();

    void setLockedHarmonyMultiplier(float multiplier);

    /**
     * Calculates the live Harmony Multiplier based on total Echo count:
     * 0-1 Echoes -> 0.5x
     * 2 Echoes   -> 0.7x
     * 3 Echoes   -> 0.9x
     * 4 Echoes   -> 1.1x
     * 5 Echoes   -> 1.3x
     * 6 Echoes   -> 1.5x
     */
    float getLiveHarmonyMultiplier();

    /**
     * Returns locked Harmony Multiplier if > 0 (during Convergence), else live multiplier.
     */
    float getEffectiveHarmonyMultiplier();

    float getConvergenceCritChance();

    void setConvergenceCritChance(float chance);

    float getConvergenceCritDamage();

    void setConvergenceCritDamage(float damage);

    void sync();
}
