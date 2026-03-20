package com.complextalents.impl.darkmage.origin;

import com.complextalents.TalentsMod;
import com.complextalents.impl.darkmage.client.DarkMageRenderer;
import com.complextalents.impl.darkmage.data.SoulData;
import com.complextalents.origin.OriginBuilder;
import com.complextalents.origin.OriginManager;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dark Mage Origin - Infinite Scaling Soul Harvester.
 * <p>
 * A high-risk, high-reward playstyle that rewards sustained combat.
 * The longer you fight and kill, the stronger you become.
 * Death is punishing but not permanent - Phylactery saves you at the cost of souls.
 * </p>
 * <p>
 * Offers exponential HP-for-Power scaling. Use Blood Pact to tap into your soul reserves,
 * granting increased cast speed and soul-scaled mana regeneration.
 * </p>
 *
 * <h3>Passive: Soul Siphon</h3>
 * <ul>
 *   <li>Gain souls from killed enemies (amount = enemy max health / 40)</li>
 *   <li>Souls are UNCAPPED - can grow indefinitely</li>
 *   <li>Souls provide damage bonus ONLY during Blood Pact</li>
 * </ul>
 *
 * <h3>Passive: Phylactery (Death-Defy)</h3>
 * <ul>
 *   <li>Auto-triggers on fatal damage if souls > 0</li>
 *   <li>Sets HP to 1, loses 50% of souls</li>
 *   <li>5-minute internal cooldown</li>
 * </ul>
 *
 * <h3>Active: Blood Pact (Toggle)</h3>
 * <ul>
 *   <li>HP drain per second: 8%/7%/6%/5%/4% (by level)</li>
 *   <li>Cast Speed bonus: +10%/20%/30%/40%/50% (by level)</li>
 *   <li>Soul-scaled Mana Regeneration while active: 1.0 + (souls / 200.0)</li>
 *   <li>Soul damage bonus: +0.05%/0.1%/0.15%/0.2%/0.25% per soul (by level)</li>
 *   <li>30 second cooldown after toggling off</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class DarkMageOrigin {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "dark_mage");

    /**
     * Register the Dark Mage origin.
     * Call this during mod initialization.
     */
    public static void register() {
        OriginBuilder.create("complextalents", "dark_mage")
                .displayName("Dark Mage")
                .description(Component.literal("Soul harvester with infinite scaling. Harvest souls from kills (enemy max HP / 40). Soul Siphon grants +0.05-0.25% damage/soul and +0.08-0.16% spell crit/soul during Blood Pact. Souls are uncapped. Phylactery auto-triggers on fatal damage (if holding souls), saving at 1 HP, costs 50% souls; 5-min cooldown. Death without souls: 30% loss."))
                .maxLevel(5)
                // HP drain rates for Blood Pact: 8%/7%/6%/5%/4% per second
                .scaledStat("bloodPactHpDrainPercent", "HP Drain/sec", new double[]{0.08, 0.07, 0.06, 0.05, 0.04})
                // Cast speed bonus: 10%/20%/30%/40%/50%
                .scaledStat("bloodPactCastSpeedBonus", "Cast Speed Bonus", new double[]{0.10, 0.20, 0.30, 0.40, 0.50})
                // Soul damage bonus per soul: 0.05%/0.1%/0.15%/0.2%/0.25%
                .scaledStat("soulDamageBonusPercent", "Soul Dmg/%%Soul", new double[]{0.0005, 0.001, 0.0015, 0.002, 0.0025})
                // Spell crit chance per soul during Blood Pact: 0.08%/0.1%/0.12%/0.14%/0.16% per soul
                .scaledStat("soulSpellCritPercent", "Soul Spell Crit", new double[]{0.0008, 0.001, 0.0012, 0.0014, 0.0016})
                .passiveSkill("Soul Siphon", "Gain souls from killed enemies. Souls are uncapped and provide a damage bonus during Blood Pact.")
                .passiveSkill("Phylactery", "Auto-triggers on fatal damage if souls > 0, saving you at the cost of 50% souls.")
                .activeSkill("Blood Pact", "Toggle to drain HP for Cast Speed, Mana Regen, and immense Soul-scaled damage.", ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/darkmage/bloodpact.png"))
                .activeSkillId(ResourceLocation.fromNamespaceAndPath("complextalents", "blood_pact"))
                // Phylactery cooldown in seconds: 300s (5 min) at all levels
                .renderer(new DarkMageRenderer())
                .register();

        ClassCostMatrix.defineCosts(ID)
                .cost(StatType.FLAT_AD, 3)
                .cost(StatType.PERCENT_AD, 3)
                .cost(StatType.AP, 1)
                .cost(StatType.ARMOR_PEN, 2)
                .cost(StatType.LUCK_CRIT, 2)
                .cost(StatType.MAX_HP, 2)
                .cost(StatType.MAX_MANA, 2)
                .cost(StatType.MOBILITY, 3)
                .cost(StatType.CDR, 2)
                .spellMasteryCostMultiplier(1.0) // Dark Mage normal with spells, 100% cost
                .weaponMasteryCostMultiplier(2.0); // Dark Mage weak with weapons, 200% cost

        TalentsMod.LOGGER.info("Dark Mage origin registered");
    }

    /**
     * Check if a player is a Dark Mage.
     */
    public static boolean isDarkMage(ServerPlayer player) {
        return ID.equals(OriginManager.getOriginId(player));
    }

    /**
     * Handle player login - sync soul data.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!isDarkMage(player)) {
            return;
        }

        // Sync soul data on login
        SoulData.syncToClient(player);
    }

    /**
     * Handle player logout - cleanup tracking data.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Clean up Blood Pact active tracking (souls persist)
        SoulData.setBloodPactActive(player.getUUID(), false);
    }

    /**
     * Handle player respawn - sync soul data after death.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!isDarkMage(player)) {
            return;
        }

        // Sync soul data after respawn
        SoulData.syncToClient(player);
    }
}
