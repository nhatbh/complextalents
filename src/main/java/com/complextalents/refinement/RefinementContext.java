package com.complextalents.refinement;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.item.ItemStack;

public class RefinementContext {
    private static final ThreadLocal<ItemStack> CURRENT_CONTEXT_STACK = new ThreadLocal<>();
    private static final ThreadLocal<AbstractSpell> CURRENT_CONTEXT_SPELL = new ThreadLocal<>();

    public static void setCurrentContextStack(ItemStack stack) {
        CURRENT_CONTEXT_STACK.set(stack);
    }

    public static ItemStack getCurrentContextStack() {
        ItemStack stack = CURRENT_CONTEXT_STACK.get();
        return stack != null ? stack : ItemStack.EMPTY;
    }

    public static void clearCurrentContextStack() {
        CURRENT_CONTEXT_STACK.remove();
    }

    public static void setCurrentContextSpell(AbstractSpell spell) {
        CURRENT_CONTEXT_SPELL.set(spell);
    }

    public static AbstractSpell getCurrentContextSpell() {
        return CURRENT_CONTEXT_SPELL.get();
    }

    public static void clearCurrentContextSpell() {
        CURRENT_CONTEXT_SPELL.remove();
    }
}
