package com.complextalents.client.screen;

import com.complextalents.caseopening.CaseReward;
import com.complextalents.caseopening.CaseRewardPool;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.List;

public class CSGOCaseScreen extends Screen {

    private static final int CARD_WIDTH = 110;
    private static final int CARD_HEIGHT = 75;
    private static final int CARD_SPACING = 122; // CARD_WIDTH + 12px spacing
    private static final long SCROLL_DURATION_MS = 8500L; // 8.5 seconds for intense tension

    private final List<CaseReward> sequence;
    private final int winningIndex;
    private final CaseReward winningReward;
    private final List<CaseReward> fullPool;

    private long startTime;
    private boolean showingReveal = false;
    private int lastPlayedTickSlot = -1;
    private float targetScrollPos;
    private Button collectButton;

    public CSGOCaseScreen(List<CaseReward> sequence, int winningIndex, CaseReward winningReward, List<CaseReward> fullPool) {
        super(Component.literal("MYSTERIOUS LOOT UNBOXING"));
        this.sequence = sequence;
        this.winningIndex = winningIndex;
        this.winningReward = winningReward;
        this.fullPool = fullPool != null && !fullPool.isEmpty() ? fullPool : sequence;
    }

    public CSGOCaseScreen(List<CaseReward> sequence, int winningIndex, CaseReward winningReward) {
        this(sequence, winningIndex, winningReward, sequence);
    }

