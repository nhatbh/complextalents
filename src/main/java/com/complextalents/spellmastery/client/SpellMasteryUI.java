package com.complextalents.spellmastery.client;

import com.complextalents.leveling.client.ClientLevelingData;
import com.complextalents.network.PacketHandler;
import com.complextalents.spellmastery.network.FinalizeGrimoirePacket;
import com.complextalents.dev.SimpleUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.stream.Collectors;

public class SpellMasteryUI {
    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath("complextalents",
            "spell_mastery");

    private final Player player;
    private final IUIHolder holder;

    private String selectedSchool = ""; // Will be initialized in createUI
    private String selectedTier = "Common";
    private boolean rarityAscending = false;

    private static record SpellTierEntry(AbstractSpell spell, int level, SpellRarity rarity) {
        public String getUniqueId() {
            return spell.getSpellResource().toString() + "@" + level;
        }
    }

    private final List<SpellTierEntry> allSpells;
    private final Set<String> pendingSpells = new HashSet<>();
    private LabelWidget consumedLabel;
    private ButtonWidget confirmBtn;
    private WidgetGroup spellListContainer;

    public SpellMasteryUI(Player player, IUIHolder holder) {
        this.player = player;
        this.holder = holder;
        List<SpellTierEntry> entries = new ArrayList<>();
        for (AbstractSpell spell : SpellRegistry.REGISTRY.get().getValues()) {
            if (spell == null || spell == SpellRegistry.none())
                continue;
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
        SimpleUIFactory.register(UI_ID, (player, holder) -> new SpellMasteryUI(player, holder).createUI());
    }

    public ModularUI createUI() {
        WidgetGroup root = new WidgetGroup();
        root.setSize(350, 270);
        root.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);

        // Header
        LabelWidget title = new LabelWidget();
        title.setSelfPosition(10, 10);
        title.setText("§lGrimoire Mastery");
        root.addWidget(title);

        LabelWidget spLabel = new LabelWidget();
        spLabel.setSelfPosition(250, 10);
        spLabel.setTextProvider(() -> "SP: §e" + ClientLevelingData.getAvailableSkillPoints());
        root.addWidget(spLabel);

        // Filters Container
        WidgetGroup filters = new WidgetGroup();
        filters.setSelfPosition(10, 30);
        filters.setSize(330, 40);

        // School Filter
        SelectorWidget schoolSelector = new SelectorWidget();
        schoolSelector.setSelfPosition(0, 0);
        schoolSelector.setSize(120, 18);
        List<String> schools = new ArrayList<>();
        io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValues()
                .forEach(school -> schools.add(school.getDisplayName().getString()));

        if (!schools.isEmpty() && selectedSchool.isEmpty()) {
            this.selectedSchool = schools.get(0);
        }

        schoolSelector.setCandidates(schools);
        schoolSelector.setValue(selectedSchool);
        schoolSelector.setOnChanged(val -> {
            this.selectedSchool = val;
            updateSpellList();
        });

        // Darker background for school selector
        schoolSelector.setButtonBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444));
        filters.addWidget(schoolSelector);

        // Tier Filter
        SelectorWidget tierSelector = new SelectorWidget();
        tierSelector.setSelfPosition(130, 0);
        tierSelector.setSize(90, 18);
        tierSelector.setCandidates(List.of("Common", "Uncommon", "Rare", "Epic", "Legendary"));
        tierSelector.setValue("Common");
        tierSelector.setOnChanged(val -> {
            this.selectedTier = val;
            updateSpellList();
        });
        tierSelector.setButtonBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444));
        filters.addWidget(tierSelector);

        // Rarity Sort Toggle
        ButtonWidget sortBtn = new ButtonWidget();
        sortBtn.setSelfPosition(230, 0);
        sortBtn.setSize(90, 18);
        sortBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444),
                new TextTexture(rarityAscending ? "Tier: Asc" : "Tier: Desc"));
        sortBtn.setOnPressCallback(clickData -> {
            this.rarityAscending = !this.rarityAscending;
            sortBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444),
                    new TextTexture(rarityAscending ? "Tier: Asc" : "Tier: Desc"));
            updateSpellList();
        });
        filters.addWidget(sortBtn);

        // Spell List (Draggable Scrollable)
        DraggableScrollableWidgetGroup scrollable = new DraggableScrollableWidgetGroup();
        scrollable.setSelfPosition(10, 55);
        scrollable.setSize(330, 185);

        spellListContainer = new WidgetGroup();
        spellListContainer.setSize(310, 800); // Initial height, updated in updateSpellList
        scrollable.addWidget(spellListContainer);

        updateSpellList();
        root.addWidget(scrollable);

        // Confirmation and SP Info
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
        confirmBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444444),
                new TextTexture("Finalize"));
        confirmBtn.setOnPressCallback(clickData -> {
            if (!pendingSpells.isEmpty()) {
                List<FinalizeGrimoirePacket.MasteryUpgrade> upgrades = new ArrayList<>();
                List<FinalizeGrimoirePacket.SpellPurchase> purchases = new ArrayList<>();
                
                for (String entry : pendingSpells) {
                    if (entry.startsWith("S:")) {
                        String[] parts = entry.substring(2).split("@");
                        purchases.add(new FinalizeGrimoirePacket.SpellPurchase(ResourceLocation.parse(parts[0]), Integer.parseInt(parts[1])));
                    } else if (entry.startsWith("M:")) {
                        String[] parts = entry.substring(2).split("@");
                        upgrades.add(new FinalizeGrimoirePacket.MasteryUpgrade(ResourceLocation.parse(parts[0]), Integer.parseInt(parts[1])));
                    }
                }
                
                PacketHandler.sendToServer(new FinalizeGrimoirePacket(upgrades, purchases));
                pendingSpells.clear();
                player.closeContainer();
            }
        });
        footer.addWidget(confirmBtn);

        root.addWidget(footer);
        updateFooter();

        root.addWidget(filters);
        return new ModularUI(root, holder, player);
    }

    private void updateFooter() {
        if (consumedLabel != null) {
            int cost = calculatePendingCost();
            consumedLabel.setVisible(cost > 0);
        }
        if (confirmBtn != null) {
            confirmBtn.setVisible(!pendingSpells.isEmpty());
        }
    }

    private int calculatePendingCost() {
        int total = 0;
        for (String entryStr : pendingSpells) {
            if (entryStr.startsWith("S:")) {
                String[] parts = entryStr.substring(2).split("@");
                AbstractSpell spell = SpellRegistry.getSpell(ResourceLocation.parse(parts[0]));
                if (spell != null) {
                    total += com.complextalents.spellmastery.SpellMasteryManager.getSpellCost(spell.getRarity(Integer.parseInt(parts[1])));
                }
            } else if (entryStr.startsWith("M:")) {
                String[] parts = entryStr.substring(2).split("@");
                total += com.complextalents.spellmastery.SpellMasteryManager.getMasteryBuyUpCost(Integer.parseInt(parts[1]));
            }
        }
        return total;
    }

    private void updateSpellList() {
        spellListContainer.clearAllWidgets();

        List<SpellTierEntry> filtered = allSpells.stream()
                .filter(e -> e.spell.getSchoolType().getDisplayName().getString().equals(selectedSchool))
                .filter(e -> e.rarity.name().equalsIgnoreCase(selectedTier))
                .sorted((e1, e2) -> {
                    int r1 = e1.rarity.getValue();
                    int r2 = e2.rarity.getValue();
                    int comparison = rarityAscending ? Integer.compare(r1, r2) : Integer.compare(r2, r1);
                    if (comparison != 0)
                        return comparison;

                    int schoolComp = e1.spell.getSchoolType().getDisplayName().getString()
                            .compareTo(e2.spell.getSchoolType().getDisplayName().getString());
                    if (schoolComp != 0)
                        return schoolComp;

                    return e1.spell.getDisplayName(null).getString()
                            .compareTo(e2.spell.getDisplayName(null).getString());
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
            if (col >= 2) {
                col = 0;
                xOffset = 0;
                yOffset += 50; // 45 height + 5 spacing
            } else {
                xOffset += 160; // 155 width + 5 spacing
            }
        }

        // Add Mastery Buy-up button if applicable
        io.redspace.ironsspellbooks.api.spells.SchoolType school = io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValues().stream()
                .filter(s -> s.getDisplayName().getString().equals(selectedSchool))
                .findFirst().orElse(null);

        if (school != null) {
            int currentMastery = ClientSpellMasteryData.getMasteryLevel(school.getId());
            SpellRarity targetRarity = SpellRarity.valueOf(selectedTier.toUpperCase());
            int targetValue = targetRarity.getValue();

            // Check if this tier is next in progression and not already unlocked
            if (currentMastery < targetValue && targetValue == currentMastery + 1) {
                // Show buy-up option for the next tier ALWAYS at the end of the list
                int cost = com.complextalents.spellmastery.SpellMasteryManager.getMasteryBuyUpCost(targetValue);
                if (cost < 999) {
                    WidgetGroup buyWidget = createMasteryBuyEntry(school, targetRarity, cost);
                    buyWidget.setSelfPosition(0, yOffset + (col > 0 ? 50 : 0));
                    spellListContainer.addWidget(buyWidget);
                    yOffset += 50;
                }
            }
        }

        // Update container height to fit all spells and enable scrolling
        int totalRows = (int) Math.ceil(filtered.size() / 2.0);
        spellListContainer.setSize(320, Math.max(185, totalRows * 50));
    }

    private WidgetGroup createSpellEntry(SpellTierEntry entry) {
        AbstractSpell spell = entry.spell;
        int level = entry.level;
        SpellRarity rarity = entry.rarity;

        WidgetGroup widget = new WidgetGroup();
        widget.setSize(155, 45); // Grid entry: 2 columns

        boolean learned = ClientSpellMasteryData.isSpellLearned(spell.getSpellResource(), level);
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON);

        // Icon
        ImageWidget icon = new ImageWidget();
        icon.setSelfPosition(5, 5);
        icon.setSize(20, 20);
        icon.setImage(new ResourceTexture(spell.getSpellIconResource()));
        widget.addWidget(icon);

        // Name
        LabelWidget name = new LabelWidget();
        name.setSelfPosition(30, 5);
        String nameText = rarity.getChatFormatting() + spell.getDisplayName(null).getString();
        if (learned)
            nameText = "§8" + ChatFormatting.stripFormatting(nameText);
        name.setText(nameText);

        // School and Info
        LabelWidget info = new LabelWidget();
        info.setSelfPosition(30, 15);
        int cost = com.complextalents.spellmastery.SpellMasteryManager.getSpellCost(rarity);
        String schoolName = spell.getSchoolType().getDisplayName().getString();
        String infoText = "§7" + schoolName + " | §e" + cost + "SP";
        if (learned)
            infoText = "§8" + ChatFormatting.stripFormatting(infoText);
        info.setText(infoText);
        widget.addWidget(name);
        widget.addWidget(info);

        // Learn Button
        ButtonWidget learnBtn = new ButtonWidget();
        learnBtn.setSelfPosition(5, 26);
        learnBtn.setSize(145, 14);

        if (learned) {
            learnBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§7Learned"));
        } else {
            String pendingId = "S:" + entry.getUniqueId();
            boolean isPending = pendingSpells.contains(pendingId);

            if (isPending) {
                learnBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44),
                        new TextTexture("Selected"));
                learnBtn.setOnPressCallback(clickData -> {
                    pendingSpells.remove(pendingId);
                    updateSpellList();
                    updateFooter();
                });
            } else {
                int currentSP = ClientLevelingData.getAvailableSkillPoints();
                int pendingCost = calculatePendingCost();
                
                // Track pending mastery level for this school
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
                    if ((currentSP - pendingCost) < cost)
                        tooltip.add("§7- Need §e" + cost + " SP §7(Available: §e" + (currentSP - pendingCost) + "§7)");
                    if (effectiveMastery < requiredMastery) {
                        int prevRarityValue = requiredMastery - 1;
                        io.redspace.ironsspellbooks.api.spells.SpellRarity prevRarity = io.redspace.ironsspellbooks.api.spells.SpellRarity
                                .values()[prevRarityValue];
                        int needed = (prevRarityValue == 0 || prevRarityValue == 1) ? 3 : 2;
                        int currentCount = ClientSpellMasteryData.getLearnedCount(spell.getSchoolType().getId(),
                                prevRarity);

                        tooltip.add(
                                "§7- Need §b" + rarity.getDisplayName().getString() + " " + schoolName + " Mastery");
                        tooltip.add("§7- Progress: §a" + currentCount + "/" + needed + " "
                                + prevRarity.getDisplayName().getString() + " spells learned");
                    }
                    learnBtn.setHoverTooltips(tooltip.toArray(new String[0]));
                } else {
                    learnBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("Select"));
                    learnBtn.setOnPressCallback(clickData -> {
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
        widget.setBackground(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF444466)); // Slightly blueish

        LabelWidget title = new LabelWidget();
        title.setSelfPosition(10, 5);
        title.setText("§bUnlock " + targetRarity.getDisplayName().getString() + " Mastery");
        widget.addWidget(title);

        LabelWidget desc = new LabelWidget();
        desc.setSelfPosition(10, 15);
        double reward = cost * 0.02;
        desc.setText("§7Lump sum: §e" + cost + " SP §7| Reward: §a+" + String.format("%.2f", reward) + " Spell Power");
        widget.addWidget(desc);

        ButtonWidget buyBtn = new ButtonWidget();
        buyBtn.setSelfPosition(10, 26);
        buyBtn.setSize(295, 14);
        
        String pendingId = "M:" + school.getId().toString() + "@" + targetRarity.getValue();
        boolean isPending = pendingSpells.contains(pendingId);
        
        if (isPending) {
            buyBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON.copy().setColor(0xFF44FF44), new TextTexture("Selected"));
            buyBtn.setOnPressCallback(clickData -> {
                pendingSpells.remove(pendingId);
                updateSpellList();
                updateFooter();
            });
        } else {
            int availableSP = ClientLevelingData.getAvailableSkillPoints();
            int pendingCost = calculatePendingCost();
            if ((availableSP - pendingCost) >= cost) {
                buyBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("Add to Selection"));
                buyBtn.setOnPressCallback(clickData -> {
                    pendingSpells.add(pendingId);
                    updateSpellList();
                    updateFooter();
                });
            } else {
                buyBtn.setButtonTexture(ResourceBorderTexture.BUTTON_COMMON, new TextTexture("§cInsufficient SP (" + cost + ")"));
            }
        }
        widget.addWidget(buyBtn);

        return widget;
    }
}
