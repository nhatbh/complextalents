package com.complextalents.mixin;

import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slot.class)
public class SlotMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void complextalents$onTakeSlot(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.containerMenu instanceof InscriptionTableMenu menu) {
            Slot slot = (Slot) (Object) this;
            if (slot.container == menu.getResultSlot().container) {
                ItemStack bookStack = menu.getSpellBookSlot().getItem();
                if (!bookStack.isEmpty()) {
                    CompoundTag bookTag = bookStack.getTag();
                    if (bookTag != null && bookTag.contains("RefinedSpells")) {
                        CompoundTag refinedSpells = bookTag.getCompound("RefinedSpells");
                        ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                        if (bookContainer != null) {
                            java.util.Set<String> keys = new java.util.HashSet<>(refinedSpells.getAllKeys());
                            for (String key : keys) {
                                boolean found = false;
                                for (SpellSlot spellSlot : bookContainer.getAllSpells()) {
                                    if (spellSlot != null && spellSlot.getSpell() != null && spellSlot.getSpell().getSpellId().equals(key)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    refinedSpells.remove(key);
                                }
                            }
                            if (refinedSpells.isEmpty()) {
                                bookTag.remove("RefinedSpells");
                            }
                        }
                    }
                }
            }
        }
    }
}
