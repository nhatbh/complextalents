package com.complextalents.impl.highpriest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Interface for High Priest Faith data capability.
 */
public interface IPlayerFaithData extends INBTSerializable<CompoundTag> {
    double getFaith();
    void setFaith(double faith);
    void addFaith(double amount);
    void sync();
}
