package com.complextalents.client;

import com.complextalents.spellmastery.client.ClientSpellMasteryData;
import net.minecraft.resources.ResourceLocation;

public class SpellStateManager {
    private final ProgressionStateManager parent;

    public SpellStateManager(ProgressionStateManager parent) {
        this.parent = parent;
    }

    public ProgressionStateManager getParent() {
        return parent;
    }

    public boolean isPending(String uniqueId) {
        return parent.getPendingAmount(getTypeFromId(uniqueId), uniqueId) > 0;
    }

    public void toggle(String uniqueId, int cost) {
        if (isPending(uniqueId)) {
            parent.removeFromCart(getTypeFromId(uniqueId), uniqueId);
        } else {
            if (parent.canAfford(cost)) {
                parent.modifyCartItem(getTypeFromId(uniqueId), uniqueId, 1, cost);
            }
        }
    }

    private UpgradeType getTypeFromId(String uniqueId) {
        if (uniqueId.startsWith("M:")) return UpgradeType.SPELL_MASTERY;
        if (uniqueId.startsWith("S:")) return UpgradeType.SPELL_PURCHASE;
        return UpgradeType.SPELL_PURCHASE; // Fallback
    }

    public int getEffectiveMasteryLevel(ResourceLocation schoolId) {
        int current = ClientSpellMasteryData.getMasteryLevel(schoolId);
        int pending = 0;
        for (UpgradeItem item : parent.getCart()) {
            if (item.getType() == UpgradeType.SPELL_MASTERY) {
                String uniqueId = (String) item.getContent();
                String[] parts = uniqueId.substring(2).split("@");
                if (ResourceLocation.parse(parts[0]).equals(schoolId)) {
                    pending = Math.max(pending, Integer.parseInt(parts[1]));
                }
            }
        }
        return Math.max(current, pending);
    }
}
