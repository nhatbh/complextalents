package com.complextalents.caseopening;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class CaseReward {
    private final ItemStack stack;
    private final CaseRarity rarity;
    private final int weight;
    private final Component customName;

    public CaseReward(ItemStack stack, CaseRarity rarity, int weight) {
        this(stack, rarity, weight, null);
    }

    public CaseReward(ItemStack stack, CaseRarity rarity, int weight, Component customName) {
        this.stack = stack.copy();
        this.rarity = rarity;
        this.weight = weight;
        this.customName = customName;
    }

    public ItemStack getStack() {
        return stack.copy();
    }

    public CaseRarity getRarity() {
        return rarity;
    }

    public int getWeight() {
        return weight;
    }

    public Component getDisplayName() {
        if (customName != null) {
            return customName;
        }
        return stack.getHoverName();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(stack);
        buf.writeEnum(rarity);
        buf.writeVarInt(weight);
        buf.writeBoolean(customName != null);
        if (customName != null) {
            buf.writeComponent(customName);
        }
    }

    public static CaseReward decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        CaseRarity rarity = buf.readEnum(CaseRarity.class);
        int weight = buf.readVarInt();
        Component customName = null;
        if (buf.readBoolean()) {
            customName = buf.readComponent();
        }
        return new CaseReward(stack, rarity, weight, customName);
    }
}