    @Override
    protected void init() {
        super.init();
        this.startTime = System.currentTimeMillis();

        // Full 110px card width spans -55px to +55px from card center.
        // Using [-52px, +52px] (% 105 - 52) covers 104px across the 110px card with 3px inner margin.
        int jitter = (Math.abs(sequence.hashCode() + winningIndex * 31) % 105) - 52;
        this.targetScrollPos = winningIndex * CARD_SPACING + jitter;

        int buttonWidth = 140;
        int buttonHeight = 24;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - 45;

        this.collectButton = this.addRenderableWidget(Button.builder(Component.literal("§lCOLLECT REWARD"), (btn) -> this.onClose())
                .pos(buttonX, buttonY)
                .size(buttonWidth, buttonHeight)
                .build());
        this.collectButton.visible = false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        long elapsed = now - startTime;
        float rawProgress = Math.min(1.0f, (float) elapsed / SCROLL_DURATION_MS);

        // Immediate transition to 3D Reveal as soon as unboxing completes (0ms delay, zero flash/flicker)
        if (rawProgress >= 1.0f && !showingReveal) {
            showingReveal = true;
            collectButton.visible = true;
            float soundPitch = winningReward.getRarity().getSoundPitch();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, soundPitch));
        }

        // If in 3D reveal phase, render 3D item showcase directly in-screen
        if (showingReveal) {
            render3DRevealPhase(guiGraphics, mouseX, mouseY);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        // Higher order ease-out curve (power 4.5) for dramatic deceleration & extreme tension
        float easeOutProgress = 1.0f - (float) Math.pow(1.0 - rawProgress, 4.5);
        float currentScroll = easeOutProgress * targetScrollPos;

        // Sound Ticking Logic: trigger click sound whenever center line crosses item slot boundary
        int centerSlotIndex = (int) Math.floor((currentScroll + (CARD_SPACING / 2.0f)) / CARD_SPACING);
        if (centerSlotIndex != lastPlayedTickSlot && rawProgress < 1.0f) {
            lastPlayedTickSlot = centerSlotIndex;
            float pitch = 0.75f + (rawProgress * 0.55f);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }

        // 1. Render dark translucent background
        guiGraphics.fill(0, 0, this.width, this.height, 0xEE0B0E14);

        // 2. Render Main Case Container Window
        int containerWidth = 520;
        int containerHeight = 155;
        int containerX = (this.width - containerWidth) / 2;
        int containerY = (this.height - 270) / 2;

        guiGraphics.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, 0xFF141824);
        guiGraphics.fill(containerX + 1, containerY + 1, containerX + containerWidth - 1, containerY + containerHeight - 1, 0xFF1B2030);
        guiGraphics.fill(containerX + 4, containerY + 4, containerX + containerWidth - 4, containerY + containerHeight - 4, 0xFF0F121C);

        // 3. Carousel Viewport with Scissor Clipping
        int viewportWidth = 490;
        int viewportHeight = 115;
        int viewportX = containerX + (containerWidth - viewportWidth) / 2;
        int viewportY = containerY + 20;

        guiGraphics.fill(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, 0xFF141722);

        guiGraphics.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);

        int centerX = viewportX + viewportWidth / 2;

        for (int i = 0; i < sequence.size(); i++) {
            CaseReward reward = sequence.get(i);
            int cardX = (int) (centerX - currentScroll + (i * CARD_SPACING) - (CARD_WIDTH / 2));
            int cardY = viewportY + (viewportHeight - CARD_HEIGHT) / 2;

            // Skip rendering if out of viewport bounds
            if (cardX + CARD_WIDTH < viewportX || cardX > viewportX + viewportWidth) {
                continue;
            }

            boolean isSpecial = (reward.getRarity() == com.complextalents.caseopening.CaseRarity.SPECIAL);

            if (isSpecial) {
                renderSpecialGoldenCard(guiGraphics, cardX, cardY, now);
            } else {
                // Normal Card Body
                guiGraphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, 0xFF1E2230);

                // Thinner Rarity Bottom Bar (2px thickness)
                int rarityColor = 0xFF000000 | reward.getRarity().getColorHex();
                guiGraphics.fill(cardX, cardY + CARD_HEIGHT - 2, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, rarityColor);

                // Subtle top border
                guiGraphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + 1, 0xFF353B50);

                // Render Scaled Item Stack Icon or Spell Icon
                ItemStack stack = reward.getStack();
                ResourceLocation spellIcon = getSpellIconIfSpell(stack);
                if (spellIcon != null) {
                    int iconSize = 28;
                    int iconX = cardX + (CARD_WIDTH - iconSize) / 2;
                    int iconY = cardY + (CARD_HEIGHT - iconSize) / 2 - 2;
                    guiGraphics.blit(spellIcon, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
                } else {
                    float itemScale = 1.5f;
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().scale(itemScale, itemScale, itemScale);
                    int scaledItemX = (int) ((cardX + (CARD_WIDTH - 24) / 2) / itemScale);
                    int scaledItemY = (int) ((cardY + (CARD_HEIGHT - 24) / 2) / itemScale);
                    guiGraphics.renderItem(stack, scaledItemX, scaledItemY);
                    guiGraphics.renderItemDecorations(this.font, stack, scaledItemX, scaledItemY);
                    guiGraphics.pose().popPose();
                }
            }
        }

        guiGraphics.disableScissor();

        // 4. Center Selector Needle / Line (Slim 1px line without arrows)
        int needleX = centerX;
        int needleTop = viewportY;
        int needleBottom = viewportY + viewportHeight;
        guiGraphics.fill(needleX, needleTop, needleX + 1, needleBottom, 0xFFFFD700);

        // 5. Unboxing Status
        int resultY = containerY + containerHeight + 12;
        guiGraphics.drawCenteredString(this.font, "§7Opening Mysterious Loot...", this.width / 2, resultY, 0xAAAAAA);

        // 6. Bottom Preview Grid (All Possible Container Items with Rates)
        renderPossibleRewardsGrid(guiGraphics, mouseX, mouseY, containerY + containerHeight + 45);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void render3DRevealPhase(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 1. Dark translucent background
        guiGraphics.fill(0, 0, this.width, this.height, 0xF50A0C12);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 2. Rarity background glow rect behind item
        int rarityColorHex = winningReward.getRarity().getColorHex();
        int glowBgColor = (0x33000000) | (rarityColorHex & 0x00FFFFFF);
        guiGraphics.fill(centerX - 90, centerY - 100, centerX + 90, centerY + 20, glowBgColor);

        // Decorative borders
        int borderColor = (0xFF000000) | rarityColorHex;
        guiGraphics.fill(centerX - 90, centerY - 100, centerX + 90, centerY - 98, borderColor);
        guiGraphics.fill(centerX - 90, centerY + 18, centerX + 90, centerY + 20, borderColor);

        // 3. Render Spell Icon or 3D Item Model
        ResourceLocation spellIcon = getSpellIconIfSpell(winningReward.getStack());
        if (spellIcon != null) {
            int iconSize = 64;
            int iconX = centerX - iconSize / 2;
            int iconY = centerY - 40 - iconSize / 2;
            guiGraphics.blit(spellIcon, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        } else {
            render3DItem(guiGraphics, winningReward.getStack(), centerX, centerY - 40);
        }

        // 4. Item Info Text (Header text removed)
        Component itemName = Component.literal("").append(winningReward.getDisplayName())
                .withStyle(style -> style.withColor(winningReward.getRarity().getColorHex()).withBold(true));
        guiGraphics.drawCenteredString(this.font, itemName, centerX, centerY + 32, 0xFFFFFF);

        Component rarityComponent = winningReward.getRarity().getFormattedComponent();
        guiGraphics.drawCenteredString(this.font, rarityComponent, centerX, centerY + 48, 0xAAAAAA);

        if (winningReward.getStack().getCount() > 1) {
            guiGraphics.drawCenteredString(this.font, "§7Quantity: §f" + winningReward.getStack().getCount(), centerX, centerY + 62, 0xDDDDDD);
        }
    }

    private void render3DItem(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        // Move pose stack to target center location
        poseStack.translate(x, y, 250.0f);

        // Scale item to 3D showcase size (75x)
        poseStack.scale(75.0f, -75.0f, 75.0f);

        // Calculate smooth continuous 3D Y-axis rotation
        long timeElapsed = System.currentTimeMillis() - startTime;
        float rotY = (timeElapsed % 4000L) / 4000.0f * 360.0f;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
        poseStack.mulPose(Axis.XP.rotationDegrees(12.0f)); // Slight tilt towards camera

        // Setup 3D lighting for item rendering
        Lighting.setupForEntityInInventory();

        // Render static item model
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.GUI,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                Minecraft.getInstance().level,
                0
        );
        bufferSource.endBatch();
        Lighting.setupFor3DItems();

        poseStack.popPose();
    }

    private List<CaseReward> getDistinctRewardsFromSequence() {
        if (sequence == null || sequence.isEmpty()) {
            return CaseRewardPool.getAllRewards();
        }
        Map<String, CaseReward> uniqueMap = new LinkedHashMap<>();
        for (CaseReward r : sequence) {
            String key = r.getStack().getItem().toString() + "_" + r.getRarity().name() + "_" + r.getWeight();
            uniqueMap.putIfAbsent(key, r);
        }
        return new ArrayList<>(uniqueMap.values());
    }

    private void renderPossibleRewardsGrid(GuiGraphics guiGraphics, int mouseX, int mouseY, int startY) {
        List<CaseReward> poolToUse = (fullPool != null && !fullPool.isEmpty()) ? fullPool : getDistinctRewardsFromSequence();
        int totalWeight = poolToUse.stream().mapToInt(CaseReward::getWeight).sum();
        if (totalWeight <= 0) return;

        List<GridEntry> entries = new ArrayList<>();

        // Tiers 1-4 (Common, Uncommon, Rare, Epic) Summaries
        com.complextalents.caseopening.CaseRarity[] lowerTiers = new com.complextalents.caseopening.CaseRarity[]{
                com.complextalents.caseopening.CaseRarity.MIL_SPEC,    // Tier 1 Common (Blue)
                com.complextalents.caseopening.CaseRarity.RESTRICTED,  // Tier 2 Uncommon (Purple)
                com.complextalents.caseopening.CaseRarity.CLASSIFIED,  // Tier 3 Rare (Pink)
                com.complextalents.caseopening.CaseRarity.COVERT       // Tier 4 Epic (Red)
        };

        for (com.complextalents.caseopening.CaseRarity rarity : lowerTiers) {
            int tierWeight = poolToUse.stream()
                    .filter(r -> r.getRarity() == rarity)
                    .mapToInt(CaseReward::getWeight)
                    .sum();

            if (tierWeight > 0) {
                double dropRate = (double) tierWeight / totalWeight * 100.0;
                entries.add(new GridEntry(rarity, null, dropRate, true));
            }
        }

        // Tier 5: Every individual Legendary / Special item
        for (CaseReward reward : poolToUse) {
            if (reward.getRarity() == com.complextalents.caseopening.CaseRarity.SPECIAL) {
                double dropRate = (double) reward.getWeight() / totalWeight * 100.0;
                entries.add(new GridEntry(com.complextalents.caseopening.CaseRarity.SPECIAL, reward, dropRate, false));
            }
        }

        if (entries.isEmpty()) return;

        int itemSlotSize = 32;
        int slotSpacing = 36;
        int rowHeight = 44;
        int gridCols = Math.min(8, Math.max(1, entries.size()));
        int gridWidth = gridCols * slotSpacing;
        int gridStartX = (this.width - gridWidth) / 2;
        int gridStartY = startY;

        for (int i = 0; i < entries.size(); i++) {
            GridEntry entry = entries.get(i);
            int col = i % gridCols;
            int row = i / gridCols;

            int slotX = gridStartX + col * slotSpacing;
            int slotY = gridStartY + row * rowHeight;

            int rarityColor = 0xFF000000 | entry.rarity.getColorHex();

            if (entry.isTierSummary) {
                // Tier Summary Box (Common - Epic): Colored box with star in the middle
                int bgColor = (0x99000000) | (entry.rarity.getColorHex() & 0x00FFFFFF);
                guiGraphics.fill(slotX, slotY, slotX + itemSlotSize, slotY + itemSlotSize, bgColor);

                // Bright 2px rarity border around box
                guiGraphics.fill(slotX, slotY, slotX + itemSlotSize, slotY + 2, rarityColor);
                guiGraphics.fill(slotX, slotY + itemSlotSize - 2, slotX + itemSlotSize, slotY + itemSlotSize, rarityColor);
                guiGraphics.fill(slotX, slotY, slotX + 2, slotY + itemSlotSize, rarityColor);
                guiGraphics.fill(slotX + itemSlotSize - 2, slotY, slotX + itemSlotSize, slotY + itemSlotSize, rarityColor);

                // Centered Star Emblem inside box
                guiGraphics.drawCenteredString(this.font, "§6★", slotX + itemSlotSize / 2, slotY + (itemSlotSize - 8) / 2, 0xFFFFD700);

                // Percentage text below box
                String rateText = String.format(java.util.Locale.ROOT, "%.1f%%", entry.dropRate);
                guiGraphics.drawCenteredString(this.font, "§7" + rateText, slotX + itemSlotSize / 2, slotY + itemSlotSize + 2, 0xAAAAAA);

                // Tooltip on Hover
                if (mouseX >= slotX && mouseX <= slotX + itemSlotSize && mouseY >= slotY && mouseY <= slotY + itemSlotSize) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(entry.rarity.getFormattedComponent().copy().append(Component.literal(" Pool")));
                    tooltip.add(Component.literal("§7Total Tier Drop Rate: §a" + String.format(java.util.Locale.ROOT, "%.1f%%", entry.dropRate)));
                    tooltip.add(Component.literal("§8Contains all " + entry.rarity.getDisplayName() + " items"));
                    guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                }
            } else {
                // Individual Legendary Reward Slot
                CaseReward reward = entry.reward;
                guiGraphics.fill(slotX, slotY, slotX + itemSlotSize, slotY + itemSlotSize, 0xFF141824);
                guiGraphics.fill(slotX, slotY + itemSlotSize - 3, slotX + itemSlotSize, slotY + itemSlotSize, rarityColor);

                // Render Item Stack or Spell Icon
                ResourceLocation spellIcon = getSpellIconIfSpell(reward.getStack());
                if (spellIcon != null) {
                    guiGraphics.blit(spellIcon, slotX + 8, slotY + 6, 0, 0, 16, 16, 16, 16);
                } else {
                    guiGraphics.renderItem(reward.getStack(), slotX + 8, slotY + 6);
                }

                // Individual Drop Rate Percentage Text below slot
                String rateText = String.format(java.util.Locale.ROOT, "%.1f%%", entry.dropRate);
                guiGraphics.drawCenteredString(this.font, "§e" + rateText, slotX + itemSlotSize / 2, slotY + itemSlotSize + 2, 0xFFFFD700);

                // Tooltip on Hover
                if (mouseX >= slotX && mouseX <= slotX + itemSlotSize && mouseY >= slotY && mouseY <= slotY + itemSlotSize) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(reward.getDisplayName());
                    tooltip.add(reward.getRarity().getFormattedComponent());
                    tooltip.add(Component.literal("§7Drop Chance: §a" + String.format(java.util.Locale.ROOT, "%.2f%%", entry.dropRate)));
                    guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                }
            }
        }
    }

    private static class GridEntry {
        final com.complextalents.caseopening.CaseRarity rarity;
        final CaseReward reward;
        final double dropRate;
        final boolean isTierSummary;

        GridEntry(com.complextalents.caseopening.CaseRarity rarity, CaseReward reward, double dropRate, boolean isTierSummary) {
            this.rarity = rarity;
            this.reward = reward;
            this.dropRate = dropRate;
            this.isTierSummary = isTierSummary;
        }
    }

    private static ResourceLocation getSpellIconIfSpell(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return null;
        try {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            ResourceLocation spellId = null;
            if (tag.contains("SpellId")) {
                spellId = ResourceLocation.tryParse(tag.getString("SpellId"));
            } else if (tag.contains("ISB_Spells")) {
                net.minecraft.nbt.CompoundTag isbSpells = tag.getCompound("ISB_Spells");
                if (isbSpells.contains("data")) {
                    net.minecraft.nbt.ListTag dataList = isbSpells.getList("data", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    if (!dataList.isEmpty()) {
                        net.minecraft.nbt.CompoundTag spellEntry = dataList.getCompound(0);
                        if (spellEntry.contains("id")) {
                            spellId = ResourceLocation.tryParse(spellEntry.getString("id"));
                        }
                    }
                }
            }

            if (spellId != null) {
                io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);
                if (spell != null && spell != io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) {
                    return spell.getSpellIconResource();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void renderSpecialGoldenCard(GuiGraphics g, int x, int y, long now) {
        int w = CARD_WIDTH;
        int h = CARD_HEIGHT;

        // === Layer 1: Layered gradient background (dark gold core → warm brown edge) ===
        g.fill(x, y, x + w, y + h, 0xFF2A2008);         // Outermost dark
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF3D2E0A);  // Mid layer
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF4A380F);  // Inner warm

        // === Layer 2: Outer gold border (2px) ===
        g.fill(x, y, x + w, y + 2, 0xFFFFD700);            // Top
        g.fill(x, y + h - 2, x + w, y + h, 0xFFFFD700);    // Bottom
        g.fill(x, y, x + 2, y + h, 0xFFFFD700);            // Left
        g.fill(x + w - 2, y, x + w, y + h, 0xFFFFD700);    // Right

        // === Layer 3: Inner inset border (1px, darker gold) ===
        int inset = 5;
        int darkGold = 0xFFC8A800;
        g.fill(x + inset, y + inset, x + w - inset, y + inset + 1, darkGold);        // Top
        g.fill(x + inset, y + h - inset - 1, x + w - inset, y + h - inset, darkGold); // Bottom
        g.fill(x + inset, y + inset, x + inset + 1, y + h - inset, darkGold);        // Left
        g.fill(x + w - inset - 1, y + inset, x + w - inset, y + h - inset, darkGold); // Right

        // === Layer 4: Corner bracket flourishes (L-shaped ornaments at each corner) ===
        int bracketLen = 12;
        int bracketThick = 1;
        int cOff = 7; // corner offset from card edge
        int brightGold = 0xFFFFE55C;

        // Top-Left corner
        g.fill(x + cOff, y + cOff, x + cOff + bracketLen, y + cOff + bracketThick, brightGold);
        g.fill(x + cOff, y + cOff, x + cOff + bracketThick, y + cOff + bracketLen, brightGold);
        // Top-Right corner
        g.fill(x + w - cOff - bracketLen, y + cOff, x + w - cOff, y + cOff + bracketThick, brightGold);
        g.fill(x + w - cOff - bracketThick, y + cOff, x + w - cOff, y + cOff + bracketLen, brightGold);
        // Bottom-Left corner
        g.fill(x + cOff, y + h - cOff - bracketThick, x + cOff + bracketLen, y + h - cOff, brightGold);
        g.fill(x + cOff, y + h - cOff - bracketLen, x + cOff + bracketThick, y + h - cOff, brightGold);
        // Bottom-Right corner
        g.fill(x + w - cOff - bracketLen, y + h - cOff - bracketThick, x + w - cOff, y + h - cOff, brightGold);
        g.fill(x + w - cOff - bracketThick, y + h - cOff - bracketLen, x + w - cOff, y + h - cOff, brightGold);

        // === Layer 5: Horizontal filigree accent lines (left and right of center) ===
        int midY = y + h / 2;
        int lineGold = 0xFFB89B00;
        // Left filigree
        g.fill(x + 8, midY, x + w / 2 - 20, midY + 1, lineGold);
        // Right filigree
        g.fill(x + w / 2 + 20, midY, x + w - 8, midY + 1, lineGold);
        // Secondary shorter lines above and below
        g.fill(x + 14, midY - 6, x + w / 2 - 24, midY - 5, 0xFF8B7500);
        g.fill(x + w / 2 + 24, midY - 6, x + w - 14, midY - 5, 0xFF8B7500);
        g.fill(x + 14, midY + 6, x + w / 2 - 24, midY + 7, 0xFF8B7500);
        g.fill(x + w / 2 + 24, midY + 6, x + w - 14, midY + 7, 0xFF8B7500);

        // === Layer 6: Diamond-shaped central emblem with concentric outlines ===
        int cx = x + w / 2;
        int cy = y + h / 2;

        // Outer diamond outline (rotated square via pixel fills)
        int outerR = 18;
        for (int dy = -outerR; dy <= outerR; dy++) {
            int halfW = outerR - Math.abs(dy);
            // Just the outline pixels (1px thick border of diamond)
            if (Math.abs(dy) == outerR) {
                g.fill(cx, cy + dy, cx + 1, cy + dy + 1, 0xFFFFD700);
            } else {
                g.fill(cx - halfW, cy + dy, cx - halfW + 1, cy + dy + 1, 0xFFFFD700);
                g.fill(cx + halfW, cy + dy, cx + halfW + 1, cy + dy + 1, 0xFFFFD700);
            }
        }

        // Inner diamond fill (smaller, solid dark gold)
        int innerR = 14;
        for (int dy = -innerR; dy <= innerR; dy++) {
            int halfW = innerR - Math.abs(dy);
            g.fill(cx - halfW, cy + dy, cx + halfW + 1, cy + dy + 1, 0xFF5C4A10);
        }

        // Inner diamond border ring
        int midR = 14;
        for (int dy = -midR; dy <= midR; dy++) {
            int halfW = midR - Math.abs(dy);
            if (Math.abs(dy) == midR) {
                g.fill(cx, cy + dy, cx + 1, cy + dy + 1, 0xFFC8A800);
            } else {
                g.fill(cx - halfW, cy + dy, cx - halfW + 1, cy + dy + 1, 0xFFC8A800);
                g.fill(cx + halfW, cy + dy, cx + halfW + 1, cy + dy + 1, 0xFFC8A800);
            }
        }

        // === Layer 7: Pixel-drawn 5-pointed star emblem ===
        drawPixelStar(g, cx, cy, 8, 3, 0xFFFFD700);
        drawPixelStar(g, cx, cy, 6, 2, 0xFFFFE55C); // Brighter inner highlight star

        // === Layer 8: Animated diagonal shimmer highlight sweeping across card ===
        float shimmerCycle = ((now % 2000L) / 2000.0f); // 2-second cycle
        int shimmerWidth = 6;
        // The shimmer travels across a wider range to account for the diagonal offset
        int totalTravel = w + h;
        int shimmerBase = (int) (shimmerCycle * (totalTravel + shimmerWidth)) - shimmerWidth;

        for (int row = y + 3; row < y + h - 3; row++) {
            // Offset the shimmer x position based on row to create diagonal angle
            int rowOffset = row - y;
            int sLeft = x + shimmerBase - rowOffset + 2;
            int sRight = sLeft + shimmerWidth;

            // Clamp within card bounds
            sLeft = Math.max(sLeft, x + 2);
            sRight = Math.min(sRight, x + w - 2);

            if (sLeft < sRight) {
                g.fill(sLeft, row, sRight, row + 1, 0x30FFFFFF);
            }
        }
    }
    /**
     * Draws a filled 5-pointed star using even-odd scanline polygon rasterization.
     * Correctly handles concave star shapes by sorting intersections and filling between pairs.
     */
    private void drawPixelStar(GuiGraphics g, int cx, int cy, int outerR, int innerR, int color) {
        // Compute 10 vertices of the star (5 outer tips + 5 inner notches)
        double[] vx = new double[10];
        double[] vy = new double[10];
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            double r = (i % 2 == 0) ? outerR : innerR;
            vx[i] = cx + r * Math.cos(angle);
            vy[i] = cy + r * Math.sin(angle);
        }

        int minY = cy - outerR - 1;
        int maxY = cy + outerR + 1;

        // Even-odd rule scanline fill
        for (int row = minY; row <= maxY; row++) {
            // Collect all x-intersections for this scanline
            java.util.List<Double> intersections = new java.util.ArrayList<>();

            for (int e = 0; e < 10; e++) {
                int next = (e + 1) % 10;
                double y0 = vy[e], y1 = vy[next];
                double x0 = vx[e], x1 = vx[next];

                if ((y0 <= row && y1 > row) || (y1 <= row && y0 > row)) {
                    double t = (row - y0) / (y1 - y0);
                    intersections.add(x0 + t * (x1 - x0));
                }
            }

            // Sort intersections left to right
            java.util.Collections.sort(intersections);

            // Fill between consecutive pairs (even-odd rule)
            for (int p = 0; p + 1 < intersections.size(); p += 2) {
                int left = (int) Math.round(intersections.get(p));
                int right = (int) Math.round(intersections.get(p + 1));
                if (right > left) {
                    g.fill(left, row, right + 1, row + 1, color);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
