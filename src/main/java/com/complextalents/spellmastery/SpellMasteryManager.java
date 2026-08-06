package com.complextalents.spellmastery;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.spellmastery.capability.ISpellMasteryData;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import com.complextalents.stats.ClassCostMatrix;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Manages Spell Mastery verification and cost calculation logic.
 * Rule: For each tier of a spell, players learn only the lowest level of that tier.
 * In-Chat Learning: Knowledge-Only cost (no physical scroll item given).
 * UI Purchase: Full purchase cost (yields physical scroll item).
 */
public class SpellMasteryManager {

    /**
     * Registers a learned spell into Iron's Spellbooks SyncedSpellData if it requires learning (e.g. Eldritch spells).
     */
    public static void onSpellLearned(ServerPlayer player, AbstractSpell spell) {
        if (player == null || spell == null) return;
        if (spell.getSchoolType() != null && "eldritch".equalsIgnoreCase(spell.getSchoolType().getId().getPath())) {
            io.redspace.ironsspellbooks.api.magic.MagicData magicData = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            if (magicData != null && magicData.getSyncedData() != null) {
                magicData.getSyncedData().learnSpell(spell);
            }
        }
    }

    /**
     * Returns the lowest level of a spell for a given rarity tier.
     */
    public static int getMinLevelForRarity(AbstractSpell spell, SpellRarity rarity) {
        if (spell == null) return 1;
        for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
            if (spell.getRarity(lvl) == rarity) {
                return lvl;
            }
        }
        return spell.getMinLevel();
    }

    /**
     * Returns the list of entry levels (lowest level per rarity tier) for a spell.
     */
    public static List<Integer> getTierEntryLevels(AbstractSpell spell) {
        List<Integer> entryLevels = new ArrayList<>();
        if (spell == null) return entryLevels;
        Set<SpellRarity> seenRarities = new HashSet<>();
        for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
            SpellRarity r = spell.getRarity(lvl);
            if (seenRarities.add(r)) {
                entryLevels.add(lvl);
            }
        }
        return entryLevels;
    }

    /**
     * Verifies if a player can cast a spell based on learned knowledge.
     * Learning the entry level of a tier authorizes casting all levels of that tier and lower.
     */
    public static Optional<CastResult> verifyCast(AbstractSpell spell, int spellLevel, CastSource castSource, Player player) {
        if (player != null && player.hasEffect(com.complextalents.effect.ModEffects.SILENCED.get())) {
            return Optional.of(new CastResult(CastResult.Type.FAILURE,
                    Component.literal("Silenced (Cannot cast)").withStyle(ChatFormatting.RED)));
        }

        if (player != null && player.hasEffect(com.complextalents.effect.ModEffects.POSSESSED.get())) {
            if (spell == null || spell.getSchoolType() == null || !"eldritch".equalsIgnoreCase(spell.getSchoolType().getId().getPath())) {
                return Optional.of(new CastResult(CastResult.Type.FAILURE,
                        Component.literal("Possessed (Eldritch Only)").withStyle(ChatFormatting.DARK_PURPLE)));
            }
        }

        if (spell != null && spell.getSchoolType() != null && "eldritch".equalsIgnoreCase(spell.getSchoolType().getId().getPath())) {
            ResourceLocation activeOrigin = player.getCapability(OriginDataProvider.ORIGIN_DATA)
                    .map(data -> data.getActiveOrigin()).orElse(null);
            boolean isDarkMage = ResourceLocation.fromNamespaceAndPath("complextalents", "dark_mage").equals(activeOrigin);
            boolean isSpellblade = ResourceLocation.fromNamespaceAndPath("complextalents", "spellblade").equals(activeOrigin);
            if (!isDarkMage && !isSpellblade) {
                return Optional.of(new CastResult(CastResult.Type.FAILURE,
                        Component.literal("Only Dark Mages and Spellblades can learn and cast Eldritch spells!").withStyle(ChatFormatting.RED)));
            }
        }

        // Scroll casting bypasses mastery and knowledge checks.
        if (castSource == CastSource.SCROLL) {
            return Optional.empty();
        }

        // Learned Verification
        return player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).map(mastery -> {
            ResourceLocation spellId = spell.getSpellResource();
            SpellRarity castRarity = spell.getRarity(spellLevel);

            // Check if player has learned the entry level of this rarity tier or a higher tier
            boolean authorized = false;
            for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
                if (mastery.isSpellLearned(spellId, lvl)) {
                    if (spell.getRarity(lvl).getValue() >= castRarity.getValue()) {
                        authorized = true;
                        break;
                    }
                }
            }

            if (!authorized) {
                int entryLevel = getMinLevelForRarity(spell, castRarity);
                if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    sendInteractiveLearnPrompt(serverPlayer, spell, entryLevel, mastery);
                }

                return new CastResult(CastResult.Type.FAILURE, 
                    Component.literal("Unlearned tier: " + spell.getDisplayName(player).getString() + " (" + castRarity.getDisplayName().getString() + " Tier - Level " + entryLevel + ")")
                    .withStyle(ChatFormatting.RED));
            }

            return new CastResult(CastResult.Type.SUCCESS);
        }).filter(result -> result.type == CastResult.Type.FAILURE);
    }

    private static void sendInteractiveLearnPrompt(ServerPlayer player, AbstractSpell spell, int targetLevel, ISpellMasteryData mastery) {
        SpellRarity rarity = spell.getRarity(targetLevel);
        int entryLevel = getMinLevelForRarity(spell, rarity);

        ResourceLocation activeOrigin = player.getCapability(OriginDataProvider.ORIGIN_DATA)
                .map(data -> data.getActiveOrigin()).orElse(null);

        PlayerLevelingData levelingData = PlayerLevelingData.get(player.getServer());
        long availableSP = levelingData.getAvailableSkillPoints(player.getUUID());

        // In-chat prompt uses Knowledge-Only cost
        int cost = getSpellUpgradeCost(spell, entryLevel, mastery, true, activeOrigin);

        MutableComponent msg = Component.literal("\n\u00A7c[Spell Mastery] You haven't learned \u00A7f" + spell.getDisplayName(player).getString() + " (" + rarity.getDisplayName().getString() + " Tier - Level " + entryLevel + ")\u00A7c!\n");
        msg.append(Component.literal("\u00A77Cost to learn knowledge: \u00A7e" + cost + " SP \u00A77(Available SP: \u00A7a" + availableSP + "\u00A77)\n"));

        if (availableSP >= cost) {
            MutableComponent learnBtn = Component.literal("\u00A7a\u00A7l[ ✦ CLICK HERE TO LEARN LEVEL " + entryLevel + " (" + cost + " SP) ]\n")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mastery purchase " + spell.getSpellResource().toString() + " " + entryLevel))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("\u00A7aClick to spend " + cost + " SP and learn " + spell.getDisplayName(player).getString() + " L" + entryLevel + " (" + rarity.getDisplayName().getString() + " Tier)!"))));
            msg.append(learnBtn);
        } else {
            msg.append(Component.literal("\u00A78[ Insufficient SP - Requires " + cost + " SP ]\n"));
        }

        player.sendSystemMessage(msg);
    }

    /**
     * Base SP cost by spell rarity tier.
     * Full Cost (UI Purchase, yields scroll): Common=2, Uncommon=4, Rare=7, Epic=10, Legendary=15.
     * Knowledge Cost (In-Chat Prompt): Common=1, Uncommon=2, Rare=3, Epic=5, Legendary=7.
     */
    public static int getSpellCost(SpellRarity rarity, boolean knowledgeOnly) {
        if (knowledgeOnly) {
            return switch (rarity) {
                case COMMON -> 1;
                case UNCOMMON -> 2;
                case RARE -> 3;
                case EPIC -> 5;
                case LEGENDARY -> 7;
                default -> 1;
            };
        } else {
            return switch (rarity) {
                case COMMON -> 2;
                case UNCOMMON -> 4;
                case RARE -> 7;
                case EPIC -> 10;
                case LEGENDARY -> 15;
                default -> 2;
            };
        }
    }

    public static int getSpellCost(SpellRarity rarity) {
        return getSpellCost(rarity, false);
    }

    /**
     * Calculates the SP cost to unlock a specific tier level of a spell with additive tier credits.
     */
    public static int getSpellUpgradeCost(AbstractSpell spell, int targetLevel, ISpellMasteryData masteryData, boolean knowledgeOnly, ResourceLocation originId) {
        if (spell == null) return 1;
        ResourceLocation spellId = spell.getSpellResource();
        SpellRarity targetRarity = spell.getRarity(targetLevel);
        int targetBaseCost = getSpellCost(targetRarity, knowledgeOnly);

        // Find highest previously learned tier for this spell
        int highestLearnedBaseCost = 0;
        for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
            if (masteryData != null && masteryData.isSpellLearned(spellId, lvl)) {
                SpellRarity r = spell.getRarity(lvl);
                int cost = getSpellCost(r, knowledgeOnly);
                if (cost > highestLearnedBaseCost) {
                    highestLearnedBaseCost = cost;
                }
            }
        }

        // Calculate additive upgrade base cost
        int effectiveBaseCost = targetBaseCost;
        if (highestLearnedBaseCost > 0 && targetBaseCost > highestLearnedBaseCost) {
            effectiveBaseCost = targetBaseCost - highestLearnedBaseCost;
        } else if (highestLearnedBaseCost >= targetBaseCost) {
            return 0;
        }

        double multiplier = (spell.getSchoolType() != null)
                ? ClassCostMatrix.getSchoolSpellMasteryCostMultiplier(originId, spell.getSchoolType())
                : ClassCostMatrix.getSpellMasteryCostMultiplier(originId);
        if (multiplier < 0 || Double.isInfinite(multiplier)) {
            return -1;
        }
        return Math.max(1, (int) Math.round(effectiveBaseCost * multiplier));
    }

    public static int getSpellUpgradeCost(AbstractSpell spell, int targetLevel, ISpellMasteryData masteryData, ResourceLocation originId) {
        return getSpellUpgradeCost(spell, targetLevel, masteryData, false, originId);
    }

    @Deprecated
    public static int getSpellCost(SpellRarity rarity, ResourceLocation originId) {
        int baseCost = getSpellCost(rarity);
        double multiplier = ClassCostMatrix.getSpellMasteryCostMultiplier(originId);
        return (int) Math.round(baseCost * multiplier);
    }

    @Deprecated
    public static int getMasteryBuyUpCost(int tier) {
        return 0;
    }

    @Deprecated
    public static int getMasteryBuyUpCost(int tier, ResourceLocation originId) {
        return 0;
    }
}
