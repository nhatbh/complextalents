package com.complextalents.impl.spellblade;

import com.complextalents.spellmastery.SpellSchool;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Interface for Spellblade capability data tracking active elemental imbue,
 * enhanced attack duration (during Overcharge), single-strike charges (outside Overcharge),
 * and Overcharge stance window.
 */
public interface IPlayerSpellbladeData extends INBTSerializable<CompoundTag> {

    SpellSchool getActiveElement();

    void setActiveElement(SpellSchool school);

    int getEnhancedAttackTicks();

    void setEnhancedAttackTicks(int ticks);

    boolean hasImbueCharge();

    void setHasImbueCharge(boolean charge);

    int getOverchargeTicks();

    void setOverchargeTicks(int ticks);

    boolean isOverchargeStance();

    void setOverchargeStance(boolean active);

    boolean isOverchargeActive();

    float getVirtualMana();

    void setVirtualMana(float amount);

    void sync();
}
