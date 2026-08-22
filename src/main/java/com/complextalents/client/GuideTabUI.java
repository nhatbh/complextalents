package com.complextalents.client;

import com.complextalents.client.screen.PlayerProgressionScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class GuideTabUI {

    private final UpgradeCart cart;
    private int selectedPage = 0; // 0=GameMechanics, 1=Stages, 2=Mastery, 3=Refinement, 4=SpellShield, 5=Reactions, 6=Stats
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public GuideTabUI(UpgradeCart cart) {
        this.cart = cart;
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        List<Button> buttons = new ArrayList<>();
        String[] categoryKeys = {
            "guide.complextalents.category.mechanics",
            "guide.complextalents.category.stages",
            "guide.complextalents.category.mastery",
            "guide.complextalents.category.refinement",
            "guide.complextalents.category.spellshield",
            "guide.complextalents.category.reactions",
            "guide.complextalents.category.stats"
        };

        for (int i = 0; i < 7; i++) {
            final int pageIdx = i;
            Button btn = Button.builder(Component.translatable(categoryKeys[i]), (button) -> {
                this.selectedPage = pageIdx;
                this.scrollOffset = 0;
                if (screen instanceof PlayerProgressionScreen pps) {
                    pps.refresh();
                }
            })
            .pos(xOffset + 12, yOffset + 15 + (i * 24))
            .size(110, 20)
            .build();
            btn.active = (pageIdx != selectedPage);
            buttons.add(btn);
        }

        // Up Scroll Button
        buttons.add(Button.builder(Component.literal("▲"), (button) -> scroll(-3))
            .pos(xOffset + 478, yOffset + 12)
            .size(10, 10)
            .build());

        // Down Scroll Button
        buttons.add(Button.builder(Component.literal("▼"), (button) -> scroll(3))
            .pos(xOffset + 478, yOffset + 338)
            .size(10, 10)
            .build());

        return buttons;
    }

    public void update() {
        // Placeholder for consistency with other tabs
    }

    public void mouseScrolled(double delta) {
        // Minecraft mouse wheel scroll delta is usually +1.0 or -1.0
        // We scroll by lines. Reverse delta because scrolling up (positive) should decrease scrollOffset
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.round(delta * 2), maxScroll));
    }

    private void scroll(int amount) {
        scrollOffset = Math.max(0, Math.min(scrollOffset + amount, maxScroll));
    }

    private List<Component> getParagraphsForPage(int page) {
        List<Component> list = new ArrayList<>();
        switch (page) {
            case 0:
                list.add(Component.translatable("guide.complextalents.mechanics.p1"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.mechanics.p2"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.mechanics.p3"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.mechanics.p4"));
                break;
            case 1:
                list.add(Component.translatable("guide.complextalents.stages.p1"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stages.p2"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stages.p3"));
                break;
            case 2:
                list.add(Component.translatable("guide.complextalents.mastery.intro"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.mastery.melee.title"));
                list.add(Component.translatable("guide.complextalents.mastery.melee.bullet1"));
                list.add(Component.translatable("guide.complextalents.mastery.melee.bullet2"));
                list.add(Component.translatable("guide.complextalents.mastery.melee.novice"));
                list.add(Component.translatable("guide.complextalents.mastery.melee.apprentice"));
                list.add(Component.translatable("guide.complextalents.mastery.melee.adept"));
                list.add(Component.translatable("guide.complextalents.mastery.melee.expert"));
                list.add(Component.translatable("guide.complextalents.mastery.melee.master"));
                list.add(Component.translatable("guide.complextalents.mastery.melee.bullet3"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.mastery.gun.title"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.bullet1"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.bullet2"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.bullet3"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.recruit"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.trooper"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.sergeant"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.captain"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.general"));
                list.add(Component.translatable("guide.complextalents.mastery.gun.bullet4"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.mastery.spell.title"));
                list.add(Component.translatable("guide.complextalents.mastery.spell.bullet1"));
                list.add(Component.translatable("guide.complextalents.mastery.spell.bullet2"));
                list.add(Component.translatable("guide.complextalents.mastery.spell.bullet3"));
                list.add(Component.translatable("guide.complextalents.mastery.spell.learn_quick"));
                list.add(Component.translatable("guide.complextalents.mastery.spell.learn_ui"));
                list.add(Component.translatable("guide.complextalents.mastery.spell.bullet4"));
                list.add(Component.translatable("guide.complextalents.mastery.spell.bullet5"));
                break;
            case 3:
                list.add(Component.translatable("guide.complextalents.refinement.intro"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.refinement.melee.title"));
                list.add(Component.translatable("guide.complextalents.refinement.melee.bullet1"));
                list.add(Component.translatable("guide.complextalents.refinement.melee.bullet2"));
                list.add(Component.translatable("guide.complextalents.refinement.melee.bullet3"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.refinement.gun.title"));
                list.add(Component.translatable("guide.complextalents.refinement.gun.bullet1"));
                list.add(Component.translatable("guide.complextalents.refinement.gun.bullet2"));
                list.add(Component.translatable("guide.complextalents.refinement.gun.bullet3"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.refinement.magic.title"));
                list.add(Component.translatable("guide.complextalents.refinement.magic.bullet1"));
                list.add(Component.translatable("guide.complextalents.refinement.magic.bullet2"));
                list.add(Component.translatable("guide.complextalents.refinement.magic.bullet3"));
                break;
            case 4:
                list.add(Component.translatable("guide.complextalents.spellshield.intro"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.spellshield.elemental.title"));
                list.add(Component.translatable("guide.complextalents.spellshield.elemental.bullet1"));
                list.add(Component.translatable("guide.complextalents.spellshield.elemental.counter_matchups"));
                list.add(Component.translatable("guide.complextalents.spellshield.elemental.bullet2"));
                list.add(Component.translatable("guide.complextalents.spellshield.elemental.resist_matchups"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.spellshield.arcane.title"));
                list.add(Component.translatable("guide.complextalents.spellshield.arcane.bullet1"));
                list.add(Component.translatable("guide.complextalents.spellshield.arcane.bullet2"));
                list.add(Component.translatable("guide.complextalents.spellshield.arcane.holy"));
                list.add(Component.translatable("guide.complextalents.spellshield.arcane.evocation"));
                list.add(Component.translatable("guide.complextalents.spellshield.arcane.ender"));
                list.add(Component.translatable("guide.complextalents.spellshield.arcane.eldritch"));
                list.add(Component.translatable("guide.complextalents.spellshield.arcane.blood"));
                break;
            case 5:
                list.add(Component.translatable("guide.complextalents.reactions.intro"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.vaporize"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.melt"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.overload"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.burning"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.freeze"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.superconduct"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.permafrost"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.electrocharged"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.bloom"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.reactions.overgrowth"));
                break;
            case 6:
                list.add(Component.translatable("guide.complextalents.stats.intro"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.flat_ad"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.percent_ad"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.max_hp"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.ap"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.max_mana"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.cdr"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.heal_and_shield"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.summoning_power"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.magic_effectiveness"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.gun_damage"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.reload_speed"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.fortitude"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.headshot_damage"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.recoil_control"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.bullet_penetration"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.fire_rate"));
                list.add(Component.literal(""));
                list.add(Component.translatable("guide.complextalents.stats.luck_crit"));
                break;
        }
        return list;
    }

    private List<FormattedCharSequence> getWrappedLines(Font font, int width) {
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        List<Component> paragraphs = getParagraphsForPage(selectedPage);
        for (Component p : paragraphs) {
            if (p.getString().isEmpty()) {
                wrappedLines.add(FormattedCharSequence.forward("", net.minecraft.network.chat.Style.EMPTY));
            } else {
                wrappedLines.addAll(font.split(p, width));
            }
        }
        return wrappedLines;
    }

    public void render(GuiGraphics g, Font font, int xOffset, int yOffset, int mouseX, int mouseY, float partialTicks) {
        // Draw left side category panel background
        g.fill(xOffset + 10, yOffset + 10, xOffset + 125, yOffset + 350, 0xFF171924);
        g.renderOutline(xOffset + 10, yOffset + 10, 115, 340, 0xFF3D4258);

        // Draw right side content panel background
        int rightX = xOffset + 130;
        g.fill(rightX, yOffset + 10, rightX + 360, yOffset + 350, 0xFF121622);
        g.renderOutline(rightX, yOffset + 10, 360, 340, 0xFF3D4258);

        // Render Title
        String titleKey = "";
        switch (selectedPage) {
            case 0 -> titleKey = "guide.complextalents.mechanics.title";
            case 1 -> titleKey = "guide.complextalents.stages.title";
            case 2 -> titleKey = "guide.complextalents.mastery.title";
            case 3 -> titleKey = "guide.complextalents.refinement.title";
            case 4 -> titleKey = "guide.complextalents.spellshield.title";
            case 5 -> titleKey = "guide.complextalents.reactions.title";
            case 6 -> titleKey = "guide.complextalents.stats.title";
        }
        g.drawString(font, Component.translatable(titleKey), rightX + 10, yOffset + 15, 0xFFFFFF, false);
        g.fill(rightX + 10, yOffset + 27, rightX + 350, yOffset + 28, 0xFF3D4258);

        // Render scrollable wrapped content
        List<FormattedCharSequence> wrappedLines = getWrappedLines(font, 330);
        int visibleLines = 27;
        maxScroll = Math.max(0, wrappedLines.size() - visibleLines);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int startY = yOffset + 35;
        for (int i = 0; i < visibleLines; i++) {
            int lineIdx = i + scrollOffset;
            if (lineIdx >= wrappedLines.size()) break;
            g.drawString(font, wrappedLines.get(lineIdx), rightX + 10, startY + (i * 11), 0xFFFFFF, false);
        }

        // Draw scrollbar track & thumb
        if (maxScroll > 0) {
            int scrollbarHeight = 290;
            int handleHeight = Math.max(15, (visibleLines * scrollbarHeight) / wrappedLines.size());
            int scrollTrackY = yOffset + 32;
            int handleY = scrollTrackY + (scrollOffset * (scrollbarHeight - handleHeight)) / maxScroll;

            // Draw track
            g.fill(rightX + 352, scrollTrackY, rightX + 355, scrollTrackY + scrollbarHeight, 0xFF171924);
            // Draw handle
            g.fill(rightX + 352, handleY, rightX + 355, handleY + handleHeight, 0xFF778899);
        }
    }
}
