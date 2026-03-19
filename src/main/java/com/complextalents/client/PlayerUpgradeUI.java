package com.complextalents.client;

import com.complextalents.dev.SimpleUIFactory;
import com.complextalents.leveling.client.ClientLevelingData;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.FinalizePlayerUpgradesPacket;

// Origin & Skill Imports
import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.skill.Skill;
import com.complextalents.skill.SkillRegistry;
import com.complextalents.skill.capability.IPlayerSkillData;
import com.complextalents.skill.client.ClientSkillData;
import com.complextalents.stats.ScaledStat;

// Spell Mastery Imports
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.client.ClientSpellMasteryData;
import com.complextalents.spellmastery.network.FinalizeGrimoirePacket;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;

// Stats Imports
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.stats.ClassCostMatrix;
import com.complextalents.stats.StatType;
import com.complextalents.stats.capability.GeneralStatsDataProvider;

// Weapon Mastery Imports
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData;
import com.complextalents.weaponmastery.capability.WeaponMasteryDataProvider;

// UI Components
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerUpgradeUI {
    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath("complextalents", "player_upgrade");

    private final Player player;
    private final IUIHolder holder;

    // --- Global State ---
    private LabelWidget spLabel;
    private LabelWidget consumedLabel;
    private ButtonWidget confirmBtn;

    // --- Stats State ---
    private final Map<String, Integer> pendingStatsUpgrades = new HashMap<>();
    private WidgetGroup statListContainer;
    private WidgetGroup accumulatedPanel;

    // --- Weapon Mastery State ---
    private final Map<String, Integer> pendingWeaponUpgrades = new HashMap<>();
    private WidgetGroup pathListContainer;

    // --- Spell Mastery State ---
    private record SpellTierEntry(AbstractSpell spell, int level, SpellRarity rarity) {
        public String getUniqueId() {
            return spell.getSpellResource().toString() + "@" + level;
        }
    }
    private final List<SpellTierEntry> allSpells;
    private final Set<String> pendingSpells = new HashSet<>();
    private WidgetGroup spellListContainer;
    private String selectedSchool = "";
    private String selectedTier = "Common";
    private boolean rarityAscending = false;

    // --- Origin & Skill State ---
    private int pendingOriginUpgrades = 0;
    private int pendingSkillUpgrades = 0;
    private WidgetGroup originSyncGroup;

    public PlayerUpgradeUI(Player player, IUIHolder holder) {
        this.player = player;
        this.holder = holder;

        // Initialize Spell Mastery data
        List<SpellTierEntry> entries = new ArrayList<>();
        for (AbstractSpell spell : SpellRegistry.REGISTRY.get().getValues()) {
            if (spell == null || spell == SpellRegistry.none()) continue;
            Set<SpellRarity> seenRarities = new HashSet<>();
            for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
                SpellRarity r = spell.getRarity(lvl);
                if (seenRarities.add(r)) {
                    entries.add(new SpellTierEntry(spell, lvl, r));
                }
            }
        }
        this.allSpells = entries.stream()
                .sorted(Comparator.<SpellTierEntry, Integer>comparing(e -> e.rarity.getValue()).reversed()
                        .thenComparing(e -> e.spell.getSchoolType().getDisplayName().getString())
                        .thenComparing(e -> e.spell.getDisplayName(null).getString()))
                .collect(Collectors.toList());
    }

    public static void init() {
        SimpleUIFactory.register(UI_ID, (player, holder) -> new PlayerUpgradeUI(player, holder).createUI());
    }

    public ModularUI createUI() {
        WidgetGroup root = new WidgetGroup();
        root.setSize(520, 320);
        root.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);

        // Header
        LabelWidget title = new LabelWidget();
        title.setSelfPosition(10, 10);
        title.setText("§lCharacter Progression");
        root.addWidget(title);

        spLabel = new LabelWidget();
        spLabel.setSelfPosition(340, 10);
        spLabel.setSize(160, 12);
        spLabel.setTextProvider(() -> "SP: §e" + (ClientLevelingData.getAvailableSkillPoints() - calculateTotalPendingCost()));
        root.addWidget(spLabel);

        // Tab Container
        TabContainer tabs = new TabContainer(10, 30, 500, 240);
        
        // Build Tabs
        WidgetGroup statsTab = buildStatsTab();
        WidgetGroup originTab = buildOriginTab();
        WidgetGroup spellsTab = buildSpellsTab();
        WidgetGroup weaponsTab = buildWeaponsTab();

        tabs.addTab(createTabButton("Stats", 0), statsTab);
        tabs.addTab(createTabButton("Origin & Skill", 1), originTab);
        tabs.addTab(createTabButton("Spells", 2), spellsTab);
        tabs.addTab(createTabButton("Weapons", 3), weaponsTab);
        
        root.addWidget(tabs);

        // Footer
        WidgetGroup footer = new WidgetGroup();
        footer.setSelfPosition(10, 280);
        footer.setSize(500, 25);

        consumedLabel = new LabelWidget();
        consumedLabel.setSelfPosition(0, 5);
        consumedLabel.setTextProvider(() -> "To Consume: §c" + calculateTotalPendingCost() + " SP");
        footer.addWidget(consumedLabel);

        confirmBtn = new ButtonWidget();
        confirmBtn.setSelfPosition(390, 0);
        confirmBtn.setSize(110, 20);
        confirmBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444), new TextTexture("Finalize"));
        confirmBtn.setOnPressCallback(clickData -> {
            if (calculateTotalPendingCost() > 0) {
                finalizeUpgrades();
            }
        });
        footer.addWidget(confirmBtn);

        root.addWidget(footer);

        // Initial Data Populate
        updateStatsList();
        updateSpellList();
        updatePathList();
        updateFooter();

        return new ModularUI(root, holder, player);
    }

    private TabButton createTabButton(String name, int index) {
        TabButton btn = new TabButton(-32 + 4, index * 28, 32, 28);
        float yStart = (index % 3) * (1f / 3);
        btn.setTexture(
            new GuiTextureGroup(TabContainer.TABS_LEFT.getSubTexture(0, yStart, 0.5f, 1f / 3), new TextTexture(name.substring(0, 1))),
            new GuiTextureGroup(TabContainer.TABS_LEFT.getSubTexture(0.5f, yStart, 0.5f, 1f / 3), new TextTexture(name.substring(0, 1)))
        );
        btn.setHoverTooltips(name);
        return btn;
    }

    private int calculateTotalPendingCost() {
        return calculateStatsCost() + calculateWeaponCost() + calculateSpellCost() + calculateOriginCost();
    }

    private void updateFooter() {
        int cost = calculateTotalPendingCost();
        consumedLabel.setVisible(cost > 0);

        if (cost == 0) {
            confirmBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444), new TextTexture("Finalize"));
        } else {
            confirmBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF22AA22), new TextTexture("§fFinalize"));
        }
    }

    private void finalizeUpgrades() {
        List<FinalizeGrimoirePacket.MasteryUpgrade> spellUpgrades = new ArrayList<>();
        List<FinalizeGrimoirePacket.SpellPurchase> spellPurchasesList = new ArrayList<>();
        
        for (String entry : pendingSpells) {
            if (entry.startsWith("S:")) {
                String[] parts = entry.substring(2).split("@");
                spellPurchasesList.add(new FinalizeGrimoirePacket.SpellPurchase(ResourceLocation.parse(parts[0]), Integer.parseInt(parts[1])));
            } else if (entry.startsWith("M:")) {
                String[] parts = entry.substring(2).split("@");
                spellUpgrades.add(new FinalizeGrimoirePacket.MasteryUpgrade(ResourceLocation.parse(parts[0]), Integer.parseInt(parts[1])));
            }
        }

        PacketHandler.sendToServer(new FinalizePlayerUpgradesPacket(
            pendingStatsUpgrades,
            pendingWeaponUpgrades,
            spellUpgrades,
            spellPurchasesList,
            pendingOriginUpgrades,
            pendingSkillUpgrades
        ));

        // Clear local cache gracefully
        pendingStatsUpgrades.clear();
        pendingWeaponUpgrades.clear();
        pendingSpells.clear();
        pendingOriginUpgrades = 0;
        pendingSkillUpgrades = 0;
        
        player.closeContainer();
    }

    // ==========================================
    // STATS TAB
    // ==========================================

    private WidgetGroup buildStatsTab() {
        WidgetGroup tab = new WidgetGroup(0, 0, 460, 240);
        
        DraggableScrollableWidgetGroup scrollable = new DraggableScrollableWidgetGroup();
        scrollable.setSelfPosition(5, 5);
        scrollable.setSize(305, 230);
        
        statListContainer = new WidgetGroup();
        scrollable.addWidget(statListContainer);
        
        accumulatedPanel = new WidgetGroup();
        accumulatedPanel.setSelfPosition(315, 5);
        accumulatedPanel.setSize(180, 230);
        accumulatedPanel.setBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF111111));
        
        tab.addWidget(scrollable);
        tab.addWidget(accumulatedPanel);
        
        return tab;
    }

    private int calculateStatsCost() {
        ResourceLocation originId = getActiveOrigin();
        int totalCost = 0;
        for (Map.Entry<String, Integer> entry : pendingStatsUpgrades.entrySet()) {
            try {
                StatType type = StatType.valueOf(entry.getKey());
                int amount = entry.getValue();
                totalCost += ClassCostMatrix.getCost(originId, type) * amount;
            } catch (IllegalArgumentException ignored) {}
        }
        return totalCost;
    }

    private void updateStatsList() {
        if (statListContainer == null) return;
        statListContainer.clearAllWidgets();
        accumulatedPanel.clearAllWidgets();

        LabelWidget accTitle = new LabelWidget();
        accTitle.setSelfPosition(5, 5);
        accTitle.setText("§lAccumulated Stats");
        accumulatedPanel.addWidget(accTitle);

        player.getCapability(GeneralStatsDataProvider.STATS_DATA).ifPresent(data -> {
            int xOffset = 5;
            int yOffset = 5;
            int col = 0;
            int accY = 20;

            for (StatType type : StatType.values()) {
                WidgetGroup entry = createStatEntry(type, data);
                if (entry != null) {
                    entry.setSelfPosition(xOffset, yOffset);
                    statListContainer.addWidget(entry);
                    
                    col++;
                    if (col >= 2) {
                        col = 0;
                        xOffset = 5;
                        yOffset += 35;
                    } else {
                        xOffset += 150;
                    }
                }

                int rank = data.getStatRank(type);
                int pending = pendingStatsUpgrades.getOrDefault(type.name(), 0);
                int totalRank = rank + pending;
                if (totalRank > 0) {
                    double val = totalRank * type.getYieldPerRank();
                    LabelWidget l = new LabelWidget();
                    l.setSelfPosition(5, accY);
                    
                    String formattedVal = String.format("%.2f", val).replace(".00", "");
                    String valText = "§f+" + formattedVal;
                    if (pending > 0) {
                        valText = "§a+" + formattedVal;
                    }
                    
                    l.setText("§7" + type.getDisplayName().replace("%", "%%") + ": " + valText);
                    accumulatedPanel.addWidget(l);
                    accY += 15;
                }
            }
            if (col > 0) yOffset += 65;
            statListContainer.setSize(300, Math.max(240, yOffset));
        });
    }

    private WidgetGroup createStatEntry(StatType type, com.complextalents.stats.capability.IGeneralStatsData data) {
        WidgetGroup widget = new WidgetGroup();
        widget.setSize(145, 30);
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON);

        int realCurrentRank = data.getStatRank(type);
        int pendingPurchases = pendingStatsUpgrades.getOrDefault(type.name(), 0);
        
        ResourceLocation originId = getActiveOrigin();
        int costPerRank = ClassCostMatrix.getCost(originId, type);

        LabelWidget nameLabel = new LabelWidget();
        nameLabel.setSelfPosition(3, 3);
        String nameText = type.getDisplayName().replace("%", "%%");
        if (nameText.length() > 10) nameText = nameText.substring(0, 9) + ".";
        
        String rankStr = pendingPurchases > 0 ? "§7(L" + realCurrentRank + "§a+" + pendingPurchases + "§7)" : "§7(L" + realCurrentRank + ")";
        nameLabel.setText("§l" + nameText + " " + rankStr);
        widget.addWidget(nameLabel);

        LabelWidget costLabel = new LabelWidget();
        costLabel.setSelfPosition(3, 15);
        costLabel.setText("§e" + costPerRank + " SP/R");
        widget.addWidget(costLabel);

        if (pendingPurchases > 0) {
            ButtonWidget cancelBtn = new ButtonWidget();
            cancelBtn.setSelfPosition(92, 3);
            cancelBtn.setSize(24, 24);
            cancelBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFFCC4444), new TextTexture("§c-"));
            cancelBtn.setOnPressCallback(cd -> {
                pendingStatsUpgrades.put(type.name(), pendingPurchases - 1);
                if (pendingStatsUpgrades.get(type.name()) <= 0) pendingStatsUpgrades.remove(type.name());
                updateStatsList();
                updateFooter();
            });
            widget.addWidget(cancelBtn);
        }

        ButtonWidget upgradeBtn = new ButtonWidget();
        upgradeBtn.setSelfPosition(118, 3);
        upgradeBtn.setSize(24, 24);

        long availableSP = ClientLevelingData.getAvailableSkillPoints() - calculateTotalPendingCost();

        if (availableSP >= costPerRank) {
            upgradeBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("§a+"));
            upgradeBtn.setOnPressCallback(cd -> {
                pendingStatsUpgrades.put(type.name(), pendingPurchases + 1);
                updateStatsList();
                updateFooter();
            });
        } else {
            upgradeBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§c+"));
            List<String> tooltip = new ArrayList<>();
            tooltip.add("§cRequirements not met:");
            tooltip.add("§7- Insufficient SP (Need " + costPerRank + ")");
            upgradeBtn.setHoverTooltips(tooltip.toArray(new String[0]));
        }
        widget.addWidget(upgradeBtn);

        return widget;
    }

    private ResourceLocation getActiveOrigin() {
        return player.getCapability(OriginDataProvider.ORIGIN_DATA)
                .map(data -> data.getActiveOrigin())
                .orElse(null);
    }

    // ==========================================
    // ORIGIN & SKILL TAB
    // ==========================================

    private WidgetGroup buildOriginTab() {
        WidgetGroup tab = new WidgetGroup(0, 0, 500, 240);
        originSyncGroup = tab;
        refreshOriginContent();
        return tab;
    }

    private int calculateOriginCost() {
        int cost = 0;
        int currentLevel = ClientOriginData.getOriginLevel();
        int targetLevel = currentLevel;
        for (int i = 0; i < pendingOriginUpgrades; i++) {
            if (targetLevel >= 5) break;
            cost += com.complextalents.origin.network.UpgradeOriginPacket.getCostForNextLevel(targetLevel);
            targetLevel++;
        }
        
        int currentSkill = getActiveSkillLevel();
        int targetSkill = currentSkill;
        for (int i = 0; i < pendingSkillUpgrades; i++) {
            if (targetSkill >= 5) break;
            cost += com.complextalents.origin.network.UpgradeOriginSkillPacket.getCostForNextLevel(targetSkill);
            targetSkill++;
        }
        
        return cost;
    }

    private void refreshOriginContent() {
        if (originSyncGroup == null) return;
        originSyncGroup.clearAllWidgets();
        
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) {
            LabelWidget error = new LabelWidget();
            error.setSelfPosition(10, 10);
            error.setText("§cYou do not have an origin selected.");
            originSyncGroup.addWidget(error);
            return;
        }

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);

        WidgetGroup originArea = new WidgetGroup();
        originArea.setSelfPosition(10, 10);
        originArea.setSize(230, 180);
        
        LabelWidget originName = new LabelWidget();
        originName.setSelfPosition(0, 0);
        originName.setText("§6§l" + origin.getDisplayName().getString().toUpperCase());
        originArea.addWidget(originName);

        TextTextureWidget originDesc = new TextTextureWidget();
        originDesc.setSelfPosition(0, 12);
        originDesc.setSize(220, 45);
        originDesc.setText("§7" + origin.getDescription().getString());
        originArea.addWidget(originDesc);

        Map<String, ScaledStat> originStats = new HashMap<>(origin.getScaledStats());
        WidgetGroup originTable = createScalingTable(originStats, true);
        originTable.setSelfPosition(0, 60);
        originArea.addWidget(originTable);

        ButtonWidget upgOriginBtn = new ButtonWidget();
        upgOriginBtn.setSelfPosition(0, 140);
        upgOriginBtn.setSize(220, 20);
        
        int originTarget = ClientOriginData.getOriginLevel() + pendingOriginUpgrades;
        int nextOriginCost = com.complextalents.origin.network.UpgradeOriginPacket.getCostForNextLevel(originTarget);
        
        if (originTarget >= origin.getMaxLevel()) {
            upgOriginBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§8MAX RANK ATTAINED"));
        } else {
            long availableSP = ClientLevelingData.getAvailableSkillPoints() - calculateTotalPendingCost();
            if (availableSP >= nextOriginCost) {
                upgOriginBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("§aAdd Rank (Cost: " + nextOriginCost + ")"));
                upgOriginBtn.setOnPressCallback(cd -> {
                    pendingOriginUpgrades++;
                    refreshOriginContent();
                    updateFooter();
                });
            } else {
                upgOriginBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§cInsufficient SP"));
            }
        }
        originArea.addWidget(upgOriginBtn);
        
        if (pendingOriginUpgrades > 0) {
            ButtonWidget cancelOrigin = new ButtonWidget();
            cancelOrigin.setSelfPosition(0, 165);
            cancelOrigin.setSize(220, 16);
            cancelOrigin.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFFCC4444), new TextTexture("§cCancel Upgrade(s)"));
            cancelOrigin.setOnPressCallback(cd -> {
                pendingOriginUpgrades--;
                refreshOriginContent();
                updateFooter();
            });
            originArea.addWidget(cancelOrigin);
        }
        
        originSyncGroup.addWidget(originArea);

        // Right Area: Skill Mastery
        WidgetGroup skillArea = new WidgetGroup();
        skillArea.setSelfPosition(260, 10);
        skillArea.setSize(230, 180);

        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                ImageWidget skillIcon = new ImageWidget();
                skillIcon.setSelfPosition(0, 0);
                skillIcon.setSize(18, 18);
                ResourceLocation iconLoc = skill.getIcon() != null ? skill.getIcon() : ResourceLocation.fromNamespaceAndPath("complextalents", "textures/skill/default_icon.png");
                skillIcon.setImage(new com.lowdragmc.lowdraglib.gui.texture.ResourceTexture(iconLoc));
                skillArea.addWidget(skillIcon);

                LabelWidget skillName = new LabelWidget();
                skillName.setSelfPosition(22, 2);
                skillName.setText("§d§l" + skill.getDisplayName().getString().toUpperCase());
                skillArea.addWidget(skillName);

                TextTextureWidget skillDesc = new TextTextureWidget();
                skillDesc.setSelfPosition(0, 22);
                skillDesc.setSize(220, 35);
                skillDesc.setText("§7" + skill.getDescription().getString());
                skillArea.addWidget(skillDesc);

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
                upgSkillBtn.setSize(220, 20);
                
                int skillTarget = getActiveSkillLevel() + pendingSkillUpgrades;
                int nextSkillCost = com.complextalents.origin.network.UpgradeOriginSkillPacket.getCostForNextLevel(skillTarget);

                if (skillTarget >= skill.getMaxLevel()) {
                    upgSkillBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§8SKILL FULLY MASTERED"));
                } else {
                    long availableSP = ClientLevelingData.getAvailableSkillPoints() - calculateTotalPendingCost();
                    if (availableSP >= nextSkillCost) {
                        upgSkillBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("§aAdd Rank (Cost: " + nextSkillCost + ")"));
                        upgSkillBtn.setOnPressCallback(cd -> {
                            pendingSkillUpgrades++;
                            refreshOriginContent();
                            updateFooter();
                        });
                    } else {
                        upgSkillBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§cInsufficient SP"));
                    }
                }
                skillArea.addWidget(upgSkillBtn);
                
                if (pendingSkillUpgrades > 0) {
                    ButtonWidget cancelSkill = new ButtonWidget();
                    cancelSkill.setSelfPosition(0, 165);
                    cancelSkill.setSize(220, 16);
                    cancelSkill.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFFCC4444), new TextTexture("§cCancel Upgrade(s)"));
                    cancelSkill.setOnPressCallback(cd -> {
                        pendingSkillUpgrades--;
                        refreshOriginContent();
                        updateFooter();
                    });
                    skillArea.addWidget(cancelSkill);
                }
            }
        }
        originSyncGroup.addWidget(skillArea);
    }

    private WidgetGroup createScalingTable(Map<String, ScaledStat> stats, boolean isOrigin) {
        WidgetGroup table = new WidgetGroup();
        table.setSize(230, 75);
        
        for (int i = 1; i <= 5; i++) {
            final int lv = i;
            LabelWidget lvHead = new LabelWidget();
            lvHead.setSelfPosition(80 + (i - 1) * 28, 0); // Squished for smaller area
            lvHead.setTextProvider(() -> {
                int current = isOrigin ? ClientOriginData.getOriginLevel() + pendingOriginUpgrades : getActiveSkillLevel() + pendingSkillUpgrades;
                String color = (lv == current) ? "§e" : "§7";
                return color + "L" + lv;
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
        if (label.length() > 12) label = label.substring(0, 10) + "..";
        rowName.setText("§8" + label);
        table.addWidget(rowName);

        double[] values = stat.values();
        for (int i = 1; i <= 5; i++) {
            final int lv = i;
            final double v = values[Math.min(i - 1, values.length - 1)];
            LabelWidget val = new LabelWidget();
            val.setSelfPosition(80 + (i - 1) * 28, 12 + row * 11);
            val.setTextProvider(() -> {
                int current = isOrigin ? ClientOriginData.getOriginLevel() + pendingOriginUpgrades : getActiveSkillLevel() + pendingSkillUpgrades;
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

    // ==========================================
    // SPELLS TAB
    // ==========================================

    private WidgetGroup buildSpellsTab() {
        WidgetGroup tab = new WidgetGroup(0, 0, 500, 240);
        
        WidgetGroup filters = new WidgetGroup();
        filters.setSelfPosition(10, 5);
        filters.setSize(480, 25);

        SelectorWidget schoolSelector = new SelectorWidget();
        schoolSelector.setSelfPosition(0, 0);
        schoolSelector.setSize(120, 18);
        List<String> schools = new ArrayList<>();
        io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValues().forEach(school -> schools.add(school.getDisplayName().getString()));
        if (!schools.isEmpty() && selectedSchool.isEmpty()) {
            this.selectedSchool = schools.get(0);
        }
        schoolSelector.setCandidates(schools);
        schoolSelector.setValue(selectedSchool);
        schoolSelector.setOnChanged(val -> { this.selectedSchool = val; updateSpellList(); });
        schoolSelector.setButtonBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444));
        filters.addWidget(schoolSelector);

        SelectorWidget tierSelector = new SelectorWidget();
        tierSelector.setSelfPosition(130, 0);
        tierSelector.setSize(90, 18);
        tierSelector.setCandidates(List.of("Common", "Uncommon", "Rare", "Epic", "Legendary"));
        tierSelector.setValue("Common");
        tierSelector.setOnChanged(val -> { this.selectedTier = val; updateSpellList(); });
        tierSelector.setButtonBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444));
        filters.addWidget(tierSelector);

        ButtonWidget sortBtn = new ButtonWidget();
        sortBtn.setSelfPosition(230, 0);
        sortBtn.setSize(90, 18);
        sortBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444), new TextTexture(rarityAscending ? "Tier: Asc" : "Tier: Desc"));
        sortBtn.setOnPressCallback(cd -> {
            this.rarityAscending = !this.rarityAscending;
            sortBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444), new TextTexture(rarityAscending ? "Tier: Asc" : "Tier: Desc"));
            updateSpellList();
        });
        filters.addWidget(sortBtn);

        DraggableScrollableWidgetGroup scrollable = new DraggableScrollableWidgetGroup();
        scrollable.setSelfPosition(10, 30);
        scrollable.setSize(480, 210);

        spellListContainer = new WidgetGroup();
        scrollable.addWidget(spellListContainer);
        tab.addWidget(scrollable);
        tab.addWidget(filters);

        return tab;
    }

    private int calculateSpellCost() {
        int total = 0;
        for (String entryStr : pendingSpells) {
            if (entryStr.startsWith("S:")) {
                String[] parts = entryStr.substring(2).split("@");
                AbstractSpell spell = SpellRegistry.getSpell(ResourceLocation.parse(parts[0]));
                if (spell != null) {
                    total += SpellMasteryManager.getSpellCost(spell.getRarity(Integer.parseInt(parts[1])));
                }
            } else if (entryStr.startsWith("M:")) {
                String[] parts = entryStr.substring(2).split("@");
                total += SpellMasteryManager.getMasteryBuyUpCost(Integer.parseInt(parts[1]));
            }
        }
        return total;
    }

    private void updateSpellList() {
        if (spellListContainer == null) return;
        spellListContainer.clearAllWidgets();

        List<SpellTierEntry> filtered = allSpells.stream()
                .filter(e -> e.spell.getSchoolType().getDisplayName().getString().equals(selectedSchool))
                .filter(e -> e.rarity.name().equalsIgnoreCase(selectedTier))
                .sorted((e1, e2) -> {
                    int r1 = e1.rarity.getValue();
                    int r2 = e2.rarity.getValue();
                    int comparison = rarityAscending ? Integer.compare(r1, r2) : Integer.compare(r2, r1);
                    if (comparison != 0) return comparison;
                    int schoolComp = e1.spell.getSchoolType().getDisplayName().getString().compareTo(e2.spell.getSchoolType().getDisplayName().getString());
                    if (schoolComp != 0) return schoolComp;
                    return e1.spell.getDisplayName(null).getString().compareTo(e2.spell.getDisplayName(null).getString());
                })
                .collect(Collectors.toList());

        int xOffset = 0;
        int yOffset = 0;
        int col = 0;
        for (SpellTierEntry entry : filtered) {
            WidgetGroup widget = createSpellEntry(entry);
            widget.setSelfPosition(xOffset, yOffset);
            spellListContainer.addWidget(widget);
            col++;
            if (col >= 3) {
                col = 0;
                xOffset = 0;
                yOffset += 50;
            } else {
                xOffset += 160;
            }
        }

        io.redspace.ironsspellbooks.api.spells.SchoolType school = io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValues().stream()
                .filter(s -> s.getDisplayName().getString().equals(selectedSchool)).findFirst().orElse(null);

        if (school != null) {
            int currentMastery = ClientSpellMasteryData.getMasteryLevel(school.getId());
            SpellRarity targetRarity = SpellRarity.valueOf(selectedTier.toUpperCase());
            int targetValue = targetRarity.getValue();

            if (currentMastery < targetValue && targetValue == currentMastery + 1) {
                int cost = com.complextalents.spellmastery.SpellMasteryManager.getMasteryBuyUpCost(targetValue);
                if (cost < 999) {
                    WidgetGroup buyWidget = createMasteryBuyEntry(school, targetRarity, cost);
                    buyWidget.setSelfPosition(0, yOffset + (col > 0 ? 50 : 0));
                    spellListContainer.addWidget(buyWidget);
                    yOffset += 50;
                }
            }
        }

        int totalRows = (int) Math.ceil(filtered.size() / 3.0);
        spellListContainer.setSize(480, Math.max(210, totalRows * 50 + 50));
    }

    private WidgetGroup createSpellEntry(SpellTierEntry entry) {
        AbstractSpell spell = entry.spell;
        int level = entry.level;
        SpellRarity rarity = entry.rarity;

        WidgetGroup widget = new WidgetGroup();
        widget.setSize(155, 45);
        boolean learned = ClientSpellMasteryData.isSpellLearned(spell.getSpellResource(), level);
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON);

        ImageWidget icon = new ImageWidget();
        icon.setSelfPosition(5, 5);
        icon.setSize(20, 20);
        icon.setImage(new ResourceTexture(spell.getSpellIconResource()));
        widget.addWidget(icon);

        LabelWidget name = new LabelWidget();
        name.setSelfPosition(30, 5);
        String nameText = rarity.getChatFormatting() + spell.getDisplayName(null).getString();
        if (learned) nameText = "§8" + ChatFormatting.stripFormatting(nameText);
        
        if (nameText.length() > 20) nameText = nameText.substring(0, 18) + "..";
        name.setText(nameText);

        LabelWidget info = new LabelWidget();
        info.setSelfPosition(30, 15);
        int cost = SpellMasteryManager.getSpellCost(rarity);
        String schoolName = spell.getSchoolType().getDisplayName().getString();
        String infoText = "§7" + schoolName + " | §e" + cost + "SP";
        if (learned) infoText = "§8" + ChatFormatting.stripFormatting(infoText);
        info.setText(infoText);
        widget.addWidget(name);
        widget.addWidget(info);

        ButtonWidget learnBtn = new ButtonWidget();
        learnBtn.setSelfPosition(5, 26);
        learnBtn.setSize(145, 14);

        if (learned) {
            learnBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§7Learned"));
        } else {
            String pendingId = "S:" + entry.getUniqueId();
            boolean isPending = pendingSpells.contains(pendingId);

            if (isPending) {
                learnBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("Selected"));
                learnBtn.setOnPressCallback(cd -> {
                    pendingSpells.remove(pendingId);
                    updateSpellList();
                    updateFooter();
                });
            } else {
                int currentSP = ClientLevelingData.getAvailableSkillPoints();
                int pendingCost = calculateTotalPendingCost(); // Global
                
                int effectiveMastery = ClientSpellMasteryData.getMasteryLevel(spell.getSchoolType().getId());
                for (String p : pendingSpells) {
                    if (p.startsWith("M:")) {
                        String[] parts = p.substring(2).split("@");
                        if (ResourceLocation.parse(parts[0]).equals(spell.getSchoolType().getId())) {
                            effectiveMastery = Math.max(effectiveMastery, Integer.parseInt(parts[1]));
                        }
                    }
                }
                
                int requiredMastery = rarity.getValue();
                boolean canSelect = (currentSP - pendingCost) >= cost && effectiveMastery >= requiredMastery;

                if (!canSelect) {
                    learnBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§cSelect"));
                    List<String> tooltip = new ArrayList<>();
                    tooltip.add("§cRequirements not met:");
                    if ((currentSP - pendingCost) < cost) tooltip.add("§7- Need §e" + cost + " SP");
                    if (effectiveMastery < requiredMastery) tooltip.add("§7- Need Mastery Level");
                    learnBtn.setHoverTooltips(tooltip.toArray(new String[0]));
                } else {
                    learnBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("Select"));
                    learnBtn.setOnPressCallback(cd -> {
                        pendingSpells.add(pendingId);
                        updateSpellList();
                        updateFooter();
                    });
                }
            }
        }
        widget.addWidget(learnBtn);
        return widget;
    }

    private WidgetGroup createMasteryBuyEntry(io.redspace.ironsspellbooks.api.spells.SchoolType school, SpellRarity targetRarity, int cost) {
        WidgetGroup widget = new WidgetGroup();
        widget.setSize(315, 45);
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444466));

        LabelWidget title = new LabelWidget();
        title.setSelfPosition(10, 5);
        title.setText("§bUnlock " + targetRarity.getDisplayName().getString() + " Mastery");
        widget.addWidget(title);

        LabelWidget desc = new LabelWidget();
        desc.setSelfPosition(10, 15);
        desc.setText("§7Lump sum: §e" + cost + " SP §7| Required to learn " + targetRarity.getDisplayName().getString() + " spells");
        widget.addWidget(desc);

        ButtonWidget buyBtn = new ButtonWidget();
        buyBtn.setSelfPosition(10, 26);
        buyBtn.setSize(295, 14);
        
        String pendingId = "M:" + school.getId().toString() + "@" + targetRarity.getValue();
        boolean isPending = pendingSpells.contains(pendingId);
        
        if (isPending) {
            buyBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("Selected"));
            buyBtn.setOnPressCallback(cd -> {
                pendingSpells.remove(pendingId);
                updateSpellList();
                updateFooter();
            });
        } else {
            int availableSP = ClientLevelingData.getAvailableSkillPoints();
            int pendingCost = calculateTotalPendingCost();
            if ((availableSP - pendingCost) >= cost) {
                buyBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("Add to Selection"));
                buyBtn.setOnPressCallback(cd -> {
                    pendingSpells.add(pendingId);
                    updateSpellList();
                    updateFooter();
                });
            } else {
                buyBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§cInsufficient SP"));
            }
        }
        widget.addWidget(buyBtn);
        return widget;
    }

    // ==========================================
    // WEAPONS TAB
    // ==========================================

    private WidgetGroup buildWeaponsTab() {
        WidgetGroup tab = new WidgetGroup(0, 0, 500, 240);
        
        DraggableScrollableWidgetGroup scrollable = new DraggableScrollableWidgetGroup();
        scrollable.setSelfPosition(10, 10);
        scrollable.setSize(480, 230);
        
        pathListContainer = new WidgetGroup();
        scrollable.addWidget(pathListContainer);
        tab.addWidget(scrollable);
        
        return tab;
    }

    private int calculateWeaponCost() {
        return player.getCapability(WeaponMasteryDataProvider.WEAPON_MASTERY_DATA).map(data -> {
            int cost = 0;
            for (Map.Entry<String, Integer> entry : pendingWeaponUpgrades.entrySet()) {
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

    private void updatePathList() {
        if (pathListContainer == null) return;
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
                    if (col >= 3) {
                        col = 0;
                        xOffset = 0;
                        yOffset += 75;
                    } else {
                        xOffset += 160;
                    }
                }
            }
            if (col > 0) yOffset += 75;
            pathListContainer.setSize(480, Math.max(230, yOffset));
        });
    }

    private WidgetGroup createPathEntry(IWeaponMasteryData.WeaponPath path, IWeaponMasteryData data) {
        WidgetGroup widget = new WidgetGroup();
        widget.setSize(155, 70); 
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON);

        int realCurrentLevel = data.getMasteryLevel(path);
        int pendingPurchases = pendingWeaponUpgrades.getOrDefault(path.name(), 0);
        int currentLevel = realCurrentLevel + pendingPurchases;
        
        double accumulated = data.getAccumulatedDamage(path);
        double requiredDamage = currentLevel < 25 ? WeaponMasteryManager.getInstance().getDamageRequiredForNextLevel(currentLevel) : 0;
        int nextCost = currentLevel < 25 ? WeaponMasteryManager.getInstance().getSPCostForNextLevel(currentLevel) : 0;

        String tierName = getTierName(currentLevel);
        String tierColor = getTierColor(currentLevel);

        LabelWidget nameLabel = new LabelWidget();
        nameLabel.setSelfPosition(5, 5);
        nameLabel.setText("§l" + path.name());
        widget.addWidget(nameLabel);

        LabelWidget tierLabel = new LabelWidget();
        tierLabel.setSelfPosition(5, 15);
        if (pendingPurchases > 0) {
            tierLabel.setText(tierColor + tierName + " §7(L." + currentLevel + ") §a(+" + pendingPurchases + ")");
        } else {
            tierLabel.setText(tierColor + tierName + " §7(L." + currentLevel + "/25)");
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
                controlBtn.setOnPressCallback(cd -> {
                    pendingWeaponUpgrades.put(path.name(), pendingPurchases - 1);
                    if (pendingWeaponUpgrades.get(path.name()) <= 0) pendingWeaponUpgrades.remove(path.name());
                    updatePathList();
                    updateFooter();
                });
            } else {
                controlBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§7Maxed"));
            }
            widget.addWidget(controlBtn);
        } else {
            double pct = (accumulated / requiredDamage);
            if (pct > 1.0) pct = 1.0;

            WidgetGroup progressBarBg = new WidgetGroup();
            progressBarBg.setSelfPosition(5, 28);
            progressBarBg.setSize(145, 10);
            progressBarBg.setBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF222222));
            
            WidgetGroup progressBarFill = new WidgetGroup();
            progressBarFill.setSelfPosition(0, 0);
            progressBarFill.setSize((int) (145 * pct), 10);
            progressBarFill.setBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF22AA22)); 
            progressBarBg.addWidget(progressBarFill);
            
            LabelWidget pctLabel = new LabelWidget();
            pctLabel.setSelfPosition(2, 1);
            pctLabel.setText("§f" + (int)(pct * 100) + "%%");
            progressBarBg.addWidget(pctLabel);
            
            widget.addWidget(progressBarBg);

            LabelWidget costLabel = new LabelWidget();
            costLabel.setSelfPosition(5, 40);
            costLabel.setText("Cost: §e" + nextCost + " SP");
            widget.addWidget(costLabel);

            if (pendingPurchases > 0) {
                ButtonWidget cancelBtn = new ButtonWidget();
                cancelBtn.setSelfPosition(45, 45);
                cancelBtn.setSize(45, 20);
                cancelBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFFCC4444), new TextTexture("§cCan"));
                cancelBtn.setOnPressCallback(cd -> {
                    pendingWeaponUpgrades.put(path.name(), pendingPurchases - 1);
                    if (pendingWeaponUpgrades.get(path.name()) <= 0) pendingWeaponUpgrades.remove(path.name());
                    updatePathList();
                    updateFooter();
                });
                widget.addWidget(cancelBtn);
            }

            ButtonWidget upgradeBtn = new ButtonWidget();
            upgradeBtn.setSelfPosition(95, 45);
            upgradeBtn.setSize(55, 20);

            long availableSP = ClientLevelingData.getAvailableSkillPoints() - calculateTotalPendingCost();
            
            if (accumulated >= requiredDamage && availableSP >= nextCost) {
                upgradeBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("§aAdd"));
                upgradeBtn.setOnPressCallback(cd -> {
                    pendingWeaponUpgrades.put(path.name(), pendingPurchases + 1);
                    updatePathList();
                    updateFooter();
                });
            } else {
                upgradeBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§cAdd"));
                List<String> tooltip = new ArrayList<>();
                tooltip.add("§cRequirements not met:");
                if (accumulated < requiredDamage) {
                    tooltip.add("§7- Deal more damage with " + path.name() + " weapons");
                }
                if (availableSP < nextCost) {
                    tooltip.add("§7- Insufficient SP");
                }
                upgradeBtn.setHoverTooltips(tooltip.toArray(new String[0]));
            }
            widget.addWidget(upgradeBtn);
        }
        return widget;
    }

    private String getTierName(int level) {
        if (level < 5) return "Nov";
        if (level < 10) return "Appr";
        if (level < 15) return "Ade";
        if (level < 20) return "Exp";
        return "Mas";
    }

    private String getTierColor(int level) {
        if (level < 5) return "§f";
        if (level < 10) return "§a";
        if (level < 15) return "§9";
        if (level < 20) return "§5";
        return "§6";
    }
}
