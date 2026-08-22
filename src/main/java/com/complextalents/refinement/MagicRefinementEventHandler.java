package com.complextalents.refinement;

import com.complextalents.TalentsMod;
import com.complextalents.registry.ModAttributes;
import io.redspace.ironsspellbooks.api.events.InscribeSpellEvent;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class MagicRefinementEventHandler {

    @FunctionalInterface
    public interface ModifierAdder {
        void add(Attribute attribute, AttributeModifier modifier);
    }

    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != net.minecraft.world.entity.EquipmentSlot.MAINHAND &&
            event.getSlotType() != net.minecraft.world.entity.EquipmentSlot.OFFHAND) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || MagicRefinementManager.isScroll(stack)) return;

        int tier = MagicRefinementManager.getMagicItemTier(stack);
        if (tier <= 0) return;

        applyCatalystAttributes(event::addModifier, stack);
    }

    @SubscribeEvent
    public static void onCurioAttributeModifier(top.theillusivec4.curios.api.event.CurioAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || MagicRefinementManager.isScroll(stack)) return;

        int tier = MagicRefinementManager.getMagicItemTier(stack);
        if (tier <= 0) return;

        LivingEntity entity = event.getSlotContext().entity();
        if (entity instanceof Player player) {
            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty() && MagicRefinementManager.isScroll(mainHand)) {
                return; // Scroll exclusion: holding a scroll in main hand disables curio catalyst stats
            }
        }

        applyCatalystAttributes(event::addModifier, stack);
    }

    private static void applyCatalystAttributes(ModifierAdder adder, ItemStack stack) {
        int totalXp = MagicRefinementManager.getRefineXp(stack);
        int cumRank = MagicRefinementManager.getRankFromXp(totalXp, 20);
        if (cumRank <= 0) return;

        // Apply Mainstat: Spell Power (MULTIPLY_BASE)
        double spellPowerBonus = MagicRefinementManager.getCatalystSpellPowerBonus(cumRank);
        if (spellPowerBonus > 0) {
            Attribute spellPowerAttr = ForgeRegistries.ATTRIBUTES.getValue(
                ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power")
            );
            if (spellPowerAttr != null) {
                adder.add(spellPowerAttr, new AttributeModifier(
                    UUID.fromString("6a4cf7b0-7b56-4c40-9a3d-6b5832a839a1"),
                    "Magic Refinement Spell Power",
                    spellPowerBonus,
                    AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        }

        // Apply Substats
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("RefineSubstats")) {
            CompoundTag substats = tag.getCompound("RefineSubstats");
            for (MagicRefinementManager.MagicSubstatType type : MagicRefinementManager.MagicSubstatType.values()) {
                if (substats.contains(type.getKey())) {
                    double value = substats.getDouble(type.getKey());
                    if (value <= 0) continue;

                    Attribute attr = null;
                    AttributeModifier.Operation op = AttributeModifier.Operation.ADDITION;
                    switch (type) {
                        case MANA_REGEN -> {
                            attr = ForgeRegistries.ATTRIBUTES.getValue(
                                ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "mana_regen")
                            );
                            op = AttributeModifier.Operation.MULTIPLY_BASE;
                        }
                        case MAX_MANA -> {
                            attr = ForgeRegistries.ATTRIBUTES.getValue(
                                ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "max_mana")
                            );
                        }
                        case COOLDOWN_REDUCTION -> {
                            attr = ForgeRegistries.ATTRIBUTES.getValue(
                                ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "cooldown_reduction")
                            );
                        }
                        case CAST_TIME_REDUCTION -> {
                            attr = ForgeRegistries.ATTRIBUTES.getValue(
                                ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "cast_time_reduction")
                            );
                        }
                        case MAGIC_EFFECTIVENESS -> {
                            attr = ModAttributes.MAGIC_EFFECTIVENESS.get();
                        }
                        case SPELL_CRIT_CHANCE -> {
                            attr = ModAttributes.SPELL_CRIT_CHANCE.get();
                        }
                        case SPELL_CRIT_DAMAGE -> {
                            attr = ModAttributes.SPELL_CRIT_DAMAGE.get();
                        }
                        case HEAL_AND_SHIELD_POWER -> {
                            attr = ModAttributes.HEAL_AND_SHIELD_POWER.get();
                        }
                        case SUMMONING_POWER -> {
                            attr = ModAttributes.SUMMONING_POWER.get();
                        }
                    }

                    if (attr != null) {
                        UUID substatUuid = com.complextalents.util.UUIDHelper.generateAttributeModifierUUID(
                            "refinement", "magic_refinement_substat_" + type.getKey().toLowerCase()
                        );
                        adder.add(attr, new AttributeModifier(
                            substatUuid,
                            "Magic Refinement Substat " + type.getDisplayName(),
                            value,
                            op
                        ));
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
        if (spell == null || com.complextalents.classification.SpellClassificationManager.getOrAutoClassify(spell) != com.complextalents.classification.SpellClassificationManager.SpellType.DAMAGE) {
            return;
        }

        ItemStack castingStack = getCastingItemStack(player, spell);
        if (castingStack.isEmpty()) return;

        double dmgBonusMultiplier = MagicRefinementManager.getSpellRefinementMainstatBonus(castingStack, spell);

        if (dmgBonusMultiplier > 0.0) {
            float oldAmount = event.getAmount();
            event.setAmount(oldAmount * (1.0f + (float) dmgBonusMultiplier));
        }
    }


    @SubscribeEvent
    public static void onSpellOnCast(SpellOnCastEvent event) {
        if (event.getEntity() == null)
            return;
        Player player = event.getEntity();

        AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) return;

        ItemStack castingStack = getCastingItemStack(player, spell);
        if (castingStack.isEmpty())
            return;

        double manaReduction = 0.0;

        if (com.complextalents.classification.SpellClassificationManager.getOrAutoClassify(spell) == com.complextalents.classification.SpellClassificationManager.SpellType.DAMAGE) {
            if (MagicRefinementManager.isScroll(castingStack)) {
                int currentXp = MagicRefinementManager.getRefineXp(castingStack);
                int cumRank = MagicRefinementManager.getRankFromXp(currentXp, 20);
                manaReduction = MagicRefinementManager.getScrollManaCostReduction(cumRank);
            } else {
                CompoundTag tag = castingStack.getTag();
                if (tag != null && tag.contains("RefinedSpells")) {
                    CompoundTag refinedSpells = tag.getCompound("RefinedSpells");
                    String spellId = spell.getSpellId();
                    if (refinedSpells.contains(spellId)) {
                        CompoundTag spellRefineData = refinedSpells.getCompound(spellId);
                        int currentXp = spellRefineData.getInt("RefineXP");
                        int cumRank = MagicRefinementManager.getRankFromXp(currentXp, 20);
                        manaReduction = MagicRefinementManager.getScrollManaCostReduction(cumRank);
                    }
                }
            }
        }

        if (manaReduction > 0.0 && event.getCastSource() != null && event.getCastSource().consumesMana()) {
            int originalCost = event.getManaCost();
            int reducedCost = (int) Math.ceil(originalCost * (1.0 - manaReduction));
            event.setManaCost(reducedCost);
        }
    }

    @SubscribeEvent
    public static void onPlayerContainerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
            return;
        Player player = event.player;
        if (player.containerMenu instanceof InscriptionTableMenu menu) {
            ItemStack bookStack = menu.getSpellBookSlot().getItem();
            ItemStack scrollStack = menu.getScrollSlot().getItem();
            ItemStack resultStack = menu.getResultSlot().getItem();

            if (!bookStack.isEmpty() && !scrollStack.isEmpty() && !resultStack.isEmpty()) {
                if (MagicRefinementManager.isScroll(scrollStack)) {
                    int currentXp = MagicRefinementManager.getRefineXp(scrollStack);
                    if (currentXp > 0) {
                        ISpellContainer scrollContainer = ISpellContainer.get(scrollStack);
                        if (scrollContainer != null && !scrollContainer.isEmpty()) {
                            SpellData scrollSpell = scrollContainer.getSpellAtIndex(0);
                            if (scrollSpell != null && scrollSpell.getSpell() != null) {
                                String spellId = scrollSpell.getSpell().getSpellId();
                                int startingTier = MagicRefinementManager.getMagicItemTier(scrollStack);
                                long seed = scrollStack.getOrCreateTag().getLong("RefineSeed");

                                CompoundTag resultTag = resultStack.getOrCreateTag();

                                // Ensure existing refined spells are copied from book to result preview
                                CompoundTag bookTag = bookStack.getTag();
                                if (bookTag != null && bookTag.contains("RefinedSpells") && !resultTag.contains("RefinedSpells")) {
                                    resultTag.put("RefinedSpells", bookTag.getCompound("RefinedSpells").copy());
                                }

                                CompoundTag refinedSpells;
                                if (resultTag.contains("RefinedSpells")) {
                                    refinedSpells = resultTag.getCompound("RefinedSpells");
                                } else {
                                    refinedSpells = new CompoundTag();
                                }

                                CompoundTag spellRefineData = new CompoundTag();
                                spellRefineData.putInt("RefineXP", currentXp);
                                spellRefineData.putInt("StartingTier", startingTier);
                                spellRefineData.putLong("RefineSeed", seed);
                                CompoundTag scrollTag = scrollStack.getTag();
                                if (scrollTag != null) {
                                    if (scrollTag.contains("RefineSubstats")) {
                                        spellRefineData.put("RefineSubstats", scrollTag.get("RefineSubstats").copy());
                                    }
                                    if (scrollTag.contains("RefineRank")) {
                                        spellRefineData.putInt("RefineRank", scrollTag.getInt("RefineRank"));
                                    }
                                    if (scrollTag.contains("RefineHistory")) {
                                        spellRefineData.put("RefineHistory", scrollTag.get("RefineHistory").copy());
                                    }
                                }

                                refinedSpells.put(spellId, spellRefineData);
                                resultTag.put("RefinedSpells", refinedSpells);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onSpellCooldown(io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent.Pre event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;

        AbstractSpell spell = event.getSpell();
        ItemStack castingStack = getCastingItemStack(player, spell);
        if (!castingStack.isEmpty()) {
            double cdReduction = MagicRefinementManager.getSpellSubstatValue(
                castingStack, spell, MagicRefinementManager.MagicSubstatType.COOLDOWN_REDUCTION
            );
            if (cdReduction > 0.0) {
                int originalCd = event.getEffectiveCooldown();
                int reducedCd = (int) Math.round(originalCd * (1.0 - cdReduction));
                event.setEffectiveCooldown(Math.max(0, reducedCd));
            }
        }
    }

    public static ItemStack getCastingItemStack(Player player, AbstractSpell spell) {
        if (player == null)
            return ItemStack.EMPTY;

        // 1. Check Main Hand
        ItemStack mainHand = player.getMainHandItem();
        if (isSpellItemWithSpell(mainHand, spell))
            return mainHand;

        // 2. Check Off Hand
        ItemStack offHand = player.getOffhandItem();
        if (isSpellItemWithSpell(offHand, spell))
            return offHand;

        // 3. Check Curios Slot
        ItemStack curiosBook = getCuriosSpellbook(player, spell);
        if (!curiosBook.isEmpty())
            return curiosBook;

        // 4. Check Inventory Items
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (isSpellItemWithSpell(invStack, spell))
                return invStack;
        }

        // Fallbacks
        if (isSpellItemWithSpell(mainHand, null))
            return mainHand;
        if (isSpellItemWithSpell(offHand, null))
            return offHand;
        ItemStack curiosFallback = getCuriosSpellbook(player, null);
        if (!curiosFallback.isEmpty())
            return curiosFallback;

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

    @Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {

        private static double getFinalCalculatedValue(AbstractSpell spell, int spellLevel, Player player, ItemStack stack, double dmgBonus) {
            if (spell == null) return 0.0;

            // Set context temporarily
            com.complextalents.refinement.RefinementContext.setCurrentContextStack(stack);
            com.complextalents.refinement.RefinementContext.setCurrentContextSpell(spell);
            try {
                float basePower = spell.getSpellPower(spellLevel, player);
                if (MagicRefinementManager.isHealingSpell(spell)) {
                    return basePower;
                } else if (MagicRefinementManager.isSummoningSpell(spell)) {
                    return basePower;
                } else {
                    double priestMultiplier = 1.0;
                    if (player != null) {
                        boolean isPriest = false;
                        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            isPriest = com.complextalents.impl.highpriest.origin.HighPriestOrigin.ID.equals(com.complextalents.origin.OriginManager.getOriginId(serverPlayer));
                        } else if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                            isPriest = com.complextalents.impl.highpriest.origin.HighPriestOrigin.ID.equals(com.complextalents.origin.client.ClientOriginData.getOriginId());
                        }
                        if (isPriest) {
                            String schoolPath = spell.getSchoolType().getId().getPath();
                            if ("holy".equals(schoolPath)) {
                                priestMultiplier = 2.0;
                            } else {
                                priestMultiplier = 0.5;
                            }
                        }
                    }
                    return basePower * (1.0 + dmgBonus) * priestMultiplier;
                }
            } finally {
                com.complextalents.refinement.RefinementContext.clearCurrentContextStack();
                com.complextalents.refinement.RefinementContext.clearCurrentContextSpell();
            }
        }

        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) return;
            if (isClassificationMenuOpen()) return;

            // Inscribed refined spells list on spellbooks
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("RefinedSpells")) {
                CompoundTag refinedSpells = tag.getCompound("RefinedSpells");
                for (int k = 0; k < event.getToolTip().size(); k++) {
                    String lineText = event.getToolTip().get(k).getString();
                    for (String spellId : refinedSpells.getAllKeys()) {
                        AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);
                        if (spell != null) {
                            String spellName = spell.getDisplayName(event.getEntity()).getString();
                            if (lineText.contains(spellName) && !lineText.contains("[") && !lineText.contains("]")) {
                                CompoundTag spellRefineData = refinedSpells.getCompound(spellId);
                                int spellXp = spellRefineData.getInt("RefineXP");
                                int startingTier = spellRefineData.getInt("StartingTier");
                                int cumRank = MagicRefinementManager.getRankFromXp(spellXp, 20);
                                MagicRefinementManager.RefinementState state = MagicRefinementManager.calculateRefinementState(
                                    startingTier, cumRank - MagicRefinementManager.getBaseCumulativeLevelForStartingTier(startingTier), true
                                );
                                String tierColor = MagicRefinementManager.getTierColor(state.currentTier);
                                double dmgBonus = MagicRefinementManager.getScrollSpellDamageBonus(cumRank);

                                int spellLevel = 1;
                                ISpellContainer container = ISpellContainer.get(stack);
                                if (container != null) {
                                    for (SpellSlot data : container.getAllSpells()) {
                                        if (data != null && data.getSpell() != null && data.getSpell().getSpellId().equals(spellId)) {
                                            spellLevel = data.getLevel();
                                            break;
                                        }
                                    }
                                }

                                Player player = event.getEntity();
                                if (player == null && net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                                    player = net.minecraft.client.Minecraft.getInstance().player;
                                }

                                String suffix = String.format(" %s[%s +%d]", tierColor, state.getTierName(), state.refineInTier);
                                event.getToolTip().set(k, event.getToolTip().get(k).copy().append(Component.literal(suffix)));
                            }
                        }
                    }
                }
            }

            int startingTier = MagicRefinementManager.getMagicItemTier(stack);
            if (startingTier <= 0) return;

            int currentXp = MagicRefinementManager.getRefineXp(stack);
            int cumRank = MagicRefinementManager.getRankFromXp(currentXp, 20);
            int baseRank = MagicRefinementManager.getBaseCumulativeLevelForStartingTier(startingTier);
            int refineRank = Math.max(0, cumRank - baseRank);

            boolean isScroll = MagicRefinementManager.isScroll(stack);
            AbstractSpell scrollSpell = isScroll ? MagicRefinementManager.getScrollSpell(stack) : null;
            MagicRefinementManager.RefinementState state = MagicRefinementManager.calculateRefinementState(startingTier, refineRank, isScroll);

            boolean isSmithingOutputPreview = false;
            ItemStack inputStack = ItemStack.EMPTY;

            if (event.getEntity() != null) {
                if (event.getEntity().containerMenu instanceof net.minecraft.world.inventory.SmithingMenu menu) {
                    net.minecraft.world.inventory.Slot resultSlot = menu.getSlot(3);
                    if (resultSlot != null && resultSlot.hasItem()) {
                        ItemStack res = resultSlot.getItem();
                        if (res == stack || ItemStack.matches(res, stack)
                                || (res.hasTag() && stack.hasTag() && res.getTag().equals(stack.getTag()))) {
                            isSmithingOutputPreview = true;
                            if (menu.getSlot(1).hasItem()) {
                                inputStack = menu.getSlot(1).getItem();
                            }
                        }
                    }
                } else if (event.getEntity().containerMenu instanceof com.complextalents.menu.RefiningAnvilMenu anvilMenu) {
                    net.minecraft.world.inventory.Slot resultSlot = anvilMenu.getSlot(10);
                    if (resultSlot != null && resultSlot.hasItem()) {
                        ItemStack res = resultSlot.getItem();
                        if (res == stack || ItemStack.matches(res, stack)
                                || (res.hasTag() && stack.hasTag() && res.getTag().equals(stack.getTag()))) {
                            isSmithingOutputPreview = true;
                            if (anvilMenu.getSlot(0).hasItem()) {
                                inputStack = anvilMenu.getSlot(0).getItem();
                            }
                        }
                    }
                }
            }

            String tierColor = MagicRefinementManager.getTierColor(state.currentTier);
            String tierCrest = MagicRefinementManager.getTierCrestIcon(state.currentTier);
            int maxRankInTier = MagicRefinementManager.getMaxRefinesForTier(state.currentTier);

            StringBuilder filledSlots = new StringBuilder();
            for (int i = 0; i < state.refineInTier; i++) {
                filledSlots.append(tierCrest);
            }
            StringBuilder emptySlots = new StringBuilder();
            for (int i = state.refineInTier; i < maxRankInTier; i++) {
                emptySlots.append(tierCrest);
            }

            // Customize display name: removed as requested

            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.literal(
                String.format("§b§l%s Magic Refinement: §f%s", tierCrest, isScroll ? "Spell Scroll" : "Catalyst")
            ));

            if (isScroll && scrollSpell != null) {
                event.getToolTip().add(Component.literal("  §e✦ Spell Description:"));
                event.getToolTip().add(Component.literal("    ").append(Component.translatable(scrollSpell.getComponentId() + ".guide").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            }

            int maxXpForTier = MagicRefinementManager.getXpForRank(20);
            String xpText = (currentXp >= maxXpForTier)
                    ? " §8(MAX XP)"
                    : String.format(" §8(%,d / %,d XP)", currentXp, maxXpForTier);

            event.getToolTip().add(Component.literal(
                String.format("  §7✦ Refinement: %s%s  %s%s%s%s", tierColor, state.getRefineDisplay(), tierColor, filledSlots.toString(), "§8", emptySlots.toString(), xpText)
            ));

            if (MagicRefinementRecipe.isRecyclableMagicItem(stack)) {
                int recyclableXp = MagicRefinementRecipe.getRecyclableXp(stack);
                event.getToolTip().add(Component.literal(
                    String.format("  §e✦ Recyclable XP: §f+%,d XP §8(60%%)", recyclableXp)
                ));
            }

            // Main stats
            if (isScroll) {
                double dmgBonus = MagicRefinementManager.getScrollSpellDamageBonus(state.cumulativeLevel);
                double manaReduction = MagicRefinementManager.getScrollManaCostReduction(state.cumulativeLevel);
                if (scrollSpell != null) {
                    com.complextalents.classification.SpellClassificationManager.SpellType type =
                            com.complextalents.classification.SpellClassificationManager.getOrAutoClassify(scrollSpell);
                    switch (type) {
                        case SUMMONING -> {
                            if (dmgBonus > 0) {
                                event.getToolTip().add(Component.literal(
                                    String.format("  §7⚔ Summoning Power: §a+%.1f%%", dmgBonus * 100.0)
                                ));
                            }
                        }
                        case HEAL_AND_SHIELD -> {
                            if (dmgBonus > 0) {
                                event.getToolTip().add(Component.literal(
                                    String.format("  §7⚔ Heal & Shield Power: §a+%.1f%%", dmgBonus * 100.0)
                                ));
                            }
                        }
                        case DAMAGE -> {
                            if (dmgBonus > 0) {
                                event.getToolTip().add(Component.literal(
                                    String.format("  §7⚔ Spell Damage: §a+%.1f%%", dmgBonus * 100.0)
                                ));
                            }
                            if (manaReduction > 0) {
                                event.getToolTip().add(Component.literal(
                                    String.format("  §7☄ Mana Cost: §a-%.1f%%", manaReduction * 100.0)
                                ));
                            }
                        }
                        case EFFECT -> {}
                    }
                }

                if (scrollSpell != null) {
                    ISpellContainer container = ISpellContainer.get(stack);
                    if (container != null && !container.isEmpty()) {
                        SpellData slot = container.getSpellAtIndex(0);
                        if (slot != null) {
                            int spellLevel = slot.getLevel();
                            Player player = event.getEntity();
                            if (player == null && net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                                player = net.minecraft.client.Minecraft.getInstance().player;
                            }

                            int finalManaCost;
                            if (player != null) {
                                com.complextalents.refinement.RefinementContext.setCurrentContextStack(stack);
                                com.complextalents.refinement.RefinementContext.setCurrentContextSpell(scrollSpell);
                                try {
                                    finalManaCost = scrollSpell.getManaCost(spellLevel);
                                } finally {
                                    com.complextalents.refinement.RefinementContext.clearCurrentContextStack();
                                    com.complextalents.refinement.RefinementContext.clearCurrentContextSpell();
                                }
                            } else {
                                finalManaCost = scrollSpell.getManaCost(spellLevel);
                            }

                            event.getToolTip().add(Component.empty());
                            event.getToolTip().add(Component.literal("  §e✦ Spell Stats:"));

                            if (!MagicRefinementManager.isSummoningSpell(scrollSpell) && !MagicRefinementManager.isEffectSpell(scrollSpell)) {
                                double finalVal = getFinalCalculatedValue(scrollSpell, spellLevel, player, stack, dmgBonus);
                                if (MagicRefinementManager.isHealingSpell(scrollSpell)) {
                                    event.getToolTip().add(Component.literal(
                                        String.format("  §7✨ Final Healing/Shield: §6%.1f", finalVal)
                                    ));
                                } else {
                                    event.getToolTip().add(Component.literal(
                                        String.format("  §7⚔ Final Damage: §6%.1f", finalVal)
                                    ));

                                    if (player != null) {
                                        double critMultiplier = 1.5;
                                        var critAttr = player.getAttribute(com.complextalents.origin.integration.SpellCritAttributes.SPELL_CRIT_DAMAGE.get());
                                        if (critAttr != null) {
                                            critMultiplier = critAttr.getValue();
                                        }
                                        critMultiplier += MagicRefinementManager.getSpellSubstatValue(stack, scrollSpell, MagicRefinementManager.MagicSubstatType.SPELL_CRIT_DAMAGE);
                                        double critDmgVal = finalVal * critMultiplier;
                                        event.getToolTip().add(Component.literal(
                                            String.format("  §7⚡ Critical Damage: §c%.1f §8(%.0f%%)", critDmgVal, critMultiplier * 100.0)
                                        ));
                                    }
                                }
                            }

                            event.getToolTip().add(Component.literal(
                                String.format("  §7☄ Final Mana Cost: §9%d", finalManaCost)
                            ));

                            if (player != null) {
                                com.complextalents.refinement.RefinementContext.setCurrentContextStack(stack);
                                com.complextalents.refinement.RefinementContext.setCurrentContextSpell(scrollSpell);
                                int finalCastTimeTicks;
                                try {
                                    finalCastTimeTicks = scrollSpell.getEffectiveCastTime(spellLevel, player);
                                } finally {
                                    com.complextalents.refinement.RefinementContext.clearCurrentContextStack();
                                    com.complextalents.refinement.RefinementContext.clearCurrentContextSpell();
                                }
                                double finalCastTimeSec = finalCastTimeTicks / 20.0;
                                String castTimeStr = finalCastTimeTicks <= 0 ? "Instant" : String.format("%.2fs", finalCastTimeSec);

                                double cdReduction = MagicRefinementManager.getSpellSubstatValue(stack, scrollSpell, MagicRefinementManager.MagicSubstatType.COOLDOWN_REDUCTION);
                                int baseCdTicks = io.redspace.ironsspellbooks.capabilities.magic.MagicManager.getEffectiveSpellCooldown(scrollSpell, player, io.redspace.ironsspellbooks.api.spells.CastSource.SCROLL);
                                int finalCdTicks = (int) Math.round(baseCdTicks * (1.0 - cdReduction));
                                double finalCdSec = finalCdTicks / 20.0;
                                String cdStr = String.format("%.2fs", finalCdSec);

                                event.getToolTip().add(Component.literal(
                                    String.format("  §7⏱ Cast Time: §e%s", castTimeStr)
                                ));
                                event.getToolTip().add(Component.literal(
                                    String.format("  §7⏳ Cooldown: §e%s", cdStr)
                                ));
                            }
                        }
                    }
                }
            } else {
                double spellPowerBonus = MagicRefinementManager.getCatalystSpellPowerBonus(state.cumulativeLevel);
                if (spellPowerBonus > 0) {
                    event.getToolTip().add(Component.literal(
                        String.format("  §7✨ Spell Power: §a+%.1f%%", spellPowerBonus * 100.0)
                    ));
                }
            }

            // Substats
            event.getToolTip().add(Component.literal("  §d✦ Substats:"));
            if (isSmithingOutputPreview) {
                CompoundTag inputSubstats = inputStack.isEmpty() ? null : inputStack.getTagElement("RefineSubstats");
                boolean hasInputSubstats = false;
                if (inputSubstats != null && !inputSubstats.isEmpty()) {
                    for (MagicRefinementManager.MagicSubstatType type : MagicRefinementManager.MagicSubstatType.values()) {
                        if (inputSubstats.contains(type.getKey())) {
                            double val = inputSubstats.getDouble(type.getKey());
                            if (val > 0) {
                                event.getToolTip().add(Component.literal(
                                    String.format("    §7- %s: §d%s", type.getDisplayName(), type.formatValue(val))
                                ));
                                hasInputSubstats = true;
                            }
                        }
                    }
                }
                if (!hasInputSubstats) {
                    event.getToolTip().add(Component.literal("    §8No substats unlocked yet"));
                }

                // Display hidden gain
                int newCumLevel = state.cumulativeLevel;
                boolean isUnlock = (newCumLevel == 1 || newCumLevel == 5 || newCumLevel == 9 || newCumLevel == 13 || newCumLevel == 17);
                if (isUnlock) {
                    event.getToolTip().add(Component.literal("    §7- §k???§r§7: §d+???"));
                } else {
                    event.getToolTip().add(Component.literal("    §7- §e[Random Upgrade]§7: §d+???"));
                }
            } else {
                CompoundTag substatsTag = stack.getTagElement("RefineSubstats");
                boolean hasSubstats = false;
                if (substatsTag != null && !substatsTag.isEmpty()) {
                    for (MagicRefinementManager.MagicSubstatType type : MagicRefinementManager.MagicSubstatType.values()) {
                        if (substatsTag.contains(type.getKey())) {
                            double val = substatsTag.getDouble(type.getKey());
                            if (val > 0) {
                                event.getToolTip().add(Component.literal(
                                    String.format("    §7- %s: §d%s", type.getDisplayName(), type.formatValue(val))
                                ));
                                hasSubstats = true;
                            }
                        }
                    }
                }
                if (!hasSubstats) {
                    event.getToolTip().remove(event.getToolTip().size() - 1); // Remove Substats line if no substats exist
                }
            }

            // History on CTRL key
            if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                ListTag historyTag;
                if (isSmithingOutputPreview) {
                    if (!inputStack.isEmpty() && inputStack.hasTag()) {
                        historyTag = inputStack.getTag().getList("RefineHistory", 8).copy();
                    } else {
                        ListTag fullHistory = tag.getList("RefineHistory", 8);
                        historyTag = new ListTag();
                        for (int i = 0; i < fullHistory.size() - 1; i++) {
                            historyTag.add(fullHistory.get(i).copy());
                        }
                    }
                    int newCumLevel = state.cumulativeLevel;
                    boolean isUnlock = (newCumLevel == 1 || newCumLevel == 5 || newCumLevel == 9 || newCumLevel == 13 || newCumLevel == 17);
                    String color = MagicRefinementManager.getTierColor(state.currentTier);
                    String crest = MagicRefinementManager.getTierCrestIcon(state.currentTier);
                    String tierName = state.getTierName();
                    String obscuredLine;
                    if (isUnlock) {
                        obscuredLine = String.format("%s%s Lv.%d: §k???§r %s+??? §8(%s)", color, crest, newCumLevel, color, tierName);
                    } else {
                        obscuredLine = String.format("%s%s Lv.%d: §e[Random Upgrade]§r %s+??? §8(%s)", color, crest, newCumLevel, color, tierName);
                    }
                    historyTag.add(net.minecraft.nbt.StringTag.valueOf(obscuredLine));
                } else {
                    historyTag = tag.getList("RefineHistory", 8);
                }

                if (historyTag != null && !historyTag.isEmpty()) {
                    event.getToolTip().add(Component.empty());
                    event.getToolTip().add(Component.literal("  §e✦ Refinement History:"));
                    for (int i = 0; i < historyTag.size(); i++) {
                        event.getToolTip().add(Component.literal("    " + historyTag.getString(i)));
                    }
                }
            } else {
                event.getToolTip().add(Component.literal("  §8[Hold CTRL to show refinement history]"));
            }
        }

        private static boolean isClassificationMenuOpen() {
            try {
                if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                    var screen = net.minecraft.client.Minecraft.getInstance().screen;
                    if (screen != null && screen.getTitle() != null) {
                        String title = screen.getTitle().getString();
                        return title.contains("Spell Classification") || title.contains("Weapon Classification");
                    }
                }
            } catch (Throwable ignored) {}
            return false;
        }
    }
}
