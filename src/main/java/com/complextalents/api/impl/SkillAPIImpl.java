package com.complextalents.api.impl;

import com.complextalents.api.skill.ISkillAPI;
import com.complextalents.skill.capability.IPlayerSkillData;
import com.complextalents.skill.capability.SkillDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class SkillAPIImpl implements ISkillAPI {

    private Optional<IPlayerSkillData> getCapability(Player player) {
        if (player == null) return Optional.empty();
        return player.getCapability(SkillDataProvider.SKILL_DATA).resolve();
    }

    @Override
    public ResourceLocation getSkillInSlot(Player player, int slotIndex) {
        return getCapability(player).map(data -> data.getSkillInSlot(slotIndex)).orElse(null);
    }

    @Override
    public void setSkillInSlot(Player player, int slotIndex, ResourceLocation skillId) {
        getCapability(player).ifPresent(data -> {
            data.setSkillInSlot(slotIndex, skillId);
            data.sync();
        });
    }

    @Override
    public int getSkillLevel(Player player, ResourceLocation skillId) {
        return getCapability(player).map(data -> data.getSkillLevel(skillId)).orElse(1);
    }

    @Override
    public void setSkillLevel(Player player, ResourceLocation skillId, int level) {
        getCapability(player).ifPresent(data -> {
            data.setSkillLevel(skillId, Math.max(1, level));
            data.sync();
        });
    }

    @Override
    public boolean isOnCooldown(Player player, ResourceLocation skillId) {
        return getCapability(player).map(data -> data.isOnCooldown(skillId)).orElse(false);
    }

    @Override
    public double getCooldown(Player player, ResourceLocation skillId) {
        return getCapability(player).map(data -> data.getCooldown(skillId)).orElse(0.0);
    }

    @Override
    public void setCooldown(Player player, ResourceLocation skillId, double seconds) {
        getCapability(player).ifPresent(data -> {
            data.setCooldown(skillId, Math.max(0.0, seconds));
            data.sync();
        });
    }

    @Override
    public void clearCooldown(Player player, ResourceLocation skillId) {
        getCapability(player).ifPresent(data -> {
            data.clearCooldown(skillId);
            data.sync();
        });
    }

    @Override
    public Set<ResourceLocation> getAllLearnedSkills(Player player) {
        return getCapability(player).map(IPlayerSkillData::getAllLearnedSkills).orElse(Collections.emptySet());
    }

    @Override
    public ResourceLocation getActiveForm(Player player) {
        return getCapability(player).map(IPlayerSkillData::getActiveForm).orElse(null);
    }

    @Override
    public void setActiveForm(Player player, ResourceLocation formSkillId) {
        getCapability(player).ifPresent(data -> {
            data.setActiveForm(formSkillId);
            data.sync();
        });
    }
}
