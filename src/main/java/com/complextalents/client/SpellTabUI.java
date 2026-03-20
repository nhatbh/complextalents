package com.complextalents.client;

import com.complextalents.origin.OriginManager;
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.client.ClientSpellMasteryData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

public class SpellTabUI {
    private final UpgradeCart cart;

    private final List<SpellTierEntry> allSpells;
    private String selectedSchool = "";
    private String selectedTier = "Common";
    private boolean rarityAscending = false;
    private int currentPage = 0;
    private int totalPages = 0;
    private static final int SPELLS_PER_PAGE = 9;
    private static final int SPELLS_PER_ROW = 3;

    private record SpellTierEntry(AbstractSpell spell, int level, SpellRarity rarity) {
        public String getUniqueId() {
            return spell.getSpellResource().toString() + "@" + level;
        }
    }

    public SpellTabUI(UpgradeCart cart) {
        this.cart = cart;

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

        // Initialize school selector
        List<String> schools = new ArrayList<>();
        SchoolRegistry.REGISTRY.get().getValues().forEach(school -> schools.add(school.getDisplayName().getString()));
        if (!schools.isEmpty() && selectedSchool.isEmpty()) {
            this.selectedSchool = schools.get(0);
        }
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        List<Button> buttons = new ArrayList<>();
        update();

        // Build school selector toggle button
        Button schoolBtn = Button.builder(Component.literal("School: " + selectedSchool),
                (btn) -> cycleSchool())
                .pos(xOffset + 15, yOffset + 10)
                .size(130, 18)
                .build();
        buttons.add(schoolBtn);

        // Build tier selector toggle button
        Button tierBtn = Button.builder(Component.literal("Tier: " + selectedTier),
                (btn) -> cycleTier())
                .pos(xOffset + 165, yOffset + 10)
                .size(110, 18)
                .build();
        buttons.add(tierBtn);

        // Build sort toggle button
        Button sortBtn = Button.builder(Component.literal(rarityAscending ? "Sort: Asc" : "Sort: Desc"),
                (btn) -> {
                    rarityAscending = !rarityAscending;
                    cart.notifyUpdate();
                })
                .pos(xOffset + 310, yOffset + 10)
                .size(95, 18)
                .build();
        buttons.add(sortBtn);

        // Build filter buttons and spell/mastery buttons
        List<SpellTierEntry> filtered = getFilteredSpells();
        this.totalPages = (filtered.size() + SPELLS_PER_PAGE - 1) / SPELLS_PER_PAGE;

        currentPage = Math.min(currentPage, Math.max(0, this.totalPages - 1));

        int xPos = xOffset + 10;
        int yPos = yOffset + 40;
        int col = 0;
        int row = 0;
        int index = 0;

        // Add pagination buttons (always visible, but disabled if no pages to navigate)
        Button prevBtn = Button.builder(Component.literal("<"),
                (btn) -> {
                    currentPage = Math.max(0, currentPage - 1);
                    cart.notifyUpdate();
                })
                .pos(xOffset + 10, yOffset + 325)
                .size(40, 16)
                .build();
        prevBtn.active = (currentPage > 0) && (this.totalPages > 0);
        buttons.add(prevBtn);

        Button nextBtn = Button.builder(Component.literal(">"),
                (btn) -> {
                    currentPage = Math.min(this.totalPages - 1, currentPage + 1);
                    cart.notifyUpdate();
                })
                .pos(xOffset + 440, yOffset + 325)
                .size(40, 16)
                .build();
        nextBtn.active = (currentPage < this.totalPages - 1) && (this.totalPages > 0);
        buttons.add(nextBtn);

        int startIndex = currentPage * SPELLS_PER_PAGE;
        int endIndex = Math.min(startIndex + SPELLS_PER_PAGE, filtered.size());

        for (int i = startIndex; i < endIndex; i++) {
            SpellTierEntry entry = filtered.get(i);
            int btnX = xPos + (col * 160);
            int btnY = yPos + (row * 50);

            String pendingId = "S:" + entry.getUniqueId();
            boolean learned = ClientSpellMasteryData.isSpellLearned(entry.spell.getSpellResource(), entry.level);
            boolean isPending = isPending(pendingId);
            int cost = getAdjustedSpellCost(entry.rarity);

            // Create +/- button at bottom right of card
            Button spellBtn;
            if (learned) {
                spellBtn = new ColoredButton(btnX + 125, btnY + 28, 20, 14,
                        Component.literal("-"), (btn) -> {}, 0xFFCC4444);
                spellBtn.active = false;
            } else {
                int effectiveMastery = getEffectiveMasteryLevel(entry.spell.getSchoolType().getId());
                int requiredMastery = entry.rarity.getValue();
                boolean canSelect = cart.canAfford(cost) && effectiveMastery >= requiredMastery;

                if (isPending) {
                    spellBtn = new ColoredButton(btnX + 125, btnY + 28, 20, 14,
                            Component.literal("-"), (btn) -> toggle(pendingId, cost), 0xFFCC4444);
                } else if (canSelect) {
                    spellBtn = Button.builder(Component.literal("+"),
                            (btn) -> toggle(pendingId, cost))
                            .pos(btnX + 125, btnY + 28)
                            .size(20, 14)
                            .build();
                } else {
                    spellBtn = Button.builder(Component.literal("+"),
                            (btn) -> {})
                            .pos(btnX + 125, btnY + 28)
                            .size(20, 14)
                            .build();
                    spellBtn.active = false;
                }
            }
            buttons.add(spellBtn);

            col++;
            if (col >= 3) {
                col = 0;
                row++;
            }
        }

        // Add mastery unlock button for selected tier (formatted like a spell card)
        SchoolType school = SchoolRegistry.REGISTRY.get().getValues().stream()
                .filter(s -> s.getDisplayName().getString().equals(selectedSchool)).findFirst().orElse(null);

        if (school != null) {
            SpellRarity targetRarity = SpellRarity.valueOf(selectedTier.toUpperCase());
            int targetValue = targetRarity.getValue();
            int actualBaseMastery = ClientSpellMasteryData.getMasteryLevel(school.getId());

            // Show button if tier is not yet unlocked
            if (actualBaseMastery < targetValue) {
                int cost = getAdjustedMasteryBuyUpCost(targetValue);
                if (cost < 999) {
                    String pendingId = "M:" + school.getId().toString() + "@" + targetValue;
                    boolean isPending = isPending(pendingId);

                    // Position mastery card in the next available slot
                    int masteryCardX = xPos + (col * 160);
                    int masteryCardY = yPos + (row * 50);

                    // Create mastery button like a spell card
                    Button masteryBtn;
                    if (isPending) {
                        masteryBtn = new ColoredButton(masteryCardX + 125, masteryCardY + 28, 20, 14,
                                Component.literal("-"), (btn) -> toggle(pendingId, cost), 0xFFCC4444);
                    } else if (cart.canAfford(cost)) {
                        masteryBtn = Button.builder(Component.literal("+"),
                                (btn) -> toggle(pendingId, cost))
                                .pos(masteryCardX + 125, masteryCardY + 28)
                                .size(20, 14)
                                .build();
                    } else {
                        masteryBtn = Button.builder(Component.literal("+"),
                                (btn) -> {})
                                .pos(masteryCardX + 125, masteryCardY + 28)
                                .size(20, 14)
                                .build();
                        masteryBtn.active = false;
                    }
                    buttons.add(masteryBtn);
                }
            }
        }

        return buttons;
    }

