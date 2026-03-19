package com.complextalents.stats.client;

import com.complextalents.network.PacketHandler;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import com.complextalents.stats.capability.GeneralStatsDataProvider;
import com.complextalents.stats.network.PurchaseStatsPacket;
import com.complextalents.dev.SimpleUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsUI {
    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath("complextalents", "stats");

    private final Player player;
    private final IUIHolder holder;

    private WidgetGroup statListContainer;
    private final Map<String, Integer> pendingUpgrades = new HashMap<>();
    private LabelWidget spLabel;
    private LabelWidget consumedLabel;
    private ButtonWidget confirmBtn;

    private WidgetGroup accumulatedPanel;

    public StatsUI(Player player, IUIHolder holder) {
        this.player = player;
        this.holder = holder;
    }

    public static void init() {
        SimpleUIFactory.register(UI_ID, (player, holder) -> new StatsUI(player, holder).createUI());
    }

    public ModularUI createUI() {
        WidgetGroup root = new WidgetGroup();
        root.setSize(500, 270);
        root.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);

        // Header
        LabelWidget title = new LabelWidget();
        title.setSelfPosition(10, 10);
        title.setText("§lStat Market");
        root.addWidget(title);

        spLabel = new LabelWidget();
        spLabel.setSelfPosition(400, 10);
        spLabel.setTextProvider(() -> "SP: §e" + (getAvailableSP() - calculatePendingCost()));
        root.addWidget(spLabel);

        // Scrollable List of Stats (Left side)
        DraggableScrollableWidgetGroup scrollable = new DraggableScrollableWidgetGroup();
        scrollable.setSelfPosition(10, 35);
        scrollable.setSize(310, 195);

        statListContainer = new WidgetGroup();
        
        // Accumulated Panel (Right side)
        accumulatedPanel = new WidgetGroup();
        accumulatedPanel.setSelfPosition(330, 35);
        accumulatedPanel.setSize(160, 195);
        accumulatedPanel.setBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF111111));
        
        updateStatList();

        scrollable.addWidget(statListContainer);
        root.addWidget(scrollable);
        root.addWidget(accumulatedPanel);

        // Footer
        WidgetGroup footer = new WidgetGroup();
        footer.setSelfPosition(10, 240);
        footer.setSize(480, 25);

        consumedLabel = new LabelWidget();
        consumedLabel.setSelfPosition(0, 5);
        consumedLabel.setTextProvider(() -> "To Consume: §c" + calculatePendingCost() + " SP");
        footer.addWidget(consumedLabel);

        confirmBtn = new ButtonWidget();
        confirmBtn.setSelfPosition(380, 0);
        confirmBtn.setSize(100, 18);
        confirmBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444), new TextTexture("Finalize"));
        confirmBtn.setOnPressCallback(clickData -> {
            if (!pendingUpgrades.isEmpty()) {
                PacketHandler.sendToServer(new PurchaseStatsPacket(pendingUpgrades));
                pendingUpgrades.clear();
                player.closeContainer();
            }
        });
        footer.addWidget(confirmBtn);

        root.addWidget(footer);
        updateFooter();

        return new ModularUI(root, holder, player);
    }

    private int getAvailableSP() {
        return player.getCapability(GeneralStatsDataProvider.STATS_DATA)
                .map(data -> data.getSkillPoints())
                .orElse(0);
    }

    private ResourceLocation getActiveOrigin() {
        return player.getCapability(OriginDataProvider.ORIGIN_DATA)
                .map(data -> data.getActiveOrigin())
                .orElse(null);
    }

    private void updateStatList() {
        statListContainer.clearAllWidgets();
        accumulatedPanel.clearAllWidgets();

        // Accumulated Panel Header
        LabelWidget accTitle = new LabelWidget();
        accTitle.setSelfPosition(5, 5);
        accTitle.setText("§lAccumulated Stats");
        accumulatedPanel.addWidget(accTitle);

        player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(data -> {
            int xOffset = 0;
            int yOffset = 0;
            int col = 0;
            
            int accY = 20;

            for (StatType type : StatType.values()) {
                // Populate Stat Card
                WidgetGroup entry = createStatEntry(type, data);
                if (entry != null) {
                    entry.setSelfPosition(xOffset, yOffset);
                    statListContainer.addWidget(entry);
                    
                    col++;
                    if (col >= 2) {
                        col = 0;
                        xOffset = 0;
                        yOffset += 65; // 60px height + 5px spacing
                    } else {
                        xOffset += 150; // 145px width + 5px spacing
                    }
                }

                // Populate Accumulated Panel Row
                int rank = data.getStatRank(type);
                int pending = pendingUpgrades.getOrDefault(type.name(), 0);
                int totalRank = rank + pending;
                if (totalRank > 0) {
                    double val = totalRank * type.getYieldPerRank();
                    LabelWidget l = new LabelWidget();
                    l.setSelfPosition(5, accY);
                    
                    String formattedVal = String.format("%.2f", val).replace(".00", ""); // Clean trailing zeros
                    String valText = "§f+" + formattedVal;
                    if (pending > 0) {
                        valText = "§a+" + formattedVal; // Highlight if increased
                    }
                    
                    l.setText("§7" + type.getDisplayName().replace("%", "%%") + ": " + valText);
                    accumulatedPanel.addWidget(l);
                    accY += 15;
                }
            }
            
            // Adjust height if the last row is half full
            if (col > 0) yOffset += 65;
            statListContainer.setSize(300, Math.max(195, yOffset));
        });
    }

    private WidgetGroup createStatEntry(StatType type, com.complextalents.stats.capability.IGeneralStatsData data) {
        WidgetGroup widget = new WidgetGroup();
        widget.setSize(145, 60);
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON);

        int realCurrentRank = data.getStatRank(type);
        int pendingPurchases = pendingUpgrades.getOrDefault(type.name(), 0);
        
        ResourceLocation originId = getActiveOrigin();
        int costPerRank = ClassCostMatrix.getCost(originId, type);

        // Stat Name
        LabelWidget nameLabel = new LabelWidget();
        nameLabel.setSelfPosition(5, 2);
        nameLabel.setText("§l" + type.getDisplayName().replace("%", "%%"));
        widget.addWidget(nameLabel);

        // Rank Display
        LabelWidget tierLabel = new LabelWidget();
        tierLabel.setSelfPosition(5, 14);
        if (pendingPurchases > 0) {
            tierLabel.setText("§7Rank: " + realCurrentRank + " §a(+" + pendingPurchases + ")");
        } else {
            tierLabel.setText("§7Rank: " + realCurrentRank);
        }
        widget.addWidget(tierLabel);

        // Cost
        LabelWidget costLabel = new LabelWidget();
        costLabel.setSelfPosition(5, 26);
        costLabel.setText("Cost: §e" + costPerRank + " SP/Rank");
        widget.addWidget(costLabel);

        // Cancel Button
        if (pendingPurchases > 0) {
            ButtonWidget cancelBtn = new ButtonWidget();
            cancelBtn.setSelfPosition(45, 38);
            cancelBtn.setSize(45, 18);
            cancelBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFFCC4444), new TextTexture("§cCancel"));
            cancelBtn.setOnPressCallback(clickData -> {
                pendingUpgrades.put(type.name(), pendingPurchases - 1);
                if (pendingUpgrades.get(type.name()) <= 0) pendingUpgrades.remove(type.name());
                updateStatList();
                updateFooter();
            });
            widget.addWidget(cancelBtn);
        }

        // Upgrade Button
        ButtonWidget upgradeBtn = new ButtonWidget();
        upgradeBtn.setSelfPosition(95, 38);
        upgradeBtn.setSize(45, 18);

        long availableSP = getAvailableSP() - calculatePendingCost();
        
        if (availableSP >= costPerRank) {
            upgradeBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("§aAdd"));
            upgradeBtn.setOnPressCallback(clickData -> {
                pendingUpgrades.put(type.name(), pendingPurchases + 1);
                updateStatList();
                updateFooter();
            });
        } else {
            upgradeBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§cAdd"));
            List<String> tooltip = new ArrayList<>();
            tooltip.add("§cRequirements not met:");
            tooltip.add("§7- Insufficient SP (Need " + costPerRank + ")");
            upgradeBtn.setHoverTooltips(tooltip.toArray(new String[0]));
        }
        widget.addWidget(upgradeBtn);

        return widget;
    }

    private void updateFooter() {
        if (pendingUpgrades.isEmpty()) {
            confirmBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444), new TextTexture("Finalize"));
        } else {
            confirmBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF22AA22), new TextTexture("§fFinalize"));
        }
    }

    private int calculatePendingCost() {
        ResourceLocation originId = getActiveOrigin();
        int totalCost = 0;
        for (Map.Entry<String, Integer> entry : pendingUpgrades.entrySet()) {
            try {
                StatType type = StatType.valueOf(entry.getKey());
                int amount = entry.getValue();
                totalCost += ClassCostMatrix.getCost(originId, type) * amount;
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }
        return totalCost;
    }
}
