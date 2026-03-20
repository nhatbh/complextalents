package com.complextalents.client;

import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;

public class WeaponStateManager {
    private final ProgressionStateManager parent;

    public WeaponStateManager(ProgressionStateManager parent) {
        this.parent = parent;
    }

    public ProgressionStateManager getParent() {
        return parent;
    }

    public int getPending(IWeaponMasteryData.WeaponPath path) {
        return parent.getPendingAmount(UpgradeType.WEAPON, path);
    }

    public void adjust(IWeaponMasteryData.WeaponPath path, int delta) {
        int current = getPending(path);

        if (delta > 0) {
             parent.getPlayer().getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
                 int realLevel = data.getMasteryLevel(path);
                 int targetLevel = realLevel + current;
                if (targetLevel < 25) {
                    int nextLevelCost = WeaponMasteryManager.getInstance().getSPCostForNextLevel(targetLevel);
                    double damage = data.getAccumulatedDamage(path);
                    double req = WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(targetLevel);
                    
                    if (parent.canAfford(nextLevelCost) && damage >= req) {
                        parent.modifyCartItem(UpgradeType.WEAPON, path, 1, nextLevelCost);
                    }
                }
            });
        } else if (delta < 0 && current > 0) {
            parent.getPlayer().getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
                int realLevel = data.getMasteryLevel(path);
                int removedLevelCost = WeaponMasteryManager.getInstance().getSPCostForNextLevel(realLevel + current - 1);
                parent.modifyCartItem(UpgradeType.WEAPON, path, -1, -removedLevelCost);
            });
        }
    }
}
