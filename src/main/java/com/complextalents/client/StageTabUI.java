package com.complextalents.client;

import com.nhatbh.basedefensev2.api.StageAPI;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.skills.ActiveSkill;
import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.elemental.MobElementConfig;
import com.nhatbh.basedefensev2.level.MobLevelConfig;
import com.nhatbh.basedefensev2.level.WorldLevelSavedData;
import com.nhatbh.basedefensev2.registry.ModBosses;
import com.nhatbh.basedefensev2.stage.StageLoader;
import com.nhatbh.basedefensev2.stage.config.MobSpawnEntry;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import com.nhatbh.basedefensev2.stage.config.WaveConfig;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StageTabUI {

    private final UpgradeCart cart;
    private StageConfig nextStageConfig = null;
    private int nextStageNumber = 1;
    private int currentWorldLevel = 0;

    private LivingEntity currentBossEntity = null;
    private String currentBossEntityId = "";

    public StageTabUI(UpgradeCart cart) {
        this.cart = cart;
        updateData();
    }

    public void updateData() {
        nextStageConfig = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        net.minecraft.server.level.ServerLevel serverLevel = null;
        if (mc.getSingleplayerServer() != null) {
            serverLevel = mc.getSingleplayerServer().overworld();
        }

        if (serverLevel != null) {
            WorldLevelSavedData wlData = WorldLevelSavedData.get(serverLevel);
            if (wlData != null) {
                this.currentWorldLevel = wlData.getWorldLevel();
            }

            List<StageConfig> allStages = new ArrayList<>(StageLoader.getAllStages(serverLevel));
            allStages.sort(Comparator.comparingInt(s -> s.order));

            int targetIndex = 0;
            StageContext ctx = StageContext.getOrCreate(serverLevel);
            if (ctx != null && ctx.getActiveConfig() != null) {
                targetIndex = ctx.getActiveConfig().order + 1;
            } else {
                targetIndex = this.currentWorldLevel;
            }

            if (!allStages.isEmpty()) {
                targetIndex = Math.max(0, Math.min(targetIndex, allStages.size() - 1));
                nextStageConfig = allStages.get(targetIndex);
            }
        }

        if (nextStageConfig != null) {
            this.nextStageNumber = nextStageConfig.order + 1;
            updateBoss3DEntity();
        }
    }

    private void updateBoss3DEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (nextStageConfig == null || mc.level == null) return;

        MobSpawnEntry bossEntry = null;
        for (WaveConfig w : nextStageConfig.waves) {
            for (MobSpawnEntry m : w.mobs) {
                if (m.is_boss && !m.boss_id.contains("miniboss")) {
                    bossEntry = m;
                    break;
                }
            }
        }

        if (bossEntry != null) {
            BossDefinition bossDef = ModBosses.get(bossEntry.boss_id);
            if (bossDef != null && bossDef.getBaseEntity() != null) {
                String baseEntity = bossDef.getBaseEntity();
                if (!baseEntity.equals(currentBossEntityId)) {
                    currentBossEntityId = baseEntity;
                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(baseEntity));
                    if (type != null) {
                        Entity entity = type.create(mc.level);
                        if (entity instanceof LivingEntity living) {
                            currentBossEntity = living;
                        }
                    }
                }
                return;
            }
        }
        currentBossEntity = null;
        currentBossEntityId = "";
    }

    public List<Button> buildWidgets(Screen screen, int xOffset, int yOffset) {
        updateData();
        return new ArrayList<>();
    }

    public void render(GuiGraphics g, Font font, int xOffset, int yOffset, int mouseX, int mouseY, float partialTicks) {
        int width = 500;
        int height = 360;

        if (nextStageConfig == null) {
            g.drawCenteredString(font, "§cNo upcoming stage data available", xOffset + width / 2, yOffset + height / 2 - 4, 0xFFFFFF);
            return;
        }

        // Header Banner
        ElementType elem = StageAPI.getStageElement(nextStageConfig);
        String elemName = elem != null ? elem.name() : "NONE";
        int elemColor = StageAPI.getElementColor(elem);
        int waveCount = nextStageConfig.waves.size();

        String headerTitle = "★ NEXT STAGE INTELLIGENCE (STAGE " + nextStageNumber + ") ★";
        g.drawCenteredString(font, headerTitle, xOffset + width / 2, yOffset + 4, 0xFFFFAA00);

        String subtitle = "Waves: §f" + waveCount + " §7| Element Affinity: ";
        int subW = font.width("Waves: " + waveCount + " | Element Affinity: ") + font.width(elemName);
        int subX = xOffset + (width - subW) / 2;
        g.drawString(font, subtitle, subX, yOffset + 16, 0xAAAAAA, false);
        g.drawString(font, elemName, subX + font.width("Waves: " + waveCount + " | Element Affinity: "), yOffset + 16, elemColor, false);

        // 1. World Level Info Box
        renderWorldLevelSection(g, font, xOffset + 10, yOffset + 28, width - 20, 38);

        // 2. Boss Information Card
        renderBossCardSection(g, font, xOffset + 10, yOffset + 70, width - 20, 285, nextStageConfig, mouseX, mouseY);
    }

    // ── World Level Progression Section ─────────────────────────────────────
    private void renderWorldLevelSection(GuiGraphics g, Font font, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF121926);
        g.renderOutline(x, y, width, height, 0xFF2B3A52);
        g.renderOutline(x + 1, y + 1, width - 2, height - 2, 0xFF1A2433);

        int nextWorldLevel = currentWorldLevel + 1;
        int currBaseLv = MobLevelConfig.getOverworldBaseLevel(currentWorldLevel);
        int nextBaseLv = MobLevelConfig.getOverworldBaseLevel(nextWorldLevel);

        // Left Column: Current World Level
        int leftX = x + 8;
        g.drawString(font, "§6CURRENT WORLD LEVEL: §f" + currentWorldLevel, leftX, y + 4, 0xFFFFAA00, false);
        g.drawString(font, "§7Base Mob Lv: §a" + currBaseLv, leftX, y + 16, 0xCCCCCC, false);

        // Divider Line
        int midX = x + (width / 2);
        g.vLine(midX, y + 4, y + height - 4, 0xFF2B3A52);

        // Right Column: Next World Level
        int rightX = midX + 8;
        g.drawString(font, "§bNEXT WORLD LEVEL: §f" + nextWorldLevel, rightX, y + 4, 0xFF00E5FF, false);
        g.drawString(font, "§7Base Mob Lv: §a" + nextBaseLv, rightX, y + 16, 0xCCCCCC, false);
        g.drawString(font, "§8(Unlocks after Stage " + nextStageNumber + ")", rightX + 115, y + 16, 0x88AAAA, false);
    }

    // ── Boss Info Card ────────────────────────────────────────────────────────
    private void renderBossCardSection(GuiGraphics g, Font font, int x, int y, int width, int height, StageConfig stage, int mouseX, int mouseY) {
        g.fill(x, y, x + width, y + height, 0xFF121622);
        g.renderOutline(x, y, width, height, 0xFF775522);
        g.renderOutline(x + 1, y + 1, width - 2, height - 2, 0xFF332211);

        MobSpawnEntry bossEntry = null;
        for (WaveConfig w : stage.waves) {
            for (MobSpawnEntry m : w.mobs) {
                if (m.is_boss && !m.boss_id.contains("miniboss")) {
                    bossEntry = m;
                    break;
                }
            }
        }

        if (bossEntry == null) {
            g.drawCenteredString(font, "§7No major boss encounter configured for Stage " + nextStageNumber, x + width / 2, y + height / 2 - 4, 0xAAAAAA);
            return;
        }

        BossDefinition bossDef = ModBosses.get(bossEntry.boss_id);
        if (bossDef == null) {
            g.drawCenteredString(font, "§cUnknown Boss Definition: " + bossEntry.boss_id, x + width / 2, y + height / 2 - 4, 0xFF5555);
            return;
        }

        // 1. Left Side: 3D Model Display Box
        int modelBoxX = x + 8;
        int modelBoxY = y + 8;
        int modelBoxW = 135;
        int modelBoxH = height - 16;

        g.fill(modelBoxX, modelBoxY, modelBoxX + modelBoxW, modelBoxY + modelBoxH, 0xFF080B12);
        g.renderOutline(modelBoxX, modelBoxY, modelBoxW, modelBoxH, 0xFF445577);

        Minecraft mc = Minecraft.getInstance();
        if (currentBossEntity != null && mc.level != null) {
            float entityHeight = currentBossEntity.getBbHeight();
            int scale = 42;
            if (entityHeight > 0) {
                scale = (int) Math.max(16, Math.min(55, 55 / Math.max(1.0f, entityHeight)));
            }
            int renderCenterX = modelBoxX + (modelBoxW / 2);
            int renderCenterY = modelBoxY + modelBoxH - 14;

            long time = mc.level.getGameTime();
            float rotationAngle = (time % 360) * 2.5f;
            float xAngle = (float) Math.sin(Math.toRadians(rotationAngle)) * 0.8f;
            float yAngle = 0.0f;

            InventoryScreen.renderEntityInInventoryFollowsAngle(g, renderCenterX, renderCenterY, scale, xAngle, yAngle, currentBossEntity);
        }

        // 2. Right Side: Full Boss Stats & Skill Breakdown
        int infoX = modelBoxX + modelBoxW + 12;
        int infoY = y + 8;
        int infoW = width - modelBoxW - 28;

        String titlePrefix = "";
        String passiveName = "None";
        String passiveDesc = "Grants unique combat mechanics.";
        if (!bossDef.getPhases().isEmpty() && !bossDef.getPhases().get(0).getPassives().isEmpty()) {
            var passive = bossDef.getPhases().get(0).getPassives().get(0);
            titlePrefix = passive.getTitlePrefix();
            passiveName = passive.getName();
            passiveDesc = passive.getDescription();
        }

        String rawEntity = bossDef.getBaseEntity();
        String mobName = rawEntity.contains(":") ? rawEntity.split(":")[1] : rawEntity;
        mobName = StageAPI.formatSkillName(mobName);

        String bossDisplayName = (!titlePrefix.isEmpty() ? titlePrefix + " " : "") + mobName;

        // Boss Header Title
        g.drawString(font, "§f§l" + bossDisplayName.toUpperCase(), infoX, infoY, 0xFFFFFF, false);
        infoY += 12;

        // Health & Poise Stat Bars
        int hpVal = (int) bossDef.getBaseStats().health;
        int poiseVal = (int) bossDef.getMaxPoise();

        g.drawString(font, "§cHP: §f" + hpVal + "   §ePoise: §f" + poiseVal, infoX, infoY, 0xFFFFFF, false);
        infoY += 10;

        int miniBarW = (infoW - 8) / 2;
        g.fill(infoX, infoY, infoX + miniBarW, infoY + 4, 0xFF441111);
        g.fill(infoX, infoY, infoX + miniBarW, infoY + 4, 0xFFDD2222);

        int poiseBarX = infoX + miniBarW + 8;
        g.fill(poiseBarX, infoY, poiseBarX + miniBarW, infoY + 4, 0xFF443300);
        g.fill(poiseBarX, infoY, poiseBarX + miniBarW, infoY + 4, 0xFFFFCC00);
        infoY += 9;

        // Passive Skill Card
        int passiveBoxH = 44;
        g.fill(infoX, infoY, infoX + infoW, infoY + passiveBoxH, 0xFF1B1626);
        g.renderOutline(infoX, infoY, infoW, passiveBoxH, 0xFF8855CC);
        g.drawString(font, "§d⚡ " + passiveName, infoX + 6, infoY + 4, 0xFFFF88, false);

        g.pose().pushPose();
        g.pose().scale(0.70f, 0.70f, 1.0f);
        int scaledDescX = (int) ((infoX + 6) / 0.70f);
        int scaledDescY = (int) ((infoY + 15) / 0.70f);
        int maxScaledWidth = (int) ((infoW - 12) / 0.70f);

        for (FormattedCharSequence line : font.split(Component.literal("§7" + passiveDesc), maxScaledWidth)) {
            g.drawString(font, line, scaledDescX, scaledDescY, 0xDDDDDD, false);
            scaledDescY += 9;
        }
        g.pose().popPose();

        infoY += passiveBoxH + 6;

        // Active Skills Breakdown across all phases (De-duplicated base skills)
        List<ActiveSkill> allActiveSkills = new ArrayList<>();
        Set<String> seenBaseSkillIds = new HashSet<>();

        for (var phase : bossDef.getPhases()) {
            for (var entry : phase.getActives()) {
                String baseId = entry.skill.getId().replaceAll("_p[0-9]+", "").replaceAll("_mb[0-9]+", "");
                if (seenBaseSkillIds.add(baseId)) {
                    allActiveSkills.add(entry.skill);
                }
            }
        }

        g.drawString(font, "§eACTIVE SKILLS §7(" + allActiveSkills.size() + ")", infoX, infoY, 0xFFFFAA00, false);
        infoY += 11;

        int skillColW = (infoW - 6) / 2;
        int cardH = 20;

        for (int i = 0; i < allActiveSkills.size() && i < 6; i++) {
            ActiveSkill skill = allActiveSkills.get(i);
            int col = i % 2;
            int row = i / 2;

            int skX = infoX + col * (skillColW + 6);
            int skY = infoY + row * (cardH + 4);

            String skName = skill.getDisplayName();
            float cdSec = skill.getCooldown() / 20.0f;
            String cdStr = String.format("%.1fs CD", cdSec);

            g.fill(skX, skY, skX + skillColW, skY + cardH, 0xFF14202D);
            g.renderOutline(skX, skY, skillColW, cardH, 0xFF336688);

            // Skill Header (Name + Cooldown)
            g.drawString(font, "§a► " + skName, skX + 4, skY + 6, 0xFFFFFF, false);
            int cdWidth = font.width(cdStr);
            g.drawString(font, "§e" + cdStr, skX + skillColW - cdWidth - 4, skY + 6, 0xFFFFAA00, false);
        }
    }

}
