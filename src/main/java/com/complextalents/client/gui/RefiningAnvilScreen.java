package com.complextalents.client.gui;

import com.complextalents.TalentsMod;
import com.complextalents.menu.RefiningAnvilMenu;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.ServerboundAutoFillRefinementPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class RefiningAnvilScreen extends AbstractContainerScreen<RefiningAnvilMenu> {
    private static final ResourceLocation ANVIL_GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "textures/gui/container/refining_anvil.png");
    private static final ResourceLocation VANILLA_ANVIL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/anvil.png");

    public RefiningAnvilScreen(RefiningAnvilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 184;
        this.inventoryLabelY = 90;
    }

    @Override
    protected void init() {
        super.init();

        // Minus (-) Button on left of XP bar
        this.addRenderableWidget(Button.builder(Component.literal("-"), button -> {
            int currentLvl = this.menu.getCurrentLevel();
            int curTargetLvl = this.menu.getCalculatedTargetLevel();
            int baseTarget = curTargetLvl > 0 ? curTargetLvl : currentLvl + 1;
            int nextTarget = Math.max(currentLvl, baseTarget - 1);
            PacketHandler.sendToServer(new ServerboundAutoFillRefinementPacket(nextTarget));
        }).bounds(this.leftPos + 8, this.topPos + 83, 12, 12).build());

        // Plus (+) Button on right of XP bar
        this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            int currentLvl = this.menu.getCurrentLevel();
            int curTargetLvl = this.menu.getCalculatedTargetLevel();
            int baseTarget = curTargetLvl > 0 ? curTargetLvl : currentLvl;
            int nextTarget = Math.min(20, baseTarget + 1);
            PacketHandler.sendToServer(new ServerboundAutoFillRefinementPacket(nextTarget));
        }).bounds(this.leftPos + 156, this.topPos + 83, 12, 12).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        int currentBaseXp = this.menu.getCurrentBaseXp();
        int maxXp = this.menu.getMaxXp();
        int totalXpGained = this.menu.getCalculatedTotalXpGained();
        int currentLevel = this.menu.getCurrentLevel();
        int targetLevel = this.menu.getCalculatedTargetLevel();

        if (maxXp > 0) {
            int barX = 23;
            int barY = 85;
            int barWidth = 130;
            int barHeight = 8;

            // 1. Draw XP Bar Background
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF181818);

            // 2. Draw Current Base XP Fill (Cyan)
            float baseRatio = Math.min(1.0F, (float) currentBaseXp / (float) maxXp);
            int baseFillWidth = Math.round(barWidth * baseRatio);
            if (baseFillWidth > 0) {
                graphics.fill(barX, barY, barX + baseFillWidth, barY + barHeight, 0xFF2575FC);
            }

            // 3. Draw Preview Gained XP Fill (Emerald Green)
            if (totalXpGained > 0) {
                float totalRatio = Math.min(1.0F, (float) (currentBaseXp + totalXpGained) / (float) maxXp);
                int totalFillWidth = Math.round(barWidth * totalRatio);
                if (totalFillWidth > baseFillWidth) {
                    graphics.fill(barX + baseFillWidth, barY, barX + totalFillWidth, barY + barHeight, 0xFF00FF88);
                }
            }

            // 4. Draw Bar Outline
            graphics.renderOutline(barX - 1, barY - 1, barWidth + 2, barHeight + 2, 0xFF444444);

            // 5. Render Level Labels
            String leftLvlText = "Lv." + currentLevel;
            graphics.drawString(this.font, leftLvlText, barX + 2, barY, 0xFFFFFF, true);

            int displayTargetLvl = targetLevel > 0 ? targetLevel : currentLevel;
            String rightLvlText = displayTargetLvl >= 20 ? "MAX" : "Lv." + displayTargetLvl;
            int rightWidth = this.font.width(rightLvlText);
            int rightColor = targetLevel > currentLevel ? 0x55FF55 : 0xAAAAAA;
            graphics.drawString(this.font, rightLvlText, barX + barWidth - rightWidth - 2, barY, rightColor, true);

            String midXpText;
            if (totalXpGained > 0) {
                midXpText = String.format("%,d (+%,d)", currentBaseXp + totalXpGained, totalXpGained);
            } else {
                midXpText = String.format("%,d / %,d", currentBaseXp, maxXp);
            }

            int midWidth = this.font.width(midXpText);
            int midX = barX + (barWidth - midWidth) / 2;
            graphics.drawString(this.font, midXpText, midX, barY - 10, 0x404040, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // 1. Render default player inventory & hotbar background at Y + 100
        graphics.blit(VANILLA_ANVIL_TEXTURE, relX, relY + 100, 0, 82, this.imageWidth, 84);

        // 2. Render top container panel (176 x 100 height)
        graphics.blit(ANVIL_GUI_TEXTURE, relX, relY, 0, 0, this.imageWidth, 100);

        // 3. Dynamically render standard vanilla 18x18 slot background for every container slot (Slots 0..10)
        for (int i = 0; i <= 10; i++) {
            Slot slot = this.menu.slots.get(i);
            graphics.blit(VANILLA_ANVIL_TEXTURE, relX + slot.x - 1, relY + slot.y - 1, 7, 83, 18, 18);
        }
    }
}
