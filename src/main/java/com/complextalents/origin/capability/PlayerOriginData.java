package com.complextalents.origin.capability;

import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.ResourceType;
import com.complextalents.origin.network.OriginDataSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of current player origin data.
 */
public class PlayerOriginData implements IPlayerOriginData {

    private ServerPlayer player;
    private ResourceLocation activeOrigin = null;
    private int originLevel = 1;
    private double resourceValue = 0;

    // Temporary shield data for HUD (not persisted)
    private double shieldValue = 0;
    private double shieldMax = 0;

    /**
     * Default constructor for persistence storage.
     */
    public PlayerOriginData() {
        // Player will be set later via setPlayer method
    }

    public PlayerOriginData(ServerPlayer player) {
        this.player = player;
    }

    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    @Nullable
    public ResourceLocation getActiveOrigin() {
        return activeOrigin;
    }

    @Override
    public void setActiveOrigin(@Nullable ResourceLocation originId) {
        this.activeOrigin = originId;
        this.originLevel = 1;
        ResourceType resourceType = getResourceType();
        this.resourceValue = resourceType != null ? resourceType.getMin() : 0;
        
        if (player != null) {
            player.getCapability(com.complextalents.passive.capability.PassiveStackDataProvider.PASSIVE_STACK_DATA)
                    .ifPresent(data -> data.resetPassiveStacks());
        }
        sync();
    }

    @Override
    public int getOriginLevel() {
        return originLevel;
    }

    @Override
    public void setOriginLevel(int level) {
        Origin origin = getOrigin();
        int maxLevel = origin != null ? origin.getMaxLevel() : 1;
        this.originLevel = Math.max(1, Math.min(level, maxLevel));
        sync();
    }

    @Override
    @Nullable
    public ResourceType getResourceType() {
        Origin origin = getOrigin();
        return origin != null ? origin.getResourceType() : null;
    }

    @Override
    public double getResource() {
        return resourceValue;
    }

    @Override
    public void setResource(double value) {
        ResourceType resourceType = getResourceType();
        if (resourceType != null) {
            Origin origin = getOrigin();
            double max = origin != null ? origin.getMaxResource(originLevel, player) : resourceType.getMax();
            this.resourceValue = Math.max(resourceType.getMin(), Math.min(max, value));
        } else {
            this.resourceValue = value;
        }
    }

    @Override
    public void modifyResource(double delta) {
        double oldValue = resourceValue;
        setResource(resourceValue + delta);
        if (oldValue != resourceValue) {
            sync();
        }
    }

    @Override
    public void sync() {
        if (player == null) return;
        ResourceType resourceType = getResourceType();
        ResourceLocation resourceTypeId = resourceType != null ? resourceType.getId() : null;

        double resourceMax;
        Origin origin = getOrigin();
        if (origin != null) {
            resourceMax = origin.getMaxResource(originLevel, player);
        } else if (resourceType != null) {
            resourceMax = resourceType.getMax();
        } else {
            resourceMax = 0;
        }

        OriginDataSyncPacket.send(
                player,
                activeOrigin,
                originLevel,
                resourceValue,
                resourceMax,
                resourceTypeId,
                shieldValue,
                shieldMax
        );
    }

    @Override
    public double getShieldValue() { return shieldValue; }

    @Override
    public void setShieldValue(double value) {
        this.shieldValue = value;
        sync();
    }

    @Override
    public double getShieldMax() { return shieldMax; }

    @Override
    public void setShieldMax(double value) {
        this.shieldMax = value;
        sync();
    }

    @Override
    public void clear() {
        activeOrigin = null;
        originLevel = 1;
        resourceValue = 0;
        sync();
    }

    @Override
    public void tick() {
        // Handle tick behaviors if any
    }

    @Override
    public void copyFrom(IPlayerOriginData other) {
        activeOrigin = other.getActiveOrigin();
        originLevel = other.getOriginLevel();
        resourceValue = other.getResource();
        shieldValue = other.getShieldValue();
        shieldMax = other.getShieldMax();
        sync();
    }

    @Nullable
    private Origin getOrigin() {
        if (activeOrigin == null) return null;
        return OriginRegistry.getInstance().getOrigin(activeOrigin);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (activeOrigin != null) tag.putString("activeOrigin", activeOrigin.toString());
        tag.putInt("originLevel", originLevel);
        tag.putDouble("resourceValue", resourceValue);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        activeOrigin = tag.contains("activeOrigin") ? ResourceLocation.tryParse(tag.getString("activeOrigin")) : null;
        originLevel = tag.contains("originLevel") ? tag.getInt("originLevel") : 1;
        resourceValue = tag.contains("resourceValue") ? tag.getDouble("resourceValue") : 0;
    }
}
