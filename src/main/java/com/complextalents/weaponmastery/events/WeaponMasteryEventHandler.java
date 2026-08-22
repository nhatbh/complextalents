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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.entity.LivingEntity;
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
     * 1. REAPER: -40% Poise Damage penalty (60% remaining), suppressed for Assassin
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
                        event.setAmount(original * 0.60f);
                    }
                }
            }
        }
    }

    /**
     * Blocks all health damage dealt by REAPER weapons when the target has an active
     * poise shield. Reaper is a glass-cannon archetype that excels against unshielded
     * targets — it cannot brute-force through poise by dealing overwhelming damage.
     * The Assassin Ambush backstab is exempt: a true backstab ignores poise entirely.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        ItemStack mainHand = attacker.getMainHandItem();
        if (mainHand.isEmpty()) return;

        IWeaponMasteryData.WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(mainHand);
        if (path != IWeaponMasteryData.WeaponPath.REAPER) return;

        // Assassin Ambush backstab bypasses poise entirely
        if (com.complextalents.impl.assassin.origin.AssassinOrigin.isAssassin(attacker)) {
            if (attacker.hasEffect(com.complextalents.impl.assassin.effect.AssassinEffects.AMBUSH.get())) {
                return;
            }
        }

        // If the target has an active, non-exhausted poise shield, block all health damage
        if (com.nhatbh.basedefensev2.api.PoiseAPI.hasPoise(victim)
                && !com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(victim)) {
            // Assassins run the full backstab pipeline on poise-negated hits:
            // Expose Weakness, Disengage speed burst, and backstab FX still fire
            // even though 0 HP damage was dealt.
            if (com.complextalents.impl.assassin.origin.AssassinOrigin.isAssassin(attacker)
                    && com.complextalents.impl.assassin.util.AssassinUtils.isBackstab(attacker, victim)) {
                com.complextalents.impl.assassin.events.AssassinOriginHandler.handleOriginBackstab(attacker, victim);
                // Break stealth: poise absorbed the hit so no damage event fires,
                // but the assassin must still be revealed and consume their gauge.
                if (attacker.hasEffect(com.complextalents.impl.assassin.effect.AssassinEffects.SHADOW_WALK.get())) {
                    com.complextalents.impl.assassin.events.ShadowWalkEventHandler.breakStealthOnAttack(attacker, victim, true);
                }
            }
            event.setAmount(0.0f);
            event.setCanceled(true);
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
                boolean isSmithingOutputPreview = false;
                net.minecraft.world.item.ItemStack inputStack = net.minecraft.world.item.ItemStack.EMPTY;

                if (event.getEntity() != null) {
                    if (event.getEntity().containerMenu instanceof net.minecraft.world.inventory.SmithingMenu menu) {
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
                    } else if (event.getEntity().containerMenu instanceof com.complextalents.menu.RefiningAnvilMenu anvilMenu) {
                        net.minecraft.world.inventory.Slot resultSlot = anvilMenu.getSlot(10);
                        if (resultSlot != null && resultSlot.hasItem()) {
                            net.minecraft.world.item.ItemStack res = resultSlot.getItem();
                            if (res == itemStack || net.minecraft.world.item.ItemStack.matches(res, itemStack)
                                    || (res.hasTag() && itemStack.hasTag() && res.getTag().equals(itemStack.getTag()))) {
                                isSmithingOutputPreview = true;
                                if (anvilMenu.getSlot(0).hasItem()) {
                                    inputStack = anvilMenu.getSlot(0).getItem();
                                }
                            }
                        }
                    }
                }

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
                net.minecraft.network.chat.Component refineLine;
                if (isSmithingOutputPreview && !inputStack.isEmpty()) {
                    int inputStartingTier = WeaponMasteryManager.getInstance().getWeaponTier(inputStack);
                    int inputRefines = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(inputStack);
                    WeaponMasteryManager.RefinementState inputState = WeaponMasteryManager.calculateRefinementState(inputStartingTier, inputRefines);
                    int lvGain = state.cumulativeLevel - inputState.cumulativeLevel;

                    String inputTierColor = getTierColor(inputState.currentTier);
                    refineLine = net.minecraft.network.chat.Component.literal(
                            String.format("  \u00A77✦ Refinement: %s%s \u00A77-> %s%s \u00A7e(+%d Lv)",
                                    inputTierColor, inputState.getRefineDisplay(),
                                    tierColor, state.getRefineDisplay(),
                                    lvGain));
                } else {
                    refineLine = net.minecraft.network.chat.Component
                            .literal("  \u00A77✦ Refinement: " + tierColor + state.getRefineDisplay() + "  " + tierColor
                                    + filledSlots.toString() + "\u00A78" + emptySlots.toString() + xpText);
                }
                event.getToolTip().add(refineLine);

                if (com.complextalents.refinement.WeaponRefinementRecipe.isRecyclableWeapon(itemStack)) {
                    int recyclableXp = com.complextalents.refinement.WeaponRefinementRecipe.getRecyclableXp(itemStack);
                    event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                            String.format("  \u00A7e✦ Recyclable XP: \u00A7f+%,d XP \u00A78(60%%)", recyclableXp)));
                }

                boolean isCtrlDown = net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()
                        && net.minecraft.client.gui.screens.Screen.hasControlDown();

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

                // --- Substats List Display ---
                event.getToolTip().add(net.minecraft.network.chat.Component.literal("  \u00A7d✦ Substats:"));
                if (isSmithingOutputPreview) {
                    java.util.Map<WeaponMasteryManager.SubstatType, Double> inputSubstats = WeaponMasteryManager.getCachedSubstats(inputStack);
                    if (inputSubstats.isEmpty()) {
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal("    \u00A78No substats unlocked yet"));
                    } else {
                        for (java.util.Map.Entry<WeaponMasteryManager.SubstatType, Double> entry : inputSubstats.entrySet()) {
                            WeaponMasteryManager.SubstatType type = entry.getKey();
                            double val = entry.getValue();
                            String label = type.getDisplayName();
                            String formatted = type.formatValue(val);
                            event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                                    String.format("    \u00A77• %s: \u00A7a%s", label, formatted)));
                        }
                    }

                    int newCumLevel = state.cumulativeLevel;
                    boolean isUnlock = (newCumLevel == 1 || newCumLevel == 3 || newCumLevel == 5 || newCumLevel == 8 || newCumLevel == 12);
                    if (isUnlock) {
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal("    \u00a77• \u00a7k???\u00a7r\u00a77: \u00a7a+???"));
                    } else {
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal("    \u00a77• \u00a7e[Random Upgrade]\u00a77: \u00a7a+???"));
                    }
                } else {
                    java.util.Map<WeaponMasteryManager.SubstatType, Double> substats = WeaponMasteryManager.getCachedSubstats(itemStack);
                    if (substats.isEmpty()) {
                        event.getToolTip().add(net.minecraft.network.chat.Component.literal("    \u00A78No substats unlocked yet"));
                    } else {
                        for (java.util.Map.Entry<WeaponMasteryManager.SubstatType, Double> entry : substats.entrySet()) {
                            WeaponMasteryManager.SubstatType type = entry.getKey();
                            double val = entry.getValue();
                            String label = type.getDisplayName();
                            String formatted = type.formatValue(val);
                            event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                                    String.format("    \u00A77• %s: \u00A7a%s", label, formatted)));
                        }
                    }
                }

                // Requirement & Player Wield Status
                if (playerLevel >= requiredRankLevel) {
                    event.getToolTip().add(net.minecraft.network.chat.Component.literal("  \u00A7a✔ Wield Requirement: "
                             + rankColor + "L." + requiredRankLevel + " \u00A77(Your Level: L." + playerLevel + ")"));
                } else {
                    event.getToolTip().add(net.minecraft.network.chat.Component.literal("  \u00A7c✖ Required Mastery: "
                             + rankColor + "L." + requiredRankLevel + " \u00A77(Your Level: L." + playerLevel + ")"));
                }

                // --- Substat Enhancement History (Controlled by CTRL Key) ---
                if (state.cumulativeLevel > 0) {
                    if (isCtrlDown) {
                        event.getToolTip().add(net.minecraft.network.chat.Component.empty());
                        event.getToolTip().add(net.minecraft.network.chat.Component
                                 .literal("  \u00A7d\u00A7l✦ Substat Enhancement History:"));

                        java.util.List<String> history = WeaponMasteryManager.getCachedHistory(itemStack);
                        
                        int inputCumulativeLevel = 0;
                        if (isSmithingOutputPreview && !inputStack.isEmpty()) {
                            int inputStartingTier = WeaponMasteryManager.getInstance().getWeaponTier(inputStack);
                            int inputRefines = com.complextalents.refinement.WeaponRefinementRecipe.getRefineRank(inputStack);
                            WeaponMasteryManager.RefinementState inputState = WeaponMasteryManager.calculateRefinementState(inputStartingTier, inputRefines);
                            inputCumulativeLevel = inputState.cumulativeLevel;
                        }

                        if (history.isEmpty()) {
                            event.getToolTip().add(net.minecraft.network.chat.Component.literal("    \u00A77• No history recorded."));
                        } else {
                            for (int i = 0; i < history.size(); i++) {
                                String line = history.get(i);
                                if (isSmithingOutputPreview && i >= inputCumulativeLevel) {
                                    int previewLvl = i + 1;
                                    int previewTier = WeaponMasteryManager.getTierForCumulativeLevel(previewLvl);
                                    String previewCrest = WeaponMasteryManager.getTierColor(previewTier) + WeaponMasteryManager.getTierCrestIcon(previewTier) + "\u00A7r\u00A77";
                                    line = previewCrest + " +??? ???";
                                }
                                event.getToolTip().add(net.minecraft.network.chat.Component.literal("    " + line));
                            }
                        }
                    } else {
                        event.getToolTip().add(net.minecraft.network.chat.Component
                                 .literal("  \u00A78[Hold CTRL for Enhancement History]"));
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
