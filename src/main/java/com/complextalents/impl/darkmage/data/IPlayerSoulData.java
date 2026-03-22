package com.complextalents.impl.darkmage.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Interface for Dark Mage soul data capability.
 */
public interface IPlayerSoulData extends INBTSerializable<CompoundTag> {
    double getSouls();
    void setSouls(double souls);
    void addSouls(double amount);
    double loseSouls(double percentage);
    void sync();
}
