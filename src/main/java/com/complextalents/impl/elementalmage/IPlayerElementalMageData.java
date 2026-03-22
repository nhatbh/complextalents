package com.complextalents.impl.elementalmage;

import com.complextalents.elemental.ElementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import java.util.Map;

/**
 * Interface for Elemental Mage stats data capability.
 */
public interface IPlayerElementalMageData extends INBTSerializable<CompoundTag> {
    float getStat(ElementType element);
    void setStat(ElementType element, float value);
    Map<ElementType, Float> getAllStats();
    void sync();
}
