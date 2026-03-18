package com.complextalents.weaponmastery.client;

import com.complextalents.leveling.client.ClientLevelingData;
import com.complextalents.network.PacketHandler;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;
import com.complextalents.weaponmastery.network.PurchaseWeaponMasteryPacket;
import com.complextalents.dev.SimpleUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class WeaponMasteryUI {
    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath("complextalents", "weapon_mastery");

    private final Player player;
    private final IUIHolder holder;
    
    private WidgetGroup pathListContainer;
    private final Map<String, Integer> pendingUpgrades = new HashMap<>();
    private LabelWidget spLabel;
    private LabelWidget consumedLabel;
    private ButtonWidget confirmBtn;

    public WeaponMasteryUI(Player player, IUIHolder holder) {
        this.player = player;
        this.holder = holder;
    }

    public static void init() {
        SimpleUIFactory.register(UI_ID, (player, holder) -> new WeaponMasteryUI(player, holder).createUI());
    }

    public ModularUI createUI() {
        WidgetGroup root = new WidgetGroup();
        root.setSize(350, 270);
        root.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);

        // Header
        LabelWidget title = new LabelWidget();
        title.setSelfPosition(10, 10);
        title.setText("§lWeapon Mastery");
        root.addWidget(title);

        spLabel = new LabelWidget();
        spLabel.setSelfPosition(250, 10);
        spLabel.setTextProvider(() -> "SP: §e" + (ClientLevelingData.getAvailableSkillPoints() - calculatePendingCost()));
        root.addWidget(spLabel);

        // Scrollable List
        DraggableScrollableWidgetGroup scrollable = new DraggableScrollableWidgetGroup();
        scrollable.setSelfPosition(10, 35);
        scrollable.setSize(330, 195); // Reduced height to make room for footer

        pathListContainer = new WidgetGroup();
        updatePathList();
        scrollable.addWidget(pathListContainer);
        root.addWidget(scrollable);

        // Footer
        WidgetGroup footer = new WidgetGroup();
        footer.setSelfPosition(10, 240);
        footer.setSize(330, 25);

        consumedLabel = new LabelWidget();
        consumedLabel.setSelfPosition(0, 5);
        consumedLabel.setTextProvider(() -> "To Consume: §c" + calculatePendingCost() + " SP");
        footer.addWidget(consumedLabel);

        confirmBtn = new ButtonWidget();
        confirmBtn.setSelfPosition(230, 0);
        confirmBtn.setSize(100, 18);
        confirmBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444), new TextTexture("Finalize"));
        confirmBtn.setOnPressCallback(clickData -> {
            if (!pendingUpgrades.isEmpty()) {
                PacketHandler.sendToServer(new PurchaseWeaponMasteryPacket(pendingUpgrades));
                pendingUpgrades.clear();
                player.closeContainer();
            }
        });
        footer.addWidget(confirmBtn);

        root.addWidget(footer);
        updateFooter();

        return new ModularUI(root, holder, player);
    }

    private void updatePathList() {
        pathListContainer.clearAllWidgets();

        player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).ifPresent(data -> {
            int xOffset = 0;
            int yOffset = 0;
            int col = 0;
            
            for (IWeaponMasteryData.WeaponPath path : IWeaponMasteryData.WeaponPath.values()) {
                WidgetGroup entry = createPathEntry(path, data);
                if (entry != null) {
                    entry.setSelfPosition(xOffset, yOffset);
                    pathListContainer.addWidget(entry);
                    
                    col++;
                    if (col >= 2) {
                        col = 0;
                        xOffset = 0;
                        yOffset += 75; // 70px height + 5px spacing
                    } else {
                        xOffset += 160; // 155px width + 5px spacing
                    }
                }
            }
            
            // Adjust height if the last row is half full
            if (col > 0) yOffset += 75;
            pathListContainer.setSize(320, Math.max(225, yOffset));
        });
    }

    private WidgetGroup createPathEntry(IWeaponMasteryData.WeaponPath path, IWeaponMasteryData data) {
        WidgetGroup widget = new WidgetGroup();
        widget.setSize(155, 70); // Grid entry: 2 columns
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON);

        int realCurrentLevel = data.getMasteryLevel(path);
        int pendingPurchases = pendingUpgrades.getOrDefault(path.name(), 0);
        int currentLevel = realCurrentLevel + pendingPurchases;
        
        double accumulated = data.getAccumulatedDamage(path);
        double requiredDamage = currentLevel < 25 ? WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(currentLevel) : 0;
        int nextCost = currentLevel < 25 ? WeaponMasteryManager.getInstance().getSPCostForNextLevel(currentLevel) : 0;

        // Determine Tier name and color
        String tierName = getTierName(currentLevel);
        String tierColor = getTierColor(currentLevel);

        // Path Name & Level
        LabelWidget nameLabel = new LabelWidget();
        nameLabel.setSelfPosition(5, 5);
        nameLabel.setText("§l" + path.name());
        widget.addWidget(nameLabel);

        LabelWidget tierLabel = new LabelWidget();
        tierLabel.setSelfPosition(5, 15);
        if (pendingPurchases > 0) {
            tierLabel.setText(tierColor + tierName + " §7(Lv." + currentLevel + "/25) §a(+" + pendingPurchases + ")");
        } else {
            tierLabel.setText(tierColor + tierName + " §7(Lv." + currentLevel + "/25)");
        }
        widget.addWidget(tierLabel);

        if (currentLevel >= 25) {
            LabelWidget maxedLabel = new LabelWidget();
            maxedLabel.setSelfPosition(5, 30);
            maxedLabel.setText("§aMaximum Reached");
            widget.addWidget(maxedLabel);

            ButtonWidget controlBtn = new ButtonWidget();
            controlBtn.setSelfPosition(5, 45);
            controlBtn.setSize(145, 20);
            
            if (pendingPurchases > 0) {
                controlBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFFCC4444), new TextTexture("§cCancel"));
                controlBtn.setOnPressCallback(clickData -> {
                    pendingUpgrades.put(path.name(), pendingPurchases - 1);
                    if (pendingUpgrades.get(path.name()) <= 0) pendingUpgrades.remove(path.name());
                    updatePathList();
                    updateFooter();
                });
            } else {
                controlBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§7Maxed"));
            }
            widget.addWidget(controlBtn);
        } else {
            // Damage Progress Bar
            double pct = (accumulated / requiredDamage);
            if (pct > 1.0) pct = 1.0;

            WidgetGroup progressBarBg = new WidgetGroup();
            progressBarBg.setSelfPosition(5, 28);
            progressBarBg.setSize(145, 10);
            progressBarBg.setBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF222222)); // Dark bg
            
            WidgetGroup progressBarFill = new WidgetGroup();
            progressBarFill.setSelfPosition(0, 0);
            progressBarFill.setSize((int) (145 * pct), 10);
            progressBarFill.setBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF22AA22)); // Green fill
            progressBarBg.addWidget(progressBarFill);
            
            LabelWidget pctLabel = new LabelWidget();
            pctLabel.setSelfPosition(2, 1);
            pctLabel.setText("§f" + (int)(pct * 100) + "%%");
            progressBarBg.addWidget(pctLabel);
            
            widget.addWidget(progressBarBg);

            // Cost
            LabelWidget costLabel = new LabelWidget();
            costLabel.setSelfPosition(5, 40);
            costLabel.setText("Cost: §e" + nextCost + " SP");
            widget.addWidget(costLabel);

            // Cancel Button
            if (pendingPurchases > 0) {
                ButtonWidget cancelBtn = new ButtonWidget();
                cancelBtn.setSelfPosition(45, 45);
                cancelBtn.setSize(45, 20);
                cancelBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFFCC4444), new TextTexture("§cCancel"));
                cancelBtn.setOnPressCallback(clickData -> {
                    pendingUpgrades.put(path.name(), pendingPurchases - 1);
                    if (pendingUpgrades.get(path.name()) <= 0) pendingUpgrades.remove(path.name());
                    updatePathList();
                    updateFooter();
                });
                widget.addWidget(cancelBtn);
            }

            // Upgrade Button
            ButtonWidget upgradeBtn = new ButtonWidget();
            upgradeBtn.setSelfPosition(95, 45);
            upgradeBtn.setSize(55, 20);

            long availableSP = ClientLevelingData.getAvailableSkillPoints() - calculatePendingCost();
            
            if (accumulated >= requiredDamage && availableSP >= nextCost) {
                upgradeBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("§aAdd"));
                upgradeBtn.setOnPressCallback(clickData -> {
                    pendingUpgrades.put(path.name(), pendingPurchases + 1);
                    updatePathList();
                    updateFooter();
                });
            } else {
                upgradeBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§cAdd"));
                List<String> tooltip = new ArrayList<>();
                tooltip.add("§cRequirements not met:");
                if (accumulated < requiredDamage) {
                    tooltip.add("§7- Deal more damage with " + path.name() + " weapons");
                    tooltip.add(String.format("§7- Damage: %.0f / %.0f", accumulated, requiredDamage));
                }
                if (availableSP < nextCost) {
                    tooltip.add("§7- Insufficient SP (Need " + nextCost + ")");
                }
                upgradeBtn.setHoverTooltips(tooltip.toArray(new String[0]));
            }
            widget.addWidget(upgradeBtn);
        }

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
        int totalCost = 0;
        
        // Use an atomic reference effectively to read data since player.getCapability isn't synchronous but we can assume it returns
        return player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).map(data -> {
            int cost = 0;
            for (Map.Entry<String, Integer> entry : pendingUpgrades.entrySet()) {
                IWeaponMasteryData.WeaponPath path = IWeaponMasteryData.WeaponPath.fromString(entry.getKey());
                if (path == null) continue;
                
                int currentLevel = data.getMasteryLevel(path);
                for (int i = 0; i < entry.getValue(); i++) {
                    cost += WeaponMasteryManager.getInstance().getSPCostForNextLevel(currentLevel + i);
                }
            }
            return cost;
        }).orElse(0);
    }

    private String getTierName(int level) {
        if (level < 5) return "Novice";
        if (level < 10) return "Apprentice";
        if (level < 15) return "Adept";
        if (level < 20) return "Expert";
        return "Master";
    }

    private String getTierColor(int level) {
        if (level < 5) return "§f"; // White
        if (level < 10) return "§a"; // Green
        if (level < 15) return "§9"; // Blue
        if (level < 20) return "§5"; // Purple
        return "§6"; // Gold
    }
}
