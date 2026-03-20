package com.complextalents.client;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple data structure to manage pending upgrades in a "Cart".
 * All progression tabs should interact with this class directly.
 */
public class UpgradeCart {
    private final Player player;
    private final List<UpgradeItem> items = new ArrayList<>();
    private Runnable onChangeCallback;

    public UpgradeCart(Player player) {
        this.player = player;
    }

    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    public List<UpgradeItem> getItems() {
        return items;
    }

    public void modifyItem(UpgradeType type, Object content, int amountDelta, int costDelta) {
        for (UpgradeItem item : items) {
            if (item.getType() == type && item.getContent().equals(content)) {
                int nextAmount = item.getAmount() + amountDelta;
                if (nextAmount <= 0) {
                    items.remove(item);
                } else {
                    item.setAmount(nextAmount);
                    item.setTotalCost(item.getTotalCost() + costDelta);
                }
                notifyUpdate();
                return;
            }
        }
        if (amountDelta > 0) {
            items.add(new UpgradeItem(content, costDelta, amountDelta, type));
            notifyUpdate();
        }
    }

    public void removeItem(UpgradeType type, Object content) {
        items.removeIf(item -> item.getType() == type && item.getContent().equals(content));
        notifyUpdate();
    }

    public int getAmount(UpgradeType type, Object content) {
        for (UpgradeItem item : items) {
            if (item.getType() == type && item.getContent().equals(content)) {
                return item.getAmount();
            }
        }
        return 0;
    }

    public void clear() {
        items.clear();
        notifyUpdate();
    }

    public int getTotalCost() {
        int cost = 0;
        for (UpgradeItem item : items) {
            cost += item.getTotalCost();
        }
        return cost;
    }

    public long getRemainingSP() {
        if (!net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) return Long.MAX_VALUE;
        return (long) com.complextalents.leveling.client.ClientLevelingData.getAvailableSkillPoints() - getTotalCost();
    }

    public boolean canAfford(int cost) {
        if (!net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) return false;
        return getRemainingSP() >= cost;
    }

    public void notifyUpdate() {
        if (onChangeCallback != null) onChangeCallback.run();
    }

    public Player getPlayer() {
        return player;
    }
}
