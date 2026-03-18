package com.complextalents.spellmastery.network;

import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.leveling.handlers.LevelingSyncHandler;
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
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
import java.util.List;
import java.util.function.Supplier;

public class FinalizeGrimoirePacket {
    private final List<MasteryUpgrade> masteryUpgrades;
    private final List<SpellPurchase> spellPurchases;

    public static record MasteryUpgrade(ResourceLocation schoolId, int tier) {}
    public static record SpellPurchase(ResourceLocation spellId, int level) {}

    public FinalizeGrimoirePacket(List<MasteryUpgrade> masteryUpgrades, List<SpellPurchase> spellPurchases) {
        this.masteryUpgrades = masteryUpgrades;
        this.spellPurchases = spellPurchases;
    }

    public FinalizeGrimoirePacket(FriendlyByteBuf buf) {
        int masterySize = buf.readVarInt();
        this.masteryUpgrades = new ArrayList<>(masterySize);
        for (int i = 0; i < masterySize; i++) {
            this.masteryUpgrades.add(new MasteryUpgrade(buf.readResourceLocation(), buf.readInt()));
        }

        int spellSize = buf.readVarInt();
        this.spellPurchases = new ArrayList<>(spellSize);
        for (int i = 0; i < spellSize; i++) {
            this.spellPurchases.add(new SpellPurchase(buf.readResourceLocation(), buf.readInt()));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(masteryUpgrades.size());
        for (MasteryUpgrade upgrade : masteryUpgrades) {
            buf.writeResourceLocation(upgrade.schoolId());
            buf.writeInt(upgrade.tier());
        }

        buf.writeVarInt(spellPurchases.size());
        for (SpellPurchase purchase : spellPurchases) {
            buf.writeResourceLocation(purchase.spellId());
            buf.writeInt(purchase.level());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            PlayerLevelingData levelingData = PlayerLevelingData.get(player.serverLevel());
            player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(mastery -> {
                int totalCost = 0;
                
                // 1. Calculate and verify Mastery Upgrades
                for (MasteryUpgrade upgrade : masteryUpgrades) {
                    int currentMastery = mastery.getMasteryLevel(upgrade.schoolId());
                    if (upgrade.tier() == currentMastery + 1) {
                        totalCost += SpellMasteryManager.getMasteryBuyUpCost(upgrade.tier());
                    }
                }

                // 2. Calculate and verify Spell Purchases
                for (SpellPurchase purchase : spellPurchases) {
                    AbstractSpell spell = SpellRegistry.getSpell(purchase.spellId());
                    if (spell != null && !mastery.isSpellLearned(purchase.spellId(), purchase.level())) {
                        totalCost += SpellMasteryManager.getSpellCost(spell.getRarity(purchase.level()));
                    }
                }

                int availableSP = levelingData.getAvailableSkillPoints(player.getUUID());
                if (availableSP >= totalCost) {
                    // 3. Process Mastery Upgrades FIRST
                    for (MasteryUpgrade upgrade : masteryUpgrades) {
                        int currentMastery = mastery.getMasteryLevel(upgrade.schoolId());
                        if (upgrade.tier() == currentMastery + 1) {
                            int cost = SpellMasteryManager.getMasteryBuyUpCost(upgrade.tier());
                            mastery.purchaseMastery(upgrade.schoolId(), upgrade.tier(), cost);
                        }
                    }

                    // 4. Process Spell Purchases
                    for (SpellPurchase purchase : spellPurchases) {
                        AbstractSpell spell = SpellRegistry.getSpell(purchase.spellId());
                        if (spell != null && !mastery.isSpellLearned(purchase.spellId(), purchase.level())) {
                            int cost = SpellMasteryManager.getSpellCost(spell.getRarity(purchase.level()));
                            
                            // Double check mastery requirements (after upgrades)
                            int reqMastery = spell.getRarity(purchase.level()).getValue();
                            if (mastery.getMasteryLevel(spell.getSchoolType().getId()) >= reqMastery) {
                                mastery.learnSpell(purchase.spellId(), purchase.level());
                                giveScroll(player, purchase.spellId(), purchase.level());
                            } else {
                                // This should ideally not happen if client-side verification is correct
                                totalCost -= cost; 
                            }
                        }
                    }

                    // 5. Deduct total cost and sync
                    int currentConsumed = levelingData.getConsumedSkillPoints(player.getUUID());
                    levelingData.setConsumedSkillPoints(player.getUUID(), currentConsumed + totalCost);
                    LevelingSyncHandler.syncPlayerLevelData(player);
                }
            });
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