    public void update() {
        // Update is handled via cart callback
    }

    public void renderBackgrounds(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        // Draw filter area background (buttons area)
        guiGraphics.fill(xOffset + 10, yOffset, xOffset + 490, yOffset + 35, 0xFF333333);
        guiGraphics.fill(xOffset + 11, yOffset + 1, xOffset + 489, yOffset + 34, 0xFF222222);

        // Draw spell grid background
        guiGraphics.fill(xOffset + 10, yOffset + 35, xOffset + 490, yOffset + 360, 0xFF111111);
    }

    public void renderLabels(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int xOffset, int yOffset, int mouseX, int mouseY) {
        // Filter labels and buttons are now rendered as part of buildWidgets() buttons

        // Draw spell grid
        List<SpellTierEntry> filtered = getFilteredSpells();
        int totalPages = (filtered.size() + SPELLS_PER_PAGE - 1) / SPELLS_PER_PAGE;

        int xPos = xOffset + 10;
        int yPos = yOffset + 40;
        int col = 0;
        int row = 0;

        int startIndex = currentPage * SPELLS_PER_PAGE;
        int endIndex = Math.min(startIndex + SPELLS_PER_PAGE, filtered.size());

        for (int i = startIndex; i < endIndex; i++) {
            SpellTierEntry entry = filtered.get(i);
            int cardX = xPos + 5 + (col * 160);
            int cardY = yPos + (row * 50) + 5;

            // Draw spell card background
            guiGraphics.fill(cardX - 5, cardY - 5, cardX + 150, cardY + 40, 0xFF333333);
            guiGraphics.fill(cardX - 4, cardY - 4, cardX + 149, cardY + 39, 0xFF222222);

            boolean learned = ClientSpellMasteryData.isSpellLearned(entry.spell.getSpellResource(), entry.level);

            // Draw spell icon on the left
            guiGraphics.blit(entry.spell.getSpellIconResource(), cardX, cardY, 0, 0, 16, 16, 16, 16);

            String nameText = entry.rarity.getChatFormatting() + entry.spell.getDisplayName(null).getString();
            if (learned) nameText = "§8" + ChatFormatting.stripFormatting(nameText);
            // Truncate name to fit within card width (approx 120 pixels for text)
            while (font.width(nameText) > 120 && nameText.length() > 3) {
                nameText = nameText.substring(0, nameText.length() - 1);
            }
            if (nameText.length() > 3 && font.width(nameText) > 120) {
                nameText = nameText.substring(0, Math.max(1, nameText.length() - 3)) + "..";
            }

            int cost = getAdjustedSpellCost(entry.rarity);
            String infoText = "§7" + entry.spell.getSchoolType().getDisplayName().getString() + " | §e" + cost + "SP";
            if (learned) infoText = "§8" + ChatFormatting.stripFormatting(infoText);

            // Adjust text position to account for icon
            guiGraphics.drawString(font, nameText, cardX + 20, cardY, 0xFFFFFF, false);
            guiGraphics.drawString(font, infoText, cardX + 20, cardY + 10, 0xFFAAAA, false);

            col++;
            if (col >= 3) {
                col = 0;
                row++;
            }
        }

        // Draw page indicator
        if (this.totalPages > 1) {
            String pageText = "Page " + (currentPage + 1) + "/" + this.totalPages;
            guiGraphics.drawString(font, pageText, xPos + 200, yOffset + 328, 0xFFAAAAAA, false);
        }

        // Draw mastery unlock card (formatted like a spell card)
        SchoolType school = SchoolRegistry.REGISTRY.get().getValues().stream()
                .filter(s -> s.getDisplayName().getString().equals(selectedSchool)).findFirst().orElse(null);

        if (school != null) {
            SpellRarity targetRarity = SpellRarity.valueOf(selectedTier.toUpperCase());
            int targetValue = targetRarity.getValue();
            int actualBaseMastery = ClientSpellMasteryData.getMasteryLevel(school.getId());

            // Show card if tier is not yet unlocked
            if (actualBaseMastery < targetValue) {
                int cost = getAdjustedMasteryBuyUpCost(targetValue);
                if (cost < 999) {
                    // Position mastery card in the next available slot
                    int masteryCardX = xPos + 5 + (col * 160);
                    int masteryCardY = yPos + (row * 50) + 5;

                    // Draw mastery card background (like spell card)
                    guiGraphics.fill(masteryCardX - 5, masteryCardY - 5, masteryCardX + 150, masteryCardY + 40, 0xFF333333);
                    guiGraphics.fill(masteryCardX - 4, masteryCardY - 4, masteryCardX + 149, masteryCardY + 39, 0xFF222222);

                    // Draw school icon placeholder (can use school texture if available)
                    String schoolName = school.getDisplayName().getString();
                    String masteryText = "§b" + targetRarity.getDisplayName().getString() + " Mastery";
                    String costText = "§7" + schoolName + " | §e" + cost + "SP";

                    // Adjust text position to account for icon space
                    guiGraphics.drawString(font, masteryText, masteryCardX + 20, masteryCardY, 0xFFFFFF, false);
                    guiGraphics.drawString(font, costText, masteryCardX + 20, masteryCardY + 10, 0xFFAAAA, false);
                }
            }
        }
    }

