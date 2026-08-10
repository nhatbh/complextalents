package com.complextalents.api.impl;

import com.complextalents.api.spellmastery.ISpellMasteryAPI;
import com.complextalents.spellmastery.capability.ISpellMasteryData;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SpellMasteryAPIImpl implements ISpellMasteryAPI {

    private Optional<ISpellMasteryData> getCapability(Player player) {
        if (player == null) return Optional.empty();
        return player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).resolve();
    }

    @Override
    public int getMasteryLevel(Player player, ResourceLocation schoolId) {
        return getCapability(player).map(data -> data.getMasteryLevel(schoolId)).orElse(0);
    }

    @Override
    public void setMasteryLevel(Player player, ResourceLocation schoolId, int level) {
        getCapability(player).ifPresent(data -> {
            data.setMasteryLevel(schoolId, level);
            data.sync();
        });
    }

    @Override
    public boolean isSpellLearned(Player player, ResourceLocation spellId, int level) {
        return getCapability(player).map(data -> data.isSpellLearned(spellId, level)).orElse(false);
    }

    @Override
    public void learnSpell(Player player, ResourceLocation spellId, int level) {
        getCapability(player).ifPresent(data -> {
            data.learnSpell(spellId, level);
            data.sync();
        });
    }

    @Override
    public void forgetSpell(Player player, ResourceLocation spellId) {
        getCapability(player).ifPresent(data -> {
            data.forgetSpell(spellId);
            data.sync();
        });
    }

    @Override
    public Set<ResourceLocation> getLearnedSpells(Player player) {
        return getCapability(player).map(ISpellMasteryData::getLearnedSpells).orElse(Collections.emptySet());
    }

    @Override
    public Map<ResourceLocation, Integer> getAllMasteryLevels(Player player) {
        return getCapability(player).map(ISpellMasteryData::getAllMasteryLevels).orElse(Collections.emptyMap());
    }

    @Override
    public int getPurchasedMastery(Player player, ResourceLocation schoolId) {
        return getCapability(player).map(data -> data.getPurchasedMastery(schoolId)).orElse(0);
    }

    @Override
    public void purchaseMastery(Player player, ResourceLocation schoolId, int tier, int cost) {
        getCapability(player).ifPresent(data -> {
            data.purchaseMastery(schoolId, tier, cost);
            data.sync();
        });
    }
}
