package com.complextalents.mixin;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import com.complextalents.refinement.MagicRefinementManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InscriptionTableMenu.class, remap = false)
public class InscriptionTableMenuMixin {

    @Shadow
    private int selectedSpellIndex;

    @Inject(method = "doInscription", at = @At("HEAD"))
    private void complextalents$beforeInscription(int selectedIndex, CallbackInfo ci) {
        InscriptionTableMenu menu = (InscriptionTableMenu) (Object) this;
        ItemStack spellBookItemStack = menu.getSpellBookSlot().getItem();
        ItemStack scrollItemStack = menu.getScrollSlot().getItem();

        if (spellBookItemStack.getItem() instanceof SpellBook && scrollItemStack.getItem() instanceof Scroll) {
            var scrollContainer = ISpellContainer.get(scrollItemStack);
            if (scrollContainer != null && !scrollContainer.isEmpty()) {
                var scrollSlot = scrollContainer.getSpellAtIndex(0);
                if (scrollSlot != null && scrollSlot.getSpell() != null) {
                    String spellId = scrollSlot.getSpell().getSpellId();
                    if (MagicRefinementManager.isScroll(scrollItemStack)) {
                        int currentXp = MagicRefinementManager.getRefineXp(scrollItemStack);
                        if (currentXp > 0) {
                            CompoundTag scrollTag = scrollItemStack.getTag();
                            if (scrollTag != null) {
                                CompoundTag bookTag = spellBookItemStack.getOrCreateTag();
                                CompoundTag refinedSpells;
                                if (bookTag.contains("RefinedSpells")) {
                                    refinedSpells = bookTag.getCompound("RefinedSpells");
                                } else {
                                    refinedSpells = new CompoundTag();
                                }
                                
                                CompoundTag spellRefineData = new CompoundTag();
                                if (scrollTag.contains("RefineSubstats")) {
                                    spellRefineData.put("RefineSubstats", scrollTag.getCompound("RefineSubstats").copy());
                                }
                                if (scrollTag.contains("RefineRank")) {
                                    spellRefineData.putInt("RefineRank", scrollTag.getInt("RefineRank"));
                                }
                                if (scrollTag.contains("RefineHistory")) {
                                    spellRefineData.put("RefineHistory", scrollTag.getList("RefineHistory", 8).copy());
                                }
                                
                                spellRefineData.putInt("RefineXP", currentXp);
                                spellRefineData.putInt("StartingTier", MagicRefinementManager.getMagicItemTier(scrollItemStack));
                                spellRefineData.putLong("RefineSeed", scrollTag.getLong("RefineSeed"));
                                
                                refinedSpells.put(spellId, spellRefineData);
                                bookTag.put("RefinedSpells", refinedSpells);
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "setupResultSlot", at = @At("RETURN"))
    private void complextalents$afterSetupResultSlot(CallbackInfo ci) {
        InscriptionTableMenu menu = (InscriptionTableMenu) (Object) this;
        ItemStack resultStack = menu.getResultSlot().getItem();
        ItemStack spellBookStack = menu.getSpellBookSlot().getItem();
        if (!resultStack.isEmpty() && !spellBookStack.isEmpty() && this.selectedSpellIndex >= 0) {
            ISpellContainer spellList = ISpellContainer.get(spellBookStack);
            SpellData spellData = spellList.getSpellAtIndex(this.selectedSpellIndex);
            if (spellData != SpellData.EMPTY && spellData.canRemove()) {
                String spellId = spellData.getSpell().getSpellId();
                CompoundTag bookTag = spellBookStack.getTag();
                if (bookTag != null && bookTag.contains("RefinedSpells")) {
                    CompoundTag refinedSpells = bookTag.getCompound("RefinedSpells");
                    if (refinedSpells.contains(spellId)) {
                        CompoundTag spellRefineData = refinedSpells.getCompound(spellId);
                        CompoundTag resultTag = resultStack.getOrCreateTag();
                        
                        // Copy all refinement NBT to the output scroll
                        if (spellRefineData.contains("RefineXP")) {
                            resultTag.putInt("RefineXP", spellRefineData.getInt("RefineXP"));
                        }
                        if (spellRefineData.contains("StartingTier")) {
                            resultTag.putInt("StartingTier", spellRefineData.getInt("StartingTier"));
                        }
                        if (spellRefineData.contains("RefineSeed")) {
                            resultTag.putLong("RefineSeed", spellRefineData.getLong("RefineSeed"));
                        }
                        if (spellRefineData.contains("RefineSubstats")) {
                            resultTag.put("RefineSubstats", spellRefineData.getCompound("RefineSubstats").copy());
                        }
                        if (spellRefineData.contains("RefineRank")) {
                            resultTag.putInt("RefineRank", spellRefineData.getInt("RefineRank"));
                        }
                        if (spellRefineData.contains("RefineHistory")) {
                            resultTag.put("RefineHistory", spellRefineData.getList("RefineHistory", 8).copy());
                        }
                    }
                }
            }
        }
    }
}