    private void cycleSchool() {
        List<String> schools = new ArrayList<>();
        SchoolRegistry.REGISTRY.get().getValues().forEach(school -> schools.add(school.getDisplayName().getString()));

        if (schools.isEmpty()) return;

        int currentIndex = schools.indexOf(selectedSchool);
        int nextIndex = (currentIndex + 1) % schools.size();
        selectedSchool = schools.get(nextIndex);
        currentPage = 0; // Reset page when changing filter
        cart.notifyUpdate(); // Notify cart to rebuild UI
    }

    private void cycleTier() {
        SpellRarity[] rarities = SpellRarity.values();
        int currentIndex = -1;

        for (int i = 0; i < rarities.length; i++) {
            if (rarities[i].name().equalsIgnoreCase(selectedTier)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) currentIndex = 0;
        int nextIndex = (currentIndex + 1) % rarities.length;
        selectedTier = rarities[nextIndex].name().substring(0, 1).toUpperCase() + rarities[nextIndex].name().substring(1).toLowerCase();
        currentPage = 0; // Reset page when changing filter
        cart.notifyUpdate(); // Notify cart to rebuild UI
    }

    private List<SpellTierEntry> getFilteredSpells() {
        return allSpells.stream()
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
    }

    private boolean isPending(String uniqueId) {
        return cart.getAmount(getTypeFromId(uniqueId), uniqueId) > 0;
    }

    private void toggle(String uniqueId, int cost) {
        if (isPending(uniqueId)) {
            cart.removeItem(getTypeFromId(uniqueId), uniqueId);
        } else {
            if (cart.canAfford(cost)) {
                cart.modifyItem(getTypeFromId(uniqueId), uniqueId, 1, cost);
            }
        }
    }

    private UpgradeType getTypeFromId(String uniqueId) {
        if (uniqueId.startsWith("M:")) return UpgradeType.SPELL_MASTERY;
        if (uniqueId.startsWith("S:")) return UpgradeType.SPELL_PURCHASE;
        return UpgradeType.SPELL_PURCHASE;
    }

    private int getEffectiveMasteryLevel(ResourceLocation schoolId) {
        int current = ClientSpellMasteryData.getMasteryLevel(schoolId);
        int pending = 0;
        for (UpgradeItem item : cart.getItems()) {
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

    /**
     * Get the spell cost adjusted by the player's origin mastery cost multiplier.
     */
    private int getAdjustedSpellCost(SpellRarity rarity) {
        // Get origin from cached client data
        ResourceLocation originId = com.complextalents.origin.client.ClientOriginData.getOriginId();

        if (originId != null) {
            return SpellMasteryManager.getSpellCost(rarity, originId);
        }
        return SpellMasteryManager.getSpellCost(rarity);
    }

    /**
     * Get the mastery buy-up cost adjusted by the player's origin mastery cost multiplier.
     */
    private int getAdjustedMasteryBuyUpCost(int tier) {
        // Get origin from cached client data
        ResourceLocation originId = com.complextalents.origin.client.ClientOriginData.getOriginId();

        if (originId != null) {
            return SpellMasteryManager.getMasteryBuyUpCost(tier, originId);
        }
        return SpellMasteryManager.getMasteryBuyUpCost(tier);
    }
}
