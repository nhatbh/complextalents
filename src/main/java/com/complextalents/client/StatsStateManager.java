package com.complextalents.client;

import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import net.minecraft.resources.ResourceLocation;

public class StatsStateManager {
    private final ProgressionStateManager parent;

    public StatsStateManager(ProgressionStateManager parent) {
        this.parent = parent;
    }

    public ProgressionStateManager getParent() {
        return parent;
    }

    public int getPending(StatType type) {
        return parent.getPendingAmount(UpgradeType.STAT, type);
    }

    public void adjust(StatType type, int delta) {
        ResourceLocation originId = parent.getActiveOrigin();
        int cost = ClassCostMatrix.getCost(originId, type);
        
        if (delta > 0) {
            if (parent.canAfford(cost)) {
                parent.modifyCartItem(UpgradeType.STAT, type, 1, cost);
            }
        } else if (delta < 0) {
            parent.modifyCartItem(UpgradeType.STAT, type, -1, -cost);
        }
    }
}
