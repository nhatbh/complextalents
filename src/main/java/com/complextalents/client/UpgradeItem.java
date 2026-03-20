package com.complextalents.client;

public class UpgradeItem {
    private final Object content; // StatType, ResourceLocation (for weapons/spells), etc.
    private int totalCost;
    private int amount;
    private final UpgradeType type;

    public UpgradeItem(Object content, int totalCost, int amount, UpgradeType type) {
        this.content = content;
        this.totalCost = totalCost;
        this.amount = amount;
        this.type = type;
    }

    public Object getContent() { return content; }
    public int getTotalCost() { return totalCost; }
    public int getAmount() { return amount; }
    public UpgradeType getType() { return type; }
    
    public void setAmount(int amount) { this.amount = amount; }
    public void setTotalCost(int totalCost) { this.totalCost = totalCost; }
}
