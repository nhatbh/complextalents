package com.complextalents.origin.client;

import com.complextalents.dev.SimpleUIFactory;
import com.complextalents.leveling.client.ClientLevelingData;
import com.complextalents.network.PacketHandler;
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.network.UpgradeOriginPacket;
import com.complextalents.origin.network.UpgradeOriginSkillPacket;
import com.complextalents.stats.ScaledStat;
import com.complextalents.skill.Skill;
import com.complextalents.skill.SkillRegistry;
import com.complextalents.skill.capability.IPlayerSkillData;
import com.complextalents.skill.client.ClientSkillData;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class OriginUpgradeUI {
    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath("complextalents", "origin_upgrade");

    public static void init() {
        SimpleUIFactory.register(UI_ID, (player, holder) -> new OriginUpgradeUI(player, holder).createUI());
    }

    private final Player player;
    private final IUIHolder holder;

    public OriginUpgradeUI(Player player, IUIHolder holder) {
        this.player = player;
        this.holder = holder;
    }

    public ModularUI createUI() {
        WidgetGroup root = new WidgetGroup();
        root.setSize(600, 360);
        // Premium Dark Background
        root.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND.copy().setColor(0xEE080808));

        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) {
            LabelWidget error = new LabelWidget();
            error.setSelfPosition(10, 10);
            error.setText("§cYou do not have an origin selected.");
            root.addWidget(error);
            return new ModularUI(root, holder, player);
        }

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        
        // Header
        LabelWidget title = new LabelWidget();
        title.setSelfPosition(15, 12);
        title.setText("§6§l" + origin.getDisplayName().getString().toUpperCase() + " §8| §7MASTERY");
        root.addWidget(title);

        LabelWidget spLabel = new LabelWidget();
        spLabel.setSelfPosition(480, 12);
        spLabel.setTextProvider(() -> "§7Skill Points: §e" + ClientLevelingData.getAvailableSkillPoints());
        root.addWidget(spLabel);

        // --- Integrated Layout (Name -> Description -> Scaling) ---

        // Left Area: Origin Progression
        WidgetGroup originArea = new WidgetGroup();
        originArea.setSelfPosition(20, 40);
        originArea.setSize(270, 165);
        
        LabelWidget originName = new LabelWidget();
        originName.setSelfPosition(0, 0);
        originName.setText("§6§l" + origin.getDisplayName().getString().toUpperCase());
        originArea.addWidget(originName);

        // Origin Description
        TextTextureWidget originDesc = new TextTextureWidget();
        originDesc.setSelfPosition(0, 12);
        originDesc.setSize(260, 45);
        originDesc.setText("§7" + origin.getDescription().getString());
        originArea.addWidget(originDesc);

        // Origin Scaling Table
        Map<String, ScaledStat> originStats = new HashMap<>(origin.getScaledStats());
        WidgetGroup originTable = createScalingTable(originStats, true);
        originTable.setSelfPosition(0, 60);
        originArea.addWidget(originTable);

        ButtonWidget upgOriginBtn = new ButtonWidget();
        upgOriginBtn.setSelfPosition(0, 140);
        upgOriginBtn.setSize(260, 20);
        upgOriginBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture(""));
        upgOriginBtn.setOnPressCallback(cd -> {
            if (ClientOriginData.getOriginLevel() < origin.getMaxLevel()) {
                PacketHandler.sendToServer(new UpgradeOriginPacket());
            }
        });
        originArea.addWidget(upgOriginBtn);
        
        LabelWidget upgOriginLabel = new LabelWidget();
        upgOriginLabel.setSelfPosition(10, 145);
        upgOriginLabel.setTextProvider(() -> {
            int lvl = ClientOriginData.getOriginLevel();
            if (lvl >= origin.getMaxLevel()) return "§8MAX RANK ATTAINED";
            return "§fUpgrade Origin §8(§e" + UpgradeOriginPacket.getCostForNextLevel(lvl) + " SP§8)";
        });
        originArea.addWidget(upgOriginLabel);
        root.addWidget(originArea);

        // Right Area: Skill Mastery
        WidgetGroup skillArea = new WidgetGroup();
        skillArea.setSelfPosition(310, 40);
        skillArea.setSize(270, 165);

        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                // Identity
                ImageWidget skillIcon = new ImageWidget();
                skillIcon.setSelfPosition(0, 0);
                skillIcon.setSize(18, 18);
                ResourceLocation iconLoc = skill.getIcon();
                if (iconLoc == null) iconLoc = ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/default_icon.png");
                skillIcon.setImage(new com.lowdragmc.lowdraglib.gui.texture.ResourceTexture(iconLoc));
                skillArea.addWidget(skillIcon);

                LabelWidget skillName = new LabelWidget();
                skillName.setSelfPosition(22, 2);
                skillName.setText("§d§l" + skill.getDisplayName().getString().toUpperCase());
                skillArea.addWidget(skillName);

                // Multi-line Description
                TextTextureWidget skillDesc = new TextTextureWidget();
                skillDesc.setSelfPosition(0, 22);
                skillDesc.setSize(260, 35);
                skillDesc.setText("§7" + skill.getDescription().getString());
                skillArea.addWidget(skillDesc);

                // Skill Scaling Table (Ensuring core stats are shown)
                Map<String, ScaledStat> skillStats = new HashMap<>();
                double[] cds = new double[5];
                double[] costs = new double[5];
                for (int i = 1; i <= 5; i++) {
                    cds[i-1] = skill.getActiveCooldown(i);
                    costs[i-1] = skill.getResourceCost(i);
                }
                if (cds[0] > 0 || cds[4] > 0) skillStats.put("Cooldown", new ScaledStat("Cooldown", cds));
                if (skill.getResourceType() != null) skillStats.put("Cost", new ScaledStat("Cost", costs));
                
                skillStats.putAll(skill.getScaledStats());

                WidgetGroup skillTable = createScalingTable(skillStats, false);
                skillTable.setSelfPosition(0, 60);
                skillArea.addWidget(skillTable);

                ButtonWidget upgSkillBtn = new ButtonWidget();
                upgSkillBtn.setSelfPosition(0, 140);
                upgSkillBtn.setSize(260, 20);
                upgSkillBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture(""));
                upgSkillBtn.setOnPressCallback(cd -> {
                    int lvl = getActiveSkillLevel();
                    if (lvl < skill.getMaxLevel()) {
                        PacketHandler.sendToServer(new UpgradeOriginSkillPacket());
                    }
                });
                skillArea.addWidget(upgSkillBtn);
                
                LabelWidget upgSkillLabel = new LabelWidget();
                upgSkillLabel.setSelfPosition(10, 145);
                upgSkillLabel.setTextProvider(() -> {
                    int lvl = getActiveSkillLevel();
                    if (lvl >= skill.getMaxLevel()) return "§8SKILL FULLY MASTERED";
                    return "§fEnhance Skill §8(§e" + UpgradeOriginSkillPacket.getCostForNextLevel(lvl) + " SP§8)";
                });
                skillArea.addWidget(upgSkillLabel);
            }
        }
        root.addWidget(skillArea);

        // Bottom Area: Custom Mechanics
        WidgetGroup bottomArea = new WidgetGroup();
        bottomArea.setSelfPosition(20, 210);
        bottomArea.setSize(560, 130);

        LabelWidget customHead = new LabelWidget();
        customHead.setSelfPosition(0, 0);
        customHead.setText("§e§lUNIQUE ARCHETYPE MECHANICS");
        bottomArea.addWidget(customHead);

        WidgetGroup customContents = origin.getCustomUpgradeUI(player);
        if (customContents != null) {
            customContents.setSelfPosition(0, 15);
            customContents.setSize(560, 110);
            customContents.setBackground((com.lowdragmc.lowdraglib.gui.texture.IGuiTexture) null); // Integrated feel
            bottomArea.addWidget(customContents);
        } else {
            LabelWidget noCustom = new LabelWidget();
            noCustom.setSelfPosition(10, 25);
            noCustom.setText("§8No unique mastery trackers for this origin.");
            bottomArea.addWidget(noCustom);
        }
        
        root.addWidget(bottomArea);

        return new ModularUI(root, holder, player);
    }

    private WidgetGroup createScalingTable(Map<String, ScaledStat> stats, boolean isOrigin) {
        WidgetGroup table = new WidgetGroup();
        table.setSize(270, 75);
        
        // Header levels
        for (int i = 1; i <= 5; i++) {
            final int lv = i;
            LabelWidget lvHead = new LabelWidget();
            lvHead.setSelfPosition(110 + (i - 1) * 32, 0);
            lvHead.setTextProvider(() -> {
                int current = isOrigin ? ClientOriginData.getOriginLevel() : getActiveSkillLevel();
                String color = (lv == current) ? "§e" : "§7";
                return color + "Lv" + lv;
            });
            table.addWidget(lvHead);
        }

        int row = 0;
        String[] priority = {"Cooldown", "Cost"};
        for (String key : priority) {
            if (stats.containsKey(key)) {
                addTableRow(table, stats.get(key), row++, isOrigin);
            }
        }

        for (var entry : stats.entrySet()) {
            if (row >= 4) break;
            if (entry.getKey().equals("Cooldown") || entry.getKey().equals("Cost")) continue;
            addTableRow(table, entry.getValue(), row++, isOrigin);
        }
        
        return table;
    }

    private void addTableRow(WidgetGroup table, ScaledStat stat, int row, boolean isOrigin) {
        LabelWidget rowName = new LabelWidget();
        rowName.setSelfPosition(0, 12 + row * 11);
        String label = stat.displayName().getString();
        if (label.length() > 14) label = label.substring(0, 12) + "..";
        rowName.setText("§8" + label);
        table.addWidget(rowName);

        double[] values = stat.values();
        for (int i = 1; i <= 5; i++) {
            final int lv = i;
            final double v = values[Math.min(i - 1, values.length - 1)];
            LabelWidget val = new LabelWidget();
            val.setSelfPosition(110 + (i - 1) * 32, 12 + row * 11);
            val.setTextProvider(() -> {
                int current = isOrigin ? ClientOriginData.getOriginLevel() : getActiveSkillLevel();
                String color = (lv == current) ? "§f" : "§7";
                return color + formatValue(v);
            });
            table.addWidget(val);
        }
    }

    private String formatValue(double v) {
        if (v == (long) v) return String.format("%d", (long) v);
        if (v < 0.1) return String.format("%.3f", v);
        if (v < 1.0) return String.format("%.2f", v);
        return String.format("%.1f", v);
    }

    private int getActiveSkillLevel() {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) return 0;
        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) return 0;
        ResourceLocation skillId = origin.getActiveSkillId();
        if (skillId == null) return 0;
        
        boolean assigned = false;
        for (int i = 0; i < IPlayerSkillData.SLOT_COUNT; i++) {
            if (skillId.equals(ClientSkillData.getSkillInSlot(i))) assigned = true;
        }
        return assigned ? ClientSkillData.getSkillLevel(skillId) : 0;
    }
}
