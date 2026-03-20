package com.complextalents.client;

import com.complextalents.leveling.client.ClientLevelingData;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.skill.capability.IPlayerSkillData;
import com.complextalents.skill.client.ClientSkillData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;



/**
 * Centered state manager for all character progression upgrades.
 * Separates UI logic from rendering to ensure consistency.
 */
public class ProgressionStateManager {
    private final Player player;

    // --- Sub-Managers ---
    private final StatsStateManager statsManager;
    private final WeaponStateManager weaponManager;
    private final SpellStateManager spellManager;
    private final OriginStateManager originManager;

    // --- Cached Data ---
    private final List<UpgradeItem> cart = new ArrayList<>();

    private Runnable onChangeCallback;

    public ProgressionStateManager(Player player) {
        this.player = player;
        this.statsManager = new StatsStateManager(this);
        this.weaponManager = new WeaponStateManager(this);
        this.spellManager = new SpellStateManager(this);
        this.originManager = new OriginStateManager(this);
    }

    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    public void notifyUpdate() {
        recalculateTotals();
    }

    public void refreshBaseData() {
        recalculateTotals();
    }

    private void recalculateTotals() {
        if (onChangeCallback != null) onChangeCallback.run();
    }

    // --- Cart Management ---

    public void removeFromCart(UpgradeType type, Object content) {
        cart.removeIf(item -> item.getType() == type && item.getContent().equals(content));
        notifyUpdate();
    }


    public List<UpgradeItem> getCart() {
        return cart;
    }

    public int getPendingAmount(UpgradeType type, Object content) {
        for (UpgradeItem item : cart) {
            if (item.getType() == type && item.getContent().equals(content)) {
                return item.getAmount();
            }
        }
        return 0;
    }

    public void modifyCartItem(UpgradeType type, Object content, int amountDelta, int costDelta) {
        for (UpgradeItem item : cart) {
            if (item.getType() == type && item.getContent().equals(content)) {
                int nextAmount = item.getAmount() + amountDelta;
                if (nextAmount <= 0) {
                    cart.remove(item);
                } else {
                    item.setAmount(nextAmount);
                    item.setTotalCost(item.getTotalCost() + costDelta);
                }
                notifyUpdate();
                return;
            }
        }
        if (amountDelta > 0) {
            cart.add(new UpgradeItem(content, costDelta, amountDelta, type));
            notifyUpdate();
        }
    }

    public void clearCart() {
        cart.clear();
        notifyUpdate();
    }

    // --- Global Accessors ---

    public Player getPlayer() {
        return player;
    }

    public long getRemainingSP() {
        return (long) ClientLevelingData.getAvailableSkillPoints() - getTotalPendingCost();
    }

    public int getTotalPendingCost() {
        int cost = 0;
        for (UpgradeItem item : cart) {
            cost += item.getTotalCost();
        }
        return cost;
    }

    public boolean canAfford(int cost) {
        int currentTotal = 0;
        for (UpgradeItem item : cart) {
            currentTotal += item.getTotalCost();
        }
        return ClientLevelingData.getAvailableSkillPoints() >= (currentTotal + cost);
    }

    public boolean canAdd(int cost) {
        return canAfford(cost);
    }

    // --- Sub-Manager Accessors ---

    public StatsStateManager getStatsManager() { return statsManager; }
    public WeaponStateManager getWeaponManager() { return weaponManager; }
    public SpellStateManager getSpellManager() { return spellManager; }
    public OriginStateManager getOriginManager() { return originManager; }

    // --- Utility (Used by sub-managers) ---

    public ResourceLocation getActiveOrigin() {
        return player.getCapability(com.complextalents.origin.capability.OriginDataProvider.ORIGIN_DATA)
                .map(com.complextalents.origin.capability.IPlayerOriginData::getActiveOrigin)
                .orElse(null);
    }

    public int getActiveSkillLevel() {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) return 0;
        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null || origin.getActiveSkillId() == null) return 0;
        ResourceLocation skillId = origin.getActiveSkillId();
        
        for (int i = 0; i < IPlayerSkillData.SLOT_COUNT; i++) {
            if (skillId.equals(ClientSkillData.getSkillInSlot(i))) return ClientSkillData.getSkillLevel(skillId);
        }
        return 0;
    }
}
