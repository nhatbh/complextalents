package com.complextalents.network;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.leveling.handlers.LevelingSyncHandler;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.skill.capability.IPlayerSkillData;
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import com.complextalents.stats.capability.GeneralStatsDataProvider;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class FinalizePlayerUpgradesPacket {
    private final Map<String, Integer> statsUpgrades;
    private final Map<String, Integer> weaponMasteryUpgrades;
    private final List<UpgradeData.MasteryUpgrade> spellMasteryUpgrades;
    private final List<UpgradeData.SpellPurchase> spellPurchases;
    private final int originUpgrades;
    private final int originSkillUpgrades;

    public FinalizePlayerUpgradesPacket(
            Map<String, Integer> statsUpgrades,
            Map<String, Integer> weaponUpgrades,
            List<UpgradeData.MasteryUpgrade> spellMasteryUpgrades,
            List<UpgradeData.SpellPurchase> spellPurchases,
            int originUpgrades,
            int originSkillUpgrades) {
        this.statsUpgrades = statsUpgrades == null ? new HashMap<>() : statsUpgrades;
        this.weaponMasteryUpgrades = weaponUpgrades == null ? new HashMap<>() : weaponUpgrades;
        this.spellMasteryUpgrades = spellMasteryUpgrades == null ? new ArrayList<>() : spellMasteryUpgrades;
        this.spellPurchases = spellPurchases == null ? new ArrayList<>() : spellPurchases;
        this.originUpgrades = originUpgrades;
        this.originSkillUpgrades = originSkillUpgrades;
    }

    public FinalizePlayerUpgradesPacket(FriendlyByteBuf buf) {
        this.statsUpgrades = new HashMap<>();
        int statsCount = buf.readInt();
        for (int i = 0; i < statsCount; i++) {
            this.statsUpgrades.put(buf.readUtf(), buf.readInt());
        }

        this.weaponMasteryUpgrades = new HashMap<>();
        int weaponCount = buf.readInt();
        for (int i = 0; i < weaponCount; i++) {
            this.weaponMasteryUpgrades.put(buf.readUtf(), buf.readInt());
        }

        int mSize = buf.readVarInt();
        this.spellMasteryUpgrades = new ArrayList<>(mSize);
        for (int i = 0; i < mSize; i++) {
            this.spellMasteryUpgrades.add(new UpgradeData.MasteryUpgrade(buf.readResourceLocation(), buf.readInt()));
        }

        int sSize = buf.readVarInt();
        this.spellPurchases = new ArrayList<>(sSize);
        for (int i = 0; i < sSize; i++) {
            this.spellPurchases.add(new UpgradeData.SpellPurchase(buf.readResourceLocation(), buf.readInt()));
        }

        this.originUpgrades = buf.readInt();
        this.originSkillUpgrades = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(statsUpgrades.size());
        for (Map.Entry<String, Integer> entry : statsUpgrades.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeInt(weaponMasteryUpgrades.size());
        for (Map.Entry<String, Integer> entry : weaponMasteryUpgrades.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeVarInt(spellMasteryUpgrades.size());
        for (UpgradeData.MasteryUpgrade upgrade : spellMasteryUpgrades) {
            buf.writeResourceLocation(upgrade.schoolId());
            buf.writeInt(upgrade.tier());
        }

        buf.writeVarInt(spellPurchases.size());
        for (UpgradeData.SpellPurchase purchase : spellPurchases) {
            buf.writeResourceLocation(purchase.spellId());
            buf.writeInt(purchase.level());
        }

        buf.writeInt(originUpgrades);
        buf.writeInt(originSkillUpgrades);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            PlayerLevelingData levelingData = PlayerLevelingData.get(player.getServer());
            long availableSP = levelingData.getAvailableSkillPoints(player.getUUID());
            long[] totalCost = {0};

            // Variables developed during verification
            Map<StatType, Integer> validatedStats = new HashMap<>();
            Map<IWeaponMasteryData.WeaponPath, Integer> validatedWeaponPaths = new HashMap<>(); // Path -> new level after upgrades
            List<UpgradeData.MasteryUpgrade> validatedSpellMasteries = new ArrayList<>();
            List<UpgradeData.SpellPurchase> validatedSpellPurchases = new ArrayList<>();
            int validatedOriginLevel = -1;
            int validatedSkillLevel = -1;
            boolean newSkillAssigned = false;

            // 1. Verify Stats
            ResourceLocation activeOrigin = player.getCapability(OriginDataProvider.ORIGIN_DATA)
                    .map(data -> data.getActiveOrigin()).orElse(null);
            for (Map.Entry<String, Integer> entry : statsUpgrades.entrySet()) {
                try {
                    StatType type = StatType.valueOf(entry.getKey());
                    int amount = entry.getValue();
                    if (amount > 0) {
                        int costPerRank = ClassCostMatrix.getCost(activeOrigin, type);
                        totalCost[0] += (long) costPerRank * amount;
                        validatedStats.put(type, amount);
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            // 2. Verify Weapon Mastery (Atomic check)
            player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(masteryData -> {
                for (Map.Entry<String, Integer> entry : weaponMasteryUpgrades.entrySet()) {
                    IWeaponMasteryData.WeaponPath path = IWeaponMasteryData.WeaponPath.fromString(entry.getKey());
                    if (path == null) continue;
                    
                    int requestedLevels = entry.getValue();
                    if (requestedLevels <= 0) continue;

                    int currentLevel = masteryData.getMasteryLevel(path);
                    double accumulatedDamage = masteryData.getAccumulatedDamage(path);
                    
                    int proposedNewLevel = currentLevel;
                    boolean valid = true;

                    for (int i = 0; i < requestedLevels; i++) {
                        if (proposedNewLevel >= 25) { valid = false; break; }
                        double requiredDamageForNext = WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(proposedNewLevel);
                        if (accumulatedDamage < requiredDamageForNext) { valid = false; break; }

                        totalCost[0] += WeaponMasteryManager.getInstance().getSPCostForNextLevel(proposedNewLevel, activeOrigin);
                        proposedNewLevel++;
                    }

                    if (valid) {
                        validatedWeaponPaths.put(path, proposedNewLevel);
                    }
                }
            });

            // 3. Verify Spell Mastery
            player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(mastery -> {
                // 1. Calculate and verify Mastery Upgrades
                for (UpgradeData.MasteryUpgrade upgrade : spellMasteryUpgrades) {
                    int currentMastery = mastery.getMasteryLevel(upgrade.schoolId());
                    if (upgrade.tier() == currentMastery + 1) {
                        totalCost[0] += SpellMasteryManager.getMasteryBuyUpCost(upgrade.tier(), activeOrigin);
                        validatedSpellMasteries.add(upgrade);
                    }
                }

                // 2. Calculate and verify Spell Purchases
                for (UpgradeData.SpellPurchase purchase : spellPurchases) {
                    AbstractSpell spell = SpellRegistry.getSpell(purchase.spellId());
                    if (spell != null && !mastery.isSpellLearned(purchase.spellId(), purchase.level())) {
                        totalCost[0] += SpellMasteryManager.getSpellCost(spell.getRarity(purchase.level()), activeOrigin);
                        validatedSpellPurchases.add(purchase);
                    }
                }
            });

            // 4. Verify Origin
            if (originUpgrades > 0) {
                int currentLevel = OriginManager.getOriginLevel(player);
                int targetLevel = currentLevel;
                for (int i = 0; i < originUpgrades; i++) {
                    if (targetLevel >= 5) break;
                    int cost = OriginManager.getCostForNextLevel(targetLevel);
                    if (cost <= 0) break;
                    totalCost[0] += cost;
                    targetLevel++;
                }
                if (targetLevel > currentLevel) {
                    validatedOriginLevel = targetLevel;
                }
            }

            // 5. Verify Origin Skill
            if (originSkillUpgrades > 0) {
                Origin origin = OriginManager.getOrigin(player);
                if (origin != null && origin.getActiveSkillId() != null) {
                    ResourceLocation skillId = origin.getActiveSkillId();
                    IPlayerSkillData skillData = player.getCapability(com.complextalents.skill.capability.SkillDataProvider.SKILL_DATA).orElse(null);
                    if (skillData != null) {
                        boolean isAssigned = false;
                        for (int i = 0; i < IPlayerSkillData.SLOT_COUNT; i++) {
                            if (skillId.equals(skillData.getSkillInSlot(i))) {
                                isAssigned = true;
                                break;
                            }
                        }

                        int currentLevel = isAssigned ? skillData.getSkillLevel(skillId) : 0;
                        int targetLevel = currentLevel;
                        
                        for (int i = 0; i < originSkillUpgrades; i++) {
                            if (targetLevel >= 5) break;
                            int cost = OriginManager.getSkillCostForNextLevel(targetLevel);
                            if (cost <= 0) break;
                            totalCost[0] += cost;
                            targetLevel++;
                        }
                        if (targetLevel > currentLevel) {
                            validatedSkillLevel = targetLevel;
                            if (!isAssigned) newSkillAssigned = true;
                        }
                    }
                }
            }

            if (totalCost[0] > 0 && availableSP >= totalCost[0]) {
                // Deduct SP once
                levelingData.setConsumedSkillPoints(player.getUUID(), levelingData.getConsumedSkillPoints(player.getUUID()) + (int) totalCost[0]);

                // Apply Stats
                player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(stats -> {
                    for (Map.Entry<StatType, Integer> entry : validatedStats.entrySet()) {
                        StatType type = entry.getKey();
                        stats.setStatRank(type, stats.getStatRank(type) + entry.getValue());
                    }
                    stats.sync();
                });

                // Apply Weapons
                player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(masteryData -> {
                    for (Map.Entry<IWeaponMasteryData.WeaponPath, Integer> entry : validatedWeaponPaths.entrySet()) {
                        masteryData.setMasteryLevel(entry.getKey(), entry.getValue());
                    }
                    masteryData.sync();
                });

                // Apply Spells
                player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(mastery -> {
                    // 3. Process Mastery Upgrades FIRST
                    for (UpgradeData.MasteryUpgrade upgrade : validatedSpellMasteries) {
                        int currentMastery = mastery.getMasteryLevel(upgrade.schoolId());
                        if (upgrade.tier() == currentMastery + 1) {
                            int cost = SpellMasteryManager.getMasteryBuyUpCost(upgrade.tier(), activeOrigin);
                            mastery.purchaseMastery(upgrade.schoolId(), upgrade.tier(), cost);
                        }
                    }

                    // 4. Process Spell Purchases
                    for (UpgradeData.SpellPurchase purchase : validatedSpellPurchases) {
                        AbstractSpell spell = SpellRegistry.getSpell(purchase.spellId());
                        if (spell != null && !mastery.isSpellLearned(purchase.spellId(), purchase.level())) {
                            int cost = SpellMasteryManager.getSpellCost(spell.getRarity(purchase.level()), activeOrigin);

                            // Double check mastery requirements (after upgrades)
                            int reqMastery = spell.getRarity(purchase.level()).getValue();
                            if (mastery.getMasteryLevel(spell.getSchoolType().getId()) >= reqMastery) {
                                mastery.learnSpell(purchase.spellId(), purchase.level());
                                giveScroll(ctx.getSender(), purchase.spellId(), purchase.level());
                            } else {
                                totalCost[0] -= cost;
                            }
                        }
                    }
                    mastery.sync();
                });

                // Apply Origin
                if (validatedOriginLevel > 0) {
                    OriginManager.setOriginLevel(player, validatedOriginLevel);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A7aOrigin upgraded to level " + validatedOriginLevel + "!"));
                }

                // Apply Origin Skill
                if (validatedSkillLevel > 0) {
                    Origin origin = OriginManager.getOrigin(player);
                    ResourceLocation skillId = origin.getActiveSkillId();
                    IPlayerSkillData skillData = player.getCapability(com.complextalents.skill.capability.SkillDataProvider.SKILL_DATA).orElse(null);
                    if (skillId != null && skillData != null) {
                        if (newSkillAssigned) {
                            skillData.setSkillInSlot(0, skillId);
                        }
                        skillData.setSkillLevel(skillId, validatedSkillLevel);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A7aOrigin skill upgraded to level " + validatedSkillLevel + "!"));
                    }
                }

                LevelingSyncHandler.syncPlayerLevelData(player);

                // Send feedback message about total SP spent
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A76Spent \u00A7b" + totalCost[0] + " SP"));
            }
        });
        ctx.setPacketHandled(true);
    }

    private void giveScroll(ServerPlayer player, ResourceLocation spellId, int level) {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        CompoundTag tag = scroll.getOrCreateTag();
        CompoundTag isbSpells = new CompoundTag();
        isbSpells.putInt("maxSpells", 1);
        isbSpells.putBoolean("mustEquip", false);
        ListTag dataList = new ListTag();
        CompoundTag spellEntry = new CompoundTag();
        spellEntry.putInt("index", 0);
        spellEntry.putString("id", spellId.toString());
        spellEntry.putBoolean("locked", true);
        spellEntry.putInt("level", level);
        dataList.add(spellEntry);
        isbSpells.put("data", dataList);
        isbSpells.putBoolean("spellWheel", false);
        tag.put("ISB_Spells", isbSpells);

        if (!player.getInventory().add(scroll)) {
            player.drop(scroll, false);
        }
    }
}
