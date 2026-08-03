package com.complextalents.refinement;

import com.complextalents.TalentsMod;
import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.item.MagicAugmentItem;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import io.redspace.ironsspellbooks.api.events.InscribeSpellEvent;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellAugmentEventHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !SpellAugmentRecipe.isSpellItem(stack) || isSpellBook(stack))
            return;

        try {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container == null || container.isEmpty())
                return;

            List<SpellSlot> activeSpells = container.getActiveSpells();
            for (SpellSlot slot : activeSpells) {
                int slotIdx = slot.index();
                List<CompoundTag> augments = SpellAugmentRecipe.getAugments(stack, slotIdx);
                int spellTier = Math.max(1, Math.min(5, slot.getSpell().getRarity(slot.getLevel()).getValue() + 1));
                int maxSockets = Math.max(1, Math.min(5, spellTier));

                if (!augments.isEmpty() || activeSpells.size() == 1) {
                    event.getToolTip().add(Component.literal(""));
                    String prefix = activeSpells.size() > 1
                            ? "[" + slot.getSpell().getDisplayName(event.getEntity()).getString() + "] "
                            : "";
                    event.getToolTip()
                            .add(Component
                                    .literal(prefix + "Augment Sockets (" + augments.size() + "/" + maxSockets + "):")
                                    .withStyle(ChatFormatting.GOLD));

                    for (CompoundTag aug : augments) {
                        String typeStr = aug.getString("Type");
                        int tierOrdinal = aug.getInt("Tier");
                        CrateRarity rarity = CrateRarity.values()[Math.min(4, Math.max(0, tierOrdinal))];
                        try {
                            MagicAugmentItem.AugmentType type = MagicAugmentItem.AugmentType.valueOf(typeStr);
                            String bonus = MagicAugmentItem.getBonusText(type, rarity);
                            event.getToolTip()
                                    .add(Component.literal("  ◆ " + type.getDisplayName() + " Rune: ")
                                            .withStyle(ChatFormatting.AQUA)
                                            .append(Component.literal(bonus).withStyle(ChatFormatting.GREEN)));
                        } catch (Exception ignored) {
                        }
                    }

                    for (int i = augments.size(); i < maxSockets; i++) {
                        event.getToolTip()
                                .add(Component.literal("  ◇ Empty Socket").withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public static void onInscribeSpell(InscribeSpellEvent event) {
        Player player = event.getEntity();
        if (player != null && player.containerMenu instanceof InscriptionTableMenu menu) {
            ItemStack scrollStack = menu.getScrollSlot().getItem();
            ItemStack bookStack = menu.getSpellBookSlot().getItem();
            if (!scrollStack.isEmpty() && !bookStack.isEmpty()) {
                ISpellContainer scrollContainer = ISpellContainer.get(scrollStack);
                if (scrollContainer != null && !scrollContainer.isEmpty()) {
                    SpellData scrollSpell = scrollContainer.getSpellAtIndex(0);
                    if (scrollSpell != null && scrollSpell.getSpell() != null) {
                        // Auto-learn spell for player capability
                        player.getCapability(
                                com.complextalents.spellmastery.capability.SpellMasteryDataProvider.MASTERY_DATA)
                                .ifPresent(data -> {
                                    data.learnSpell(scrollSpell.getSpell().getSpellResource(), scrollSpell.getLevel());
                                });

                        List<CompoundTag> scrollAugs = SpellAugmentRecipe.getAugments(scrollStack, 0);
                        if (!scrollAugs.isEmpty()) {
                            ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                            if (bookContainer != null) {
                                int slotIndex = getSlotIndexForSpell(bookContainer, scrollSpell.getSpell());
                                if (slotIndex < 0)
                                    slotIndex = bookContainer.getNextAvailableIndex();
                                if (slotIndex >= 0) {
                                    SpellAugmentRecipe.setAugments(bookStack, slotIndex, scrollAugs);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerContainerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
            return;
        Player player = event.player;
        if (player.containerMenu instanceof InscriptionTableMenu menu) {
            ItemStack bookStack = menu.getSpellBookSlot().getItem();
            ItemStack resultStack = menu.getResultSlot().getItem();

            if (!bookStack.isEmpty() && !resultStack.isEmpty()) {
                ISpellContainer resultContainer = ISpellContainer.get(resultStack);
                ISpellContainer bookContainer = ISpellContainer.get(bookStack);
                if (resultContainer != null && !resultContainer.isEmpty() && bookContainer != null) {
                    SpellData resultSpell = resultContainer.getSpellAtIndex(0);
                    if (resultSpell != null && resultSpell.getSpell() != null) {
                        int slotIndex = getSlotIndexForSpell(bookContainer, resultSpell.getSpell());
                        if (slotIndex >= 0) {
                            List<CompoundTag> bookAugs = SpellAugmentRecipe.getAugments(bookStack, slotIndex);
                            if (!bookAugs.isEmpty()) {
                                List<CompoundTag> resultAugs = SpellAugmentRecipe.getAugments(resultStack, 0);
                                if (resultAugs.isEmpty()) {
                                    SpellAugmentRecipe.setAugments(resultStack, 0, bookAugs);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent event) {
        if (event.getEntity() == null || !(event.getSpellDamageSource().getEntity() instanceof Player player)) {
            return;
        }

        AbstractSpell spell = event.getSpellDamageSource().spell();
        ItemStack castingStack = getCastingItemStack(player, spell);
        if (castingStack.isEmpty())
            return;

        List<CompoundTag> augments = getAugmentsForSpell(castingStack, spell);
        if (augments.isEmpty())
            return;

        float powerDmgPercent = 0;
        float piercePercent = 0;
        float lifestealPercent = 0;

        for (CompoundTag aug : augments) {
            try {
                MagicAugmentItem.AugmentType type = MagicAugmentItem.AugmentType.valueOf(aug.getString("Type"));
                CrateRarity rarity = CrateRarity.values()[Math.min(4, Math.max(0, aug.getInt("Tier")))];
                int tLvl = rarity.ordinal() + 1;

                switch (type) {
                    case POWER -> powerDmgPercent += switch (tLvl) {
                        case 1 -> 5;
                        case 2 -> 10;
                        case 3 -> 15;
                        case 4 -> 22;
                        default -> 30;
                    };
                    case PIERCE -> piercePercent += switch (tLvl) {
                        case 3 -> 10;
                        case 4 -> 18;
                        default -> 25;
                    };
                    case VAMPIRISM -> lifestealPercent += switch (tLvl) {
                        case 3 -> 8;
                        case 4 -> 14;
                        default -> 20;
                    };
                    default -> {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        float currentAmount = event.getAmount();

        if (powerDmgPercent > 0) {
            currentAmount *= (1.0f + (powerDmgPercent / 100.0f));
        }

        if (piercePercent > 0) {
            currentAmount *= (1.0f + (piercePercent / 100.0f));
        }

        event.setAmount(currentAmount);

        if (lifestealPercent > 0 && currentAmount > 0) {
            float heal = currentAmount * (lifestealPercent / 100.0f);
            player.heal(heal);
        }
    }

    @SubscribeEvent
    public static void onChangeMana(ChangeManaEvent event) {
        if (event.getEntity() == null)
            return;
        Player player = event.getEntity();

        ItemStack castingStack = getCastingItemStack(player, null);
        if (castingStack.isEmpty())
            return;

        List<CompoundTag> augments = SpellAugmentRecipe.getAugments(castingStack, 0);
        if (augments.isEmpty())
            return;

        float manaSaverPercent = 0;
        for (CompoundTag aug : augments) {
            try {
                MagicAugmentItem.AugmentType type = MagicAugmentItem.AugmentType.valueOf(aug.getString("Type"));
                if (type == MagicAugmentItem.AugmentType.MANA_SAVER) {
                    CrateRarity rarity = CrateRarity.values()[Math.min(4, Math.max(0, aug.getInt("Tier")))];
                    int tLvl = rarity.ordinal() + 1;
                    manaSaverPercent += switch (tLvl) {
                        case 1 -> 6;
                        case 2 -> 12;
                        case 3 -> 18;
                        case 4 -> 24;
                        default -> 30;
                    };
                }
            } catch (Exception ignored) {
            }
        }

        if (manaSaverPercent > 0) {
            float oldMana = event.getOldMana();
            float newMana = event.getNewMana();
            if (newMana < oldMana) {
                float originalCost = oldMana - newMana;
                float reducedCost = Math.max(0, originalCost * (1.0f - (manaSaverPercent / 100.0f)));
                event.setNewMana(oldMana - reducedCost);
            }
        }
    }

    @SubscribeEvent
    public static void onSpellOnCast(SpellOnCastEvent event) {
        if (event.getEntity() == null)
            return;
        Player player = event.getEntity();

        AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(event.getSpellId());
        ItemStack castingStack = getCastingItemStack(player, spell);
        if (castingStack.isEmpty())
            return;

        List<CompoundTag> augments = getAugmentsForSpell(castingStack, spell);
        if (augments.isEmpty())
            return;

        boolean hasRecast = false;
        for (CompoundTag aug : augments) {
            try {
                MagicAugmentItem.AugmentType type = MagicAugmentItem.AugmentType.valueOf(aug.getString("Type"));
                if (type == MagicAugmentItem.AugmentType.RECAST) {
                    hasRecast = true;
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        if (hasRecast && player.getRandom().nextFloat() < 0.35f) {
            try {
                MagicData magicData = MagicData.getPlayerMagicData(player);
                if (magicData != null && magicData.getPlayerCooldowns() != null) {
                    magicData.getPlayerCooldowns().clearCooldowns();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static List<CompoundTag> getAugmentsForSpell(ItemStack stack, AbstractSpell spell) {
        if (spell != null && ISpellContainer.isSpellContainer(stack)) {
            try {
                ISpellContainer container = ISpellContainer.get(stack);
                if (container != null) {
                    int slotIndex = getSlotIndexForSpell(container, spell);
                    if (slotIndex >= 0) {
                        return SpellAugmentRecipe.getAugments(stack, slotIndex);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return SpellAugmentRecipe.getAugments(stack, 0);
    }

    private static int getSlotIndexForSpell(ISpellContainer container, AbstractSpell spell) {
        if (container == null || spell == null)
            return -1;
        try {
            int idx = container.getIndexForSpell(spell);
            if (idx >= 0)
                return idx;
        } catch (Exception ignored) {
        }

        try {
            for (SpellSlot slot : container.getActiveSpells()) {
                if (slot.getSpell() != null && slot.getSpell().getSpellId().equals(spell.getSpellId())) {
                    return slot.index();
                }
            }
        } catch (Exception ignored) {
        }

        return -1;
    }

    public static ItemStack getCastingItemStack(Player player, AbstractSpell spell) {
        if (player == null)
            return ItemStack.EMPTY;

        // 1. Check Curios Slot (Top Priority)
        ItemStack curiosBook = getCuriosSpellbook(player, spell);
        if (!curiosBook.isEmpty())
            return curiosBook;

        // 2. Check Main Hand
        ItemStack mainHand = player.getMainHandItem();
        if (isSpellItemWithSpell(mainHand, spell))
            return mainHand;

        // 3. Check Off Hand
        ItemStack offHand = player.getOffhandItem();
        if (isSpellItemWithSpell(offHand, spell))
            return offHand;

        // 4. Check Inventory Items
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (isSpellItemWithSpell(invStack, spell))
                return invStack;
        }

        // 5. Fallbacks if spell is null
        ItemStack curiosFallback = getCuriosSpellbook(player, null);
        if (!curiosFallback.isEmpty())
            return curiosFallback;
        if (SpellAugmentRecipe.isSpellItem(mainHand))
            return mainHand;
        if (SpellAugmentRecipe.isSpellItem(offHand))
            return offHand;

        return ItemStack.EMPTY;
    }

    private static ItemStack getCuriosSpellbook(Player player, AbstractSpell spell) {
        if (player == null)
            return ItemStack.EMPTY;
        try {
            var optionalInv = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
            if (optionalInv.isPresent()) {
                var curiosInv = optionalInv.resolve().orElse(null);
                if (curiosInv != null) {
                    var slotResult = curiosInv.findFirstCurio(stack -> isSpellItemWithSpell(stack, spell));
                    if (slotResult.isPresent()) {
                        return slotResult.get().stack();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static boolean isSpellItemWithSpell(ItemStack stack, AbstractSpell spell) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack))
            return false;
        try {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container == null || container.isEmpty())
                return false;
            if (spell == null)
                return true;
            return getSlotIndexForSpell(container, spell) >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isSpellBook(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof io.redspace.ironsspellbooks.item.SpellBook;
    }
}
