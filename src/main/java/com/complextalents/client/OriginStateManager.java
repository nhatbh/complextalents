package com.complextalents.client;

import com.complextalents.origin.OriginManager;
import com.complextalents.origin.client.ClientOriginData;

public class OriginStateManager {
    private final ProgressionStateManager parent;

    public OriginStateManager(ProgressionStateManager parent) {
        this.parent = parent;
    }

    public ProgressionStateManager getParent() {
        return parent;
    }

    public int getOriginPending() {
        return parent.getPendingAmount(UpgradeType.ORIGIN, "origin");
    }

    public int getSkillPending() {
        return parent.getPendingAmount(UpgradeType.SKILL, "skill");
    }

    public void adjustOrigin(int delta) {
        int current = getOriginPending();
        int next = current + delta;
        if (next < 0 || (ClientOriginData.getOriginLevel() + next) > 5) return;

        if (delta > 0) {
            int lvl = ClientOriginData.getOriginLevel() + current;
            int cost = OriginManager.getCostForNextLevel(lvl);
            if (parent.canAfford(cost)) {
                parent.modifyCartItem(UpgradeType.ORIGIN, "origin", 1, cost);
            }
        } else if (delta < 0) {
            int lvl = ClientOriginData.getOriginLevel() + current - 1;
            int cost = OriginManager.getCostForNextLevel(lvl);
            parent.modifyCartItem(UpgradeType.ORIGIN, "origin", -1, -cost);
        }
    }

    public void adjustSkill(int delta) {
        int current = getSkillPending();
        int next = current + delta;
        int currentLevel = parent.getActiveSkillLevel();
        if (next < 0 || (currentLevel + next) > 5) return;

        if (delta > 0) {
            int lvl = currentLevel + current;
            int cost = OriginManager.getSkillCostForNextLevel(lvl);
            if (parent.canAfford(cost)) {
                parent.modifyCartItem(UpgradeType.SKILL, "skill", 1, cost);
            }
        } else if (delta < 0) {
            int lvl = currentLevel + current - 1;
            int cost = OriginManager.getSkillCostForNextLevel(lvl);
            parent.modifyCartItem(UpgradeType.SKILL, "skill", -1, -cost);
        }
    }
}
