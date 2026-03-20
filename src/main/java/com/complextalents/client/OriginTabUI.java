package com.complextalents.client;

import com.complextalents.origin.Origin;
import com.complextalents.origin.OriginManager;
import com.complextalents.origin.OriginRegistry;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.skill.Skill;
import com.complextalents.skill.SkillRegistry;
import com.complextalents.stats.ScaledStat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OriginTabUI {
    private final UpgradeCart cart;

    public OriginTabUI(UpgradeCart cart) {
        this.cart = cart;
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        List<Button> buttons = new ArrayList<>();
        update();

        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) {
            return buttons;
        }

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) {
            return buttons;
        }

        // Origin upgrade button
        int originTarget = ClientOriginData.getOriginLevel() + getOriginPending();
        int nextOriginCost = OriginManager.getCostForNextLevel(originTarget);

        Button upgOriginBtn;
        if (originTarget >= origin.getMaxLevel()) {
            upgOriginBtn = Button.builder(Component.literal("MAX RANK ATTAINED"),
                    (btn) -> {})
                    .pos(xOffset + 10, yOffset + 140)
                    .size(220, 20)
                    .build();
            upgOriginBtn.active = false;
        } else if (cart.canAfford(nextOriginCost)) {
            upgOriginBtn = Button.builder(Component.literal("Add Rank (Cost: " + nextOriginCost + ")"),
                    (btn) -> adjustOrigin(1))
                    .pos(xOffset + 10, yOffset + 140)
                    .size(220, 20)
                    .build();
        } else {
            upgOriginBtn = Button.builder(Component.literal("Insufficient SP"),
                    (btn) -> {})
                    .pos(xOffset + 10, yOffset + 140)
                    .size(220, 20)
                    .build();
            upgOriginBtn.active = false;
        }
        buttons.add(upgOriginBtn);

        // Cancel origin button
        if (getOriginPending() > 0) {
            Button cancelOrigin = Button.builder(Component.literal("Cancel Upgrade(s)"),
                    (btn) -> adjustOrigin(-1))
                    .pos(xOffset + 10, yOffset + 165)
                    .size(220, 16)
                    .build();
            buttons.add(cancelOrigin);
        }

        // Skill upgrade button
        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                int skillTarget = getActiveSkillLevel() + getSkillPending();
                int nextSkillCost = OriginManager.getSkillCostForNextLevel(skillTarget);

                Button upgSkillBtn;
                if (skillTarget >= skill.getMaxLevel()) {
                    upgSkillBtn = Button.builder(Component.literal("SKILL FULLY MASTERED"),
                            (btn) -> {})
                            .pos(xOffset + 260, yOffset + 140)
                            .size(220, 20)
                            .build();
                    upgSkillBtn.active = false;
                } else if (cart.canAfford(nextSkillCost)) {
                    upgSkillBtn = Button.builder(Component.literal("Add Rank (Cost: " + nextSkillCost + ")"),
                            (btn) -> adjustSkill(1))
                            .pos(xOffset + 260, yOffset + 140)
                            .size(220, 20)
                            .build();
                } else {
                    upgSkillBtn = Button.builder(Component.literal("Insufficient SP"),
                            (btn) -> {})
                            .pos(xOffset + 260, yOffset + 140)
                            .size(220, 20)
                            .build();
                    upgSkillBtn.active = false;
                }
                buttons.add(upgSkillBtn);

                // Cancel skill button
                if (getSkillPending() > 0) {
                    Button cancelSkill = Button.builder(Component.literal("Cancel Upgrade(s)"),
                            (btn) -> adjustSkill(-1))
                            .pos(xOffset + 260, yOffset + 165)
                            .size(220, 16)
                            .build();
                    buttons.add(cancelSkill);
                }
            }
        }

        return buttons;
    }

    public void update() {
        // Update is handled by parent screen via cart callback
    }

    public void renderBackgrounds(GuiGraphics guiGraphics, int xOffset, int yOffset, int mouseX, int mouseY, float partialTick) {
        // Draw origin area background
        guiGraphics.fill(xOffset + 10, yOffset + 10, xOffset + 240, yOffset + 350, 0xFF333333);
        guiGraphics.fill(xOffset + 11, yOffset + 11, xOffset + 239, yOffset + 349, 0xFF222222);

        // Draw skill area background
        guiGraphics.fill(xOffset + 260, yOffset + 10, xOffset + 490, yOffset + 350, 0xFF333333);
        guiGraphics.fill(xOffset + 261, yOffset + 11, xOffset + 489, yOffset + 349, 0xFF222222);
    }

    public void renderLabels(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int xOffset, int yOffset, int mouseX, int mouseY) {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) {
            guiGraphics.drawString(font, "You do not have an origin selected.", xOffset + 10, yOffset + 10, 0xFFCC0000, false);
            return;
        }

        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) return;

        // Origin Area
        int originX = xOffset + 15;
        int originY = yOffset + 15;

        // Origin name
        String originName = "§6§l" + origin.getDisplayName().getString().toUpperCase();
        guiGraphics.drawString(font, originName, originX, originY, 0xFFFFAA00, false);

        // Origin description (wrapped)
        originY += 15;
        String originDesc = origin.getDescription().getString();
        if (originDesc.length() > 40) {
            originDesc = originDesc.substring(0, 40) + "...";
        }
        guiGraphics.drawString(font, originDesc, originX, originY, 0xFF888888, false);

        // Scaling table header for origin
        originY += 50;
        for (int i = 1; i <= 5; i++) {
            int current = ClientOriginData.getOriginLevel() + getOriginPending();
            String color = (i == current) ? "§e" : "§7";
            guiGraphics.drawString(font, color + "L" + i, originX + 75 + (i - 1) * 28, originY, 0xFFFFFF, false);
        }

        // Scaling table rows for origin
        Map<String, ScaledStat> originStats = new HashMap<>(origin.getScaledStats());
        originY += 12;
        int row = 0;
        String[] priority = {"Cooldown", "Cost"};
        for (String key : priority) {
            if (originStats.containsKey(key) && row < 4) {
                renderTableRow(guiGraphics, font, originX, originY + row * 11, originStats.get(key), true);
                row++;
            }
        }
        for (var entry : originStats.entrySet()) {
            if (row >= 4) break;
            if (entry.getKey().equals("Cooldown") || entry.getKey().equals("Cost")) continue;
            renderTableRow(guiGraphics, font, originX, originY + row * 11, entry.getValue(), true);
            row++;
        }

        // Skill Area
        ResourceLocation activeSkillId = origin.getActiveSkillId();
        if (activeSkillId != null) {
            Skill skill = SkillRegistry.getInstance().getSkill(activeSkillId);
            if (skill != null) {
                int skillX = xOffset + 265;
                int skillY = yOffset + 15;

                // Skill name
                String skillName = "§d§l" + skill.getDisplayName().getString().toUpperCase();
                guiGraphics.drawString(font, skillName, skillX, skillY, 0xFFFF00FF, false);

                // Skill description
                skillY += 15;
                String skillDesc = skill.getDescription().getString();
                if (skillDesc.length() > 40) {
                    skillDesc = skillDesc.substring(0, 40) + "...";
                }
                guiGraphics.drawString(font, skillDesc, skillX, skillY, 0xFF888888, false);

                // Scaling table header for skill
                skillY += 50;
                for (int i = 1; i <= 5; i++) {
                    int current = getActiveSkillLevel() + getSkillPending();
                    String color = (i == current) ? "§e" : "§7";
                    guiGraphics.drawString(font, color + "L" + i, skillX + 75 + (i - 1) * 28, skillY, 0xFFFFFF, false);
                }

                // Scaling table rows for skill
                Map<String, ScaledStat> skillStats = new HashMap<>();
                double[] cds = new double[5];
                double[] costs = new double[5];
                for (int i = 1; i <= 5; i++) {
                    cds[i - 1] = skill.getActiveCooldown(i);
                    costs[i - 1] = skill.getResourceCost(i);
                }
                if (cds[0] > 0 || cds[4] > 0) skillStats.put("Cooldown", new ScaledStat("Cooldown", cds));
                if (skill.getResourceType() != null) skillStats.put("Cost", new ScaledStat("Cost", costs));
                skillStats.putAll(skill.getScaledStats());

                skillY += 12;
                row = 0;
                for (String key : priority) {
                    if (skillStats.containsKey(key) && row < 4) {
                        renderTableRow(guiGraphics, font, skillX, skillY + row * 11, skillStats.get(key), false);
                        row++;
                    }
                }
                for (var entry : skillStats.entrySet()) {
                    if (row >= 4) break;
                    if (entry.getKey().equals("Cooldown") || entry.getKey().equals("Cost")) continue;
                    renderTableRow(guiGraphics, font, skillX, skillY + row * 11, entry.getValue(), false);
                    row++;
                }
            }
        }
    }

    private void renderTableRow(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int x, int y, ScaledStat stat, boolean isOrigin) {
        String label = stat.displayName().getString();
        if (label.length() > 12) label = label.substring(0, 10) + "..";
        guiGraphics.drawString(font, "§8" + label, x, y, 0xFF888888, false);

        double[] values = stat.values();
        for (int i = 1; i <= 5; i++) {
            int current = isOrigin ? ClientOriginData.getOriginLevel() + getOriginPending() : getActiveSkillLevel() + getSkillPending();
            String color = (i == current) ? "§f" : "§7";
            double v = values[Math.min(i - 1, values.length - 1)];
            String formattedVal = formatValue(v);
            guiGraphics.drawString(font, color + formattedVal, x + 75 + (i - 1) * 28, y, 0xFFFFFF, false);
        }
    }

    public void mouseScrolled(double scrollDelta) {
        // No scrolling needed for this tab
    }

    private String formatValue(double v) {
        if (v == (long) v) return String.format("%d", (long) v);
        if (v < 0.1) return String.format("%.3f", v);
        if (v < 1.0) return String.format("%.2f", v);
        return String.format("%.1f", v);
    }

    private int getOriginPending() {
        return cart.getAmount(UpgradeType.ORIGIN, "origin");
    }

    private int getSkillPending() {
        return cart.getAmount(UpgradeType.SKILL, "skill");
    }

    private void adjustOrigin(int delta) {
        int current = getOriginPending();
        int next = current + delta;
        if (next < 0 || (ClientOriginData.getOriginLevel() + next) > 5) return;

        if (delta > 0) {
            int lvl = ClientOriginData.getOriginLevel() + current;
            int cost = OriginManager.getCostForNextLevel(lvl);
            if (cart.canAfford(cost)) {
                cart.modifyItem(UpgradeType.ORIGIN, "origin", 1, cost);
            }
        } else if (delta < 0) {
            int lvl = ClientOriginData.getOriginLevel() + current - 1;
            int cost = OriginManager.getCostForNextLevel(lvl);
            cart.modifyItem(UpgradeType.ORIGIN, "origin", -1, -cost);
        }
    }

    private void adjustSkill(int delta) {
        int current = getSkillPending();
        int next = current + delta;
        int currentLevel = getActiveSkillLevel();
        if (next < 0 || (currentLevel + next) > 5) return;

        if (delta > 0) {
            int lvl = currentLevel + current;
            int cost = OriginManager.getSkillCostForNextLevel(lvl);
            if (cart.canAfford(cost)) {
                cart.modifyItem(UpgradeType.SKILL, "skill", 1, cost);
            }
        } else if (delta < 0) {
            int lvl = currentLevel + current - 1;
            int cost = OriginManager.getSkillCostForNextLevel(lvl);
            cart.modifyItem(UpgradeType.SKILL, "skill", -1, -cost);
        }
    }

    private int getActiveSkillLevel() {
        ResourceLocation originId = ClientOriginData.getOriginId();
        if (originId == null) return 0;
        Origin origin = OriginRegistry.getInstance().getOrigin(originId);
        if (origin == null) return 0;
        ResourceLocation skillId = origin.getActiveSkillId();
        if (skillId == null) return 0;
        return cart.getPlayer().getCapability(com.complextalents.skill.capability.SkillDataProvider.SKILL_DATA)
                .map(data -> data.getSkillLevel(skillId))
                .orElse(0);
    }
}
