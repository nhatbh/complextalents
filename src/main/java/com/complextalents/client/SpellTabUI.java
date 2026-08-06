package com.complextalents.client;

import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.client.ClientSpellMasteryData;
import com.complextalents.stats.ClassCostMatrix;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
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
    private String selectedTier = "All";
    private boolean rarityAscending = false;
    private int currentPage = 0;
    private int totalPages = 0;
    private static final int SPELLS_PER_PAGE = 18;
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
            for (int lvl : SpellMasteryManager.getTierEntryLevels(spell)) {
                SpellRarity r = spell.getRarity(lvl);
                entries.add(new SpellTierEntry(spell, lvl, r));
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

        // Build tier selector toggle button ("All", "Common", "Uncommon", etc.)
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

        // Build Inscription Table icon button to open Iron's Spell Inscription Table menu directly
        Button inscriptionBtn = Button.builder(Component.empty(),
                (btn) -> com.complextalents.network.PacketHandler.sendToServer(new com.complextalents.network.C2SOpenInscriptionTablePacket()))
                .pos(xOffset + 445, yOffset + 9)
                .size(20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Open Inscription Table")))
                .build();
        buttons.add(inscriptionBtn);

        // Build filter buttons and spell buttons
        List<SpellTierEntry> filtered = getFilteredSpells();
        this.totalPages = (filtered.size() + SPELLS_PER_PAGE - 1) / SPELLS_PER_PAGE;

        currentPage = Math.min(currentPage, Math.max(0, this.totalPages - 1));

        int xPos = xOffset + 10;
        int yPos = yOffset + 40;
        int col = 0;
        int row = 0;

        // Add pagination buttons
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
            int btnX = xPos + 5 + (col * 160);
            int btnY = yPos + (row * 42) + 5;

            boolean learned = false;
            for (int lvl = entry.spell.getMinLevel(); lvl <= entry.spell.getMaxLevel(); lvl++) {
                if (ClientSpellMasteryData.isSpellLearned(entry.spell.getSpellResource(), lvl)) {
                    if (entry.spell.getRarity(lvl).getValue() >= entry.rarity.getValue()) {
                        learned = true;
                        break;
                    }
                }
            }
            String pendingId = "S:" + entry.getUniqueId();
            boolean isPending = isPending(pendingId);
            int cost = getAdjustedSpellUpgradeCost(entry.spell, entry.level);

            Button spellBtn;
            if (cost < 0) {
                spellBtn = new ColoredButton(btnX + 129, btnY + 12, 18, 14,
                        Component.literal("✕"), (btn) -> {}, 0xFF666666);
                spellBtn.active = false;
            } else if (learned) {
                spellBtn = new ColoredButton(btnX + 129, btnY + 12, 18, 14,
                        Component.literal("-"), (btn) -> {}, 0xFFCC4444);
                spellBtn.active = false;
            } else {
                boolean canSelect = cart.canAfford(cost);

                if (isPending) {
                    spellBtn = new ColoredButton(btnX + 129, btnY + 12, 18, 14,
                            Component.literal("-"), (btn) -> toggle(pendingId, cost), 0xFFCC4444);
                } else if (canSelect) {
                    spellBtn = Button.builder(Component.literal("+"),
                            (btn) -> toggle(pendingId, cost))
                            .pos(btnX + 129, btnY + 12)
                            .size(18, 14)
                            .build();
                } else {
                    spellBtn = Button.builder(Component.literal("+"),
                            (btn) -> {})
                            .pos(btnX + 129, btnY + 12)
                            .size(18, 14)
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

        return buttons;
    }

    public void update() {
        // Update handled via cart callback
    }

    public void renderBackgrounds(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        // Filter area header background
        guiGraphics.fill(xOffset + 10, yOffset - 5, xOffset + 490, yOffset + 32, 0xFF282C3D);
        guiGraphics.fill(xOffset + 11, yOffset - 4, xOffset + 489, yOffset + 31, 0xFF141724);

        // Spell grid area background
        guiGraphics.fill(xOffset + 10, yOffset + 35, xOffset + 490, yOffset + 355, 0xFF141724);
        guiGraphics.fill(xOffset + 11, yOffset + 36, xOffset + 489, yOffset + 354, 0xFF0D0F18);
    }

    public void renderLabels(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int xOffset, int yOffset, int mouseX, int mouseY) {
        // Render Inscription Table icon on top of button
        net.minecraft.world.item.Item inscriptionTableItem = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "inscription_table"));
        if (inscriptionTableItem != null && inscriptionTableItem != net.minecraft.world.item.Items.AIR) {
            guiGraphics.renderItem(new net.minecraft.world.item.ItemStack(inscriptionTableItem), xOffset + 447, yOffset + 11);
        }

        List<SpellTierEntry> filtered = getFilteredSpells();

        int xPos = xOffset + 10;
        int yPos = yOffset + 38;
        int col = 0;
        int row = 0;

        int startIndex = currentPage * SPELLS_PER_PAGE;
        int endIndex = Math.min(startIndex + SPELLS_PER_PAGE, filtered.size());

        for (int i = startIndex; i < endIndex; i++) {
            SpellTierEntry entry = filtered.get(i);
            int cardX = xPos + 5 + (col * 160);
            int cardY = yPos + (row * 42) + 5;

            int borderColor = getRarityBorderColor(entry.rarity);
            boolean learned = ClientSpellMasteryData.isSpellLearned(entry.spell.getSpellResource(), entry.level);
            if (learned) borderColor = 0xFF333344;

            // Rarity left-accent strip (3px wide)
            guiGraphics.fill(cardX - 3, cardY - 2, cardX, cardY + 36, borderColor);
            // Card body
            guiGraphics.fill(cardX, cardY - 2, cardX + 150, cardY + 36, learned ? 0xFF12131A : 0xFF171A28);

            // Spell Icon (16x16)
            guiGraphics.blit(entry.spell.getSpellIconResource(), cardX + 3, cardY + 1, 0, 0, 16, 16, 16, 16);

            // Spell Name (truncated)
            String nameText = entry.rarity.getChatFormatting() + entry.spell.getDisplayName(null).getString();
            if (learned) nameText = "§8" + ChatFormatting.stripFormatting(nameText);
            while (font.width(nameText) > 122 && nameText.length() > 3) {
                nameText = nameText.substring(0, nameText.length() - 1);
            }
            guiGraphics.drawString(font, nameText, cardX + 22, cardY, 0xFFFFFF, false);

            // School + Level info row
            String schoolName = entry.spell.getSchoolType().getDisplayName().getString();
            String infoLine = learned ? "§8" + schoolName + " L" + entry.level : "§7" + schoolName + " L" + entry.level;
            guiGraphics.drawString(font, infoLine, cardX + 22, cardY + 11, 0xFF888888, false);

            // Cost / Status row
            int cost = getAdjustedSpellUpgradeCost(entry.spell, entry.level);
            boolean hasLowerLearned = hasLearnedLowerTier(entry.spell, entry.level);

            if (cost < 0) {
                guiGraphics.drawString(font, "§c✘ Unlearnable", cardX + 3, cardY + 24, 0xFFFF5555, false);
            } else if (learned) {
                guiGraphics.drawString(font, "§8✔ Learned", cardX + 3, cardY + 24, 0xFF555555, false);
            } else if (hasLowerLearned) {
                guiGraphics.drawString(font, "§aUpgrade §e" + cost + "SP", cardX + 3, cardY + 24, 0xFF55FF55, false);
            } else {
                guiGraphics.drawString(font, "§e" + cost + "SP", cardX + 3, cardY + 24, 0xFFFFAA00, false);
            }

            col++;
            if (col >= 3) {
                col = 0;
                row++;
            }
        }

        // Page indicator
        if (this.totalPages > 1) {
            String pageText = "Page " + (currentPage + 1) + "/" + this.totalPages;
            int pw = font.width(pageText);
            guiGraphics.drawString(font, "§7" + pageText, xPos + (480 - pw) / 2, yOffset + 305, 0xFFAAAAAA, false);
        }
    }

    private int getRarityBorderColor(SpellRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0xFF555566;
            case UNCOMMON -> 0xFF2E8B57;
            case RARE -> 0xFF1E90FF;
            case EPIC -> 0xFF9932CC;
            case LEGENDARY -> 0xFFFFD700;
        };
    }

    private void cycleSchool() {
        List<String> schools = new ArrayList<>();
        SchoolRegistry.REGISTRY.get().getValues().forEach(school -> schools.add(school.getDisplayName().getString()));

        if (schools.isEmpty()) return;

        int currentIndex = schools.indexOf(selectedSchool);
        int nextIndex = (currentIndex + 1) % schools.size();
        selectedSchool = schools.get(nextIndex);
        currentPage = 0;
        cart.notifyUpdate();
    }

    private void cycleTier() {
        List<String> options = new ArrayList<>();
        options.add("All");
        for (SpellRarity r : SpellRarity.values()) {
            String name = r.name().substring(0, 1).toUpperCase() + r.name().substring(1).toLowerCase();
            options.add(name);
        }

        int currentIndex = options.indexOf(selectedTier);
        if (currentIndex == -1) currentIndex = 0;
        int nextIndex = (currentIndex + 1) % options.size();
        selectedTier = options.get(nextIndex);
        currentPage = 0;
        cart.notifyUpdate();
    }

    private List<SpellTierEntry> getFilteredSpells() {
        return allSpells.stream()
                .filter(e -> e.spell.getSchoolType().getDisplayName().getString().equals(selectedSchool))
                .filter(e -> selectedTier.equalsIgnoreCase("All") || e.rarity.name().equalsIgnoreCase(selectedTier))
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
        if (cost < 0) return;
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

    private boolean hasLearnedLowerTier(AbstractSpell spell, int targetLevel) {
        ResourceLocation spellId = spell.getSpellResource();
        SpellRarity targetRarity = spell.getRarity(targetLevel);
        int targetValue = targetRarity.getValue();

        for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
            if (ClientSpellMasteryData.isSpellLearned(spellId, lvl)) {
                if (spell.getRarity(lvl).getValue() < targetValue) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getAdjustedSpellUpgradeCost(AbstractSpell spell, int targetLevel) {
        ResourceLocation originId = ClientOriginData.getOriginId();
        ResourceLocation spellId = spell.getSpellResource();
        SpellRarity targetRarity = spell.getRarity(targetLevel);
        int targetBaseCost = SpellMasteryManager.getSpellCost(targetRarity);

        int highestLearnedBaseCost = 0;
        for (int lvl = spell.getMinLevel(); lvl <= spell.getMaxLevel(); lvl++) {
            if (ClientSpellMasteryData.isSpellLearned(spellId, lvl)) {
                SpellRarity r = spell.getRarity(lvl);
                int c = SpellMasteryManager.getSpellCost(r);
                if (c > highestLearnedBaseCost) {
                    highestLearnedBaseCost = c;
                }
            }
        }

        int effectiveBase = targetBaseCost;
        if (highestLearnedBaseCost > 0 && targetBaseCost > highestLearnedBaseCost) {
            effectiveBase = targetBaseCost - highestLearnedBaseCost;
        } else if (highestLearnedBaseCost >= targetBaseCost) {
            return 0;
        }

        double multiplier = (spell.getSchoolType() != null)
                ? ClassCostMatrix.getSchoolSpellMasteryCostMultiplier(originId, spell.getSchoolType())
                : ClassCostMatrix.getSpellMasteryCostMultiplier(originId);
        if (multiplier < 0 || Double.isInfinite(multiplier)) {
            return -1;
        }
        return (int) Math.round(effectiveBase * multiplier);
    }
}
