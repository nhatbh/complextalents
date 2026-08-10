package com.complextalents.api.impl;

import com.complextalents.api.caseopening.ICaseAPI;
import com.complextalents.caseopening.CaseReward;
import com.complextalents.caseopening.DynamicCasePoolBuilder;
import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.item.MysteriousLootItem;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.caseopening.C2SClaimCaseRewardPacket;
import com.complextalents.network.caseopening.S2COpenCaseScreenPacket;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CaseAPIImpl implements ICaseAPI {

    @Override
    public ItemStack createWeaponCaseItem(WeaponPath path, CrateRarity rarity) {
        return MysteriousLootItem.createWeaponCase(path, rarity);
    }

    @Override
    public ItemStack createMagicCaseItem(ResourceLocation schoolId, CrateRarity rarity) {
        return MysteriousLootItem.createMagicCase(schoolId, rarity);
    }

    @Override
    public void openWeaponCase(ServerPlayer player, WeaponPath path, CrateRarity rarity) {
        if (player == null || path == null || rarity == null) return;
        List<CrateRarity> validRarities = DynamicCasePoolBuilder.getValidRaritiesForWeaponPath(path);
        if (!validRarities.contains(rarity)) {
            rarity = validRarities.get(validRarities.size() - 1);
        }
        List<CaseReward> pool = DynamicCasePoolBuilder.buildWeaponPool(path, rarity);
        sendOpenCasePacket(player, pool);
    }

    @Override
    public void openMagicCase(ServerPlayer player, ResourceLocation schoolId, CrateRarity rarity) {
        if (player == null || schoolId == null || rarity == null) return;
        List<CrateRarity> validRarities = DynamicCasePoolBuilder.getValidRaritiesForSchool(schoolId);
        if (!validRarities.contains(rarity)) {
            rarity = validRarities.get(validRarities.size() - 1);
        }
        List<CaseReward> pool = DynamicCasePoolBuilder.buildMagicPool(schoolId, rarity);
        sendOpenCasePacket(player, pool);
    }

    private void sendOpenCasePacket(ServerPlayer player, List<CaseReward> pool) {
        CaseReward winningReward = DynamicCasePoolBuilder.rollFromPool(pool, player.getRandom());
        int targetWinningIndex = 65 + player.getRandom().nextInt(25);
        int totalCarouselItems = targetWinningIndex + 20;

        List<CaseReward> sequence = new ArrayList<>(totalCarouselItems);
        for (int i = 0; i < totalCarouselItems; i++) {
            if (i == targetWinningIndex) {
                sequence.add(winningReward);
            } else {
                sequence.add(DynamicCasePoolBuilder.rollFromPool(pool, player.getRandom()));
            }
        }

        PacketHandler.sendTo(new S2COpenCaseScreenPacket(sequence, targetWinningIndex, winningReward, pool), player);
    }

    @Override
    public List<CaseReward> buildWeaponPool(WeaponPath path, CrateRarity rarity) {
        return DynamicCasePoolBuilder.buildWeaponPool(path, rarity);
    }

    @Override
    public List<CaseReward> buildMagicPool(ResourceLocation schoolId, CrateRarity rarity) {
        return DynamicCasePoolBuilder.buildMagicPool(schoolId, rarity);
    }

    @Override
    public CaseReward rollFromPool(List<CaseReward> pool, RandomSource random) {
        return DynamicCasePoolBuilder.rollFromPool(pool, random);
    }

    @Override
    public void grantRewardToPlayer(ServerPlayer player, CaseReward reward) {
        if (player != null && reward != null) {
            C2SClaimCaseRewardPacket.grantRewardToPlayer(player, reward);
        }
    }
}
