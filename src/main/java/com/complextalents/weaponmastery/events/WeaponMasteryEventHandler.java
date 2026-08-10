package com.complextalents.weaponmastery.events;

import com.complextalents.TalentsMod;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class WeaponMasteryEventHandler {

    /**
     * Applies the weapon refinement algorithm to compatible weapons dropped by
     * mobs/entities upon death.
     */
    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() != null && !event.getEntity().level().isClientSide) {
            net.minecraft.util.RandomSource random = event.getEntity().getRandom();
            for (net.minecraft.world.entity.item.ItemEntity drop : event.getDrops()) {
                if (drop != null && !drop.getItem().isEmpty()) {
                    ItemStack stack = drop.getItem();
                    WeaponMasteryManager.applyRandomRefinementForLoot(stack, random);
                }
            }
        }
    }

    /**
     * Cleanly intercepts PoiseDamageEvent to apply:
     * 1. REAPER: -60% Poise Damage penalty (40% remaining), suppressed for Assassin
     * origin stealth backstabs.
     * 2. COLOSSUS: +100% to +750% Poise Damage burst during active Overwhelming
     * Force sprint attack.
     */
    @SubscribeEvent
    public static void onPoiseDamage(com.nhatbh.basedefensev2.api.event.PoiseDamageEvent event) {
        if (event.getAttacker() instanceof ServerPlayer player) {
            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty()) {
                IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(mainHand);
                if (path == IWeaponMasteryData.WeaponPath.REAPER) {
                    boolean suppressPenalty = false;
                    if (com.complextalents.impl.assassin.origin.AssassinOrigin.isAssassin(player)) {
                        if (player.hasEffect(com.complextalents.impl.assassin.effect.AssassinEffects.AMBUSH.get())) {
                            suppressPenalty = true;
                        }
                    }

                    if (!suppressPenalty) {
                        float original = event.getAmount();
                        event.setAmount(original * 0.40f);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide)
            return;

        Player player = event.player;
        ItemStack mainHand = player.getMainHandItem();

        // Weapon Rank Level Requirement Check (every 20 ticks)
        if (player.tickCount % 20 == 0 && !mainHand.isEmpty()) {
            IWeaponMasteryData.WeaponPath itemPath = WeaponMasteryManager.getInstance().getWeaponPath(mainHand);
            if (itemPath != null) {
                int startingTier = WeaponMasteryManager.getInstance().getWeaponTier(mainHand);
                if (startingTier > 0) {
                    int totalRefines = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(mainHand);
                    WeaponMasteryManager.RefinementState state = WeaponMasteryManager
                            .calculateRefinementState(startingTier, totalRefines);

                    int requiredRankLevel = WeaponMasteryManager.getRequiredMasteryLevel(state.currentTier,
                            state.refineInTier);

                    player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
                        if (data.getMasteryLevel(itemPath) < requiredRankLevel) {
                            // Player lacks mastery level to wield this weapon effectively
                            player.addEffect(
                                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false, true));
                            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 4, false, false, true));
                            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4, false, false, true));
                        }
                    });
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            if (player.level().isClientSide)
                return;

            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.isEmpty())
                return;

            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(mainHandItem.getItem());
            if (itemId == null)
                return;

            IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(itemId);
            if (path != null) {
                // Accumulate damage, capped at current health of target
                double actualDamage = Math.min(event.getAmount(), event.getEntity().getHealth());
                if (actualDamage <= 0)
                    return;

                player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
                    data.addAccumulatedDamage(path, actualDamage);
                });
            }
        }
    }

    @Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            ItemStack itemStack = event.getItemStack();
            if (itemStack.isEmpty())
                return;

            IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(itemStack);
            if (path != null) {
                int startingTier = WeaponMasteryManager.getInstance().getWeaponTier(itemStack);
                int safeTier = startingTier > 0 ? startingTier : 1;
                int totalRefines = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(itemStack);
                WeaponMasteryManager.RefinementState state = WeaponMasteryManager.calculateRefinementState(safeTier,
                        totalRefines);

                int requiredRankLevel = WeaponMasteryManager.getRequiredMasteryLevel(state.currentTier,
                        state.refineInTier);
                String rankName = WeaponMasteryManager.getRankNameForLevel(requiredRankLevel);
                String rankColor = getRankColor(requiredRankLevel);
                String symbol = getRankSymbol(requiredRankLevel);

                Player player = event.getEntity();
                int playerLevel = 0;
                if (player != null) {
                    var data = player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).orElse(null);
                    if (data != null) {
                        playerLevel = data.getMasteryLevel(path);
                    }
                }

                int maxRankInTier = WeaponMasteryManager.getMaxRefinesForTier(state.currentTier);
                int currentRankInTier = state.refineInTier;

                String tierColor = getTierColor(state.currentTier);
                String tierCrest = getTierCrestIcon(state.currentTier);

                StringBuilder filledSlots = new StringBuilder();
                for (int i = 0; i < currentRankInTier; i++) {
                    filledSlots.append(tierCrest);
                }
                StringBuilder emptySlots = new StringBuilder();
                for (int i = currentRankInTier; i < maxRankInTier; i++) {
                    emptySlots.append(tierCrest);
                }

                // --- Item Display Name Customization (Mythic Title + Tier Styling) ---
                if (!event.getToolTip().isEmpty()) {
                    WeaponMasteryManager.WeaponTitle title = WeaponMasteryManager.getWeaponTitle(path,
                            state.cumulativeLevel);
                    String stylePrefix = WeaponMasteryManager.getTierStylePrefix(state.currentTier);

                    String originalName = itemStack.getItem().getDescription().getString();
                    if (itemStack.hasCustomHoverName()) {
                        originalName = itemStack.getHoverName().getString();
                    }

                    String fullTitledName = title.formatName(originalName);
                    event.getToolTip().set(0,
                            net.minecraft.network.chat.Component.literal(stylePrefix + fullTitledName));
                }

                // --- Unified Cohesive Display ---
                event.getToolTip().add(net.minecraft.network.chat.Component.empty());
                event.getToolTip().add(net.minecraft.network.chat.Component
                        .literal("\u00A7b\u00A7l" + symbol + " Weapon Mastery: \u00A7f" + path.getDisplayName()));

                int currentXp = WeaponMasteryManager.getRefineXp(itemStack);
                int maxXpForTier = WeaponMasteryManager.getMaxXpForStartingTier(safeTier);

                String xpText = (currentXp >= maxXpForTier)
                        ? " \u00A78(MAX XP)"
                        : String.format(" \u00A78(%,d / %,d XP)", currentXp, maxXpForTier);

                // Refinement & Tier display using proper Tier Name & Crest Slots
                net.minecraft.network.chat.Component refineLine = net.minecraft.network.chat.Component
                        .literal("  \u00A77✦ Refinement: " + tierColor + state.getRefineDisplay() + "  " + tierColor
                                + filledSlots.toString() + "\u00A78" + emptySlots.toString() + xpText);
                event.getToolTip().add(refineLine);

                if (com.complextalents.refinement.WeaponRefinementRecipe.isRecyclableWeapon(itemStack)) {
                    int recyclableXp = com.complextalents.refinement.WeaponRefinementRecipe.getRecyclableXp(itemStack);
                    event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                            String.format("  \u00A7e✦ Recyclable XP: \u00A7f+%,d XP \u00A78(60%%)", recyclableXp)));
                }

                boolean isCtrlDown = net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()
                        && net.minecraft.client.gui.screens.Screen.hasControlDown();

                boolean isSmithingOutputPreview = false;
                net.minecraft.world.item.ItemStack inputStack = net.minecraft.world.item.ItemStack.EMPTY;

                if (event.getEntity() != null
                        && event.getEntity().containerMenu instanceof net.minecraft.world.inventory.SmithingMenu menu) {
                    net.minecraft.world.inventory.Slot resultSlot = menu.getSlot(3);
                    if (resultSlot != null && resultSlot.hasItem()) {
                        net.minecraft.world.item.ItemStack res = resultSlot.getItem();
                        if (res == itemStack || net.minecraft.world.item.ItemStack.matches(res, itemStack)
                                || (res.hasTag() && itemStack.hasTag() && res.getTag().equals(itemStack.getTag()))) {
                            isSmithingOutputPreview = true;
                            if (menu.getSlot(1).hasItem()) {
                                inputStack = menu.getSlot(1).getItem();
                            }
                        }
                    }
                }

                int inputRank = 0;
                if (isSmithingOutputPreview && !inputStack.isEmpty()) {
                    inputRank = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(inputStack);
                }

                // Base AD Bonus percentage (Obscured during Smithing Table preview to show
                // +[Current accumulated]% + [Next base gain]%)
                double adBonus = WeaponMasteryManager.getADBonusMultiplier(itemStack);
                if (isSmithingOutputPreview) {
                    double currentAccumulated = WeaponMasteryManager.getADBonusMultiplier(inputStack);

                    int baseCumulativeLevel = WeaponMasteryManager.getBaseCumulativeLevelForStartingTier(startingTier);
                    int oldCumLevel = baseCumulativeLevel + inputRank;
                    int newCumLevel = state.cumulativeLevel;

                    double baseBonusCurr = WeaponMasteryManager.getADBonusMultiplier(newCumLevel);
                    double baseBonusPrev = WeaponMasteryManager.getADBonusMultiplier(oldCumLevel);
                    double nextBaseGain = baseBonusCurr - baseBonusPrev;

                    event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                            String.format("  \u00A77⚔ Bonus Base Damage: \u00A7a+%.1f%% + %.1f%%",
                                    currentAccumulated * 100.0, nextBaseGain * 100.0)));
                } else if (adBonus > 0) {
                    event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                            "  \u00A77⚔ Bonus Base Damage: \u00A7a+" + String.format("%.1f", adBonus * 100.0) + "%"));
                }

                // Requirement & Player Wield Status
                if (playerLevel >= requiredRankLevel) {
                    event.getToolTip().add(net.minecraft.network.chat.Component.literal("  \u00A7a✔ Wield Requirement: "
                            + rankColor + "L." + requiredRankLevel + " \u00A77(Your Level: L." + playerLevel + ")"));
                } else {
                    event.getToolTip().add(net.minecraft.network.chat.Component.literal("  \u00A7c✖ Required Mastery: "
                            + rankColor + "L." + requiredRankLevel + " \u00A77(Your Level: L." + playerLevel + ")"));
                }

                // --- Unified Level Progression Breakdown (Controlled by CTRL Key) ---
                if (state.cumulativeLevel > 0) {
                    if (isCtrlDown) {
                        event.getToolTip().add(net.minecraft.network.chat.Component.empty());
                        event.getToolTip().add(net.minecraft.network.chat.Component
                                .literal("  \u00A7d\u00A7l✦ Level Progression Breakdown:"));

                        net.minecraft.nbt.ListTag varianceList = (itemStack.hasTag()
                                && itemStack.getTag().contains("RefineVariances", net.minecraft.nbt.Tag.TAG_LIST))
                                        ? itemStack.getTag().getList("RefineVariances", net.minecraft.nbt.Tag.TAG_FLOAT)
                                        : new net.minecraft.nbt.ListTag();

                        int targetCumulativeLevel = state.cumulativeLevel;
                        int baseCumulativeLevel = WeaponMasteryManager
                                .getBaseCumulativeLevelForStartingTier(startingTier);

                        for (int level = 1; level <= targetCumulativeLevel; level++) {
                            double baseBonusCurr = WeaponMasteryManager.getADBonusMultiplier(level);
                            double baseBonusPrev = WeaponMasteryManager.getADBonusMultiplier(level - 1);
                            double increment = baseBonusCurr - baseBonusPrev;

                            if (level <= baseCumulativeLevel) {
                                String line = String.format("    \u00A77• Lv.%d: \u00A7a+%.1f%% \u00A78(Base Tier)",
                                        level, increment * 100.0);
                                event.getToolTip().add(net.minecraft.network.chat.Component.literal(line));
                            } else {
                                int step = level - baseCumulativeLevel;
                                if (isSmithingOutputPreview && step > inputRank) {
                                    event.getToolTip().add(net.minecraft.network.chat.Component
                                            .literal("    \u00A77• Lv." + level + " (+" + step + "): \u00A78??? [Uncommitted Roll]"));
                                    continue;
                                }

                                float v = (step - 1 < varianceList.size()) ? varianceList.getFloat(step - 1) : 0.0f;
                                v = Math.max(-0.20f, Math.min(0.20f, v));

                                double actualAtk = increment * (1.0 + v);

                                String varianceColor;
                                if (v >= 0.15f) {
                                    varianceColor = "\u00A76"; // Pinnacle Gold
                                } else if (v > 0.001f) {
                                    varianceColor = "\u00A7a"; // Positive Green
                                } else if (v < -0.001f) {
                                    varianceColor = "\u00A7c"; // Negative Red
                                } else {
                                    varianceColor = "\u00A77"; // Neutral Gray
                                }

                                String line = String.format("    \u00A77• Lv.%d (+" + step + "): \u00A7a+%.1f%% \u00A78(%s%+.1f%%\u00A78)",
                                        level, actualAtk * 100.0, varianceColor, v * 100.0f);
                                event.getToolTip().add(net.minecraft.network.chat.Component.literal(line));
                            }
                        }

                        double baseBonus = WeaponMasteryManager.getADBonusMultiplier(state.cumulativeLevel);
                        double diff = adBonus - baseBonus;
                        if (Math.abs(diff) >= 0.0005 && !isSmithingOutputPreview) {
                            double relativeDiff = baseBonus > 0 ? (diff / baseBonus) : 0.0;
                            String netColor = diff > 0 ? "\u00A7a" : "\u00A7c";
                            event.getToolTip()
                                    .add(net.minecraft.network.chat.Component.literal("  \u00A77✦ Net Refine Variance: "
                                            + netColor + String.format("%+.1f%%", relativeDiff * 100.0)));
                        }
                    } else {
                        event.getToolTip().add(net.minecraft.network.chat.Component
                                .literal("  \u00A78[Hold CTRL for Refinement Breakdown]"));
                    }
                }
            }
        }

        private static String getTierColor(int tier) {
            return switch (tier) {
                case 1 -> "\u00A7f"; // Novice: White
                case 2 -> "\u00A7a"; // Apprentice: Emerald Green
                case 3 -> "\u00A79"; // Adept: Indigo Blue
                case 4 -> "\u00A75"; // Expert: Purple
                case 5 -> "\u00A76"; // Master: Gold
                default -> "\u00A7f";
            };
        }

        private static String getTierCrestIcon(int tier) {
            return switch (tier) {
                case 1 -> "✦"; // Novice Crest
                case 2 -> "✦"; // Apprentice Crest
                case 3 -> "❖"; // Adept Crest
                case 4 -> "❂"; // Expert Crest
                case 5 -> "⚜"; // Master Crest
                default -> "✦";
            };
        }

        private static String getRankColor(int level) {
            if (level >= 14)
                return "\u00A76"; // Gold
            if (level >= 9)
                return "\u00A75"; // Purple
            if (level >= 5)
                return "\u00A79"; // Indigo
            if (level >= 2)
                return "\u00A7a"; // Emerald Green
            return "\u00A7f"; // White
        }

        private static String getRankSymbol(int level) {
            if (level >= 14)
                return "\u00A76\u00A7l⚜\u00A7r";
            if (level >= 9)
                return "\u00A75❂\u00A7r";
            if (level >= 5)
                return "\u00A79❖\u00A7r";
            if (level >= 2)
                return "\u00A7a✦\u00A7r";
            return "\u00A7f✧\u00A7r";
        }
    }
}
