package com.complextalents.impl.highpriest.skills.seraphsedge;

import com.complextalents.impl.highpriest.data.SeraphSwordData;
import com.complextalents.impl.highpriest.entity.SeraphsEdgeEntity;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.skill.event.ResolvedTargetData;
import com.complextalents.targeting.TargetType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import com.complextalents.leveling.service.LevelingService;
import com.complextalents.leveling.events.xp.XPSource;
import com.complextalents.leveling.events.xp.XPContext;
import com.complextalents.leveling.util.XPFormula;
import com.complextalents.leveling.data.PlayerLevelingData;
import net.minecraft.world.level.ChunkPos;

/**
 * Seraphic Echo - A divine orb of light that hovers and moves through space.
 * <p>
 * When cast on block/entity, move the beacon to that position.
 * Damages/Debuffs enemies and Shields/Buffs allies on its path.
 */
public class SeraphicEchoSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "seraphic_echo");

    public static void register() {
        SkillBuilder.create("complextalents", "seraphic_echo")
                .nature(SkillNature.ACTIVE)
                .displayName("Seraphic Echo")
                .description(
                        "Di chuyển Beacon gây sát thương đường bay (10 Command) hoặc Gắn lên người chơi trong 7s (20 Command, tỏa hào quang Absorption & Speed II 3 khối). Nhắm vào Beacon đang có để kích hoạt Pull/Purify (>=50 Command, hoàn 10 Command): kéo các mục tiêu lân cận về Beacon, giải hiệu ứng bất lợi cho đồng đội và gây sát thương diện rộng.")
                .targeting(TargetType.POSITION)
                .allowSelfTarget(true)
                .icon(ResourceLocation.fromNamespaceAndPath("complextalents",
                        "textures/skill/highpriest/seraphs_echo.png"))
                .maxRange(32.0)
                .minChannelTime(0.1)
                .maxChannelTime(0.1)
                .scaledCooldown(new double[] { 2, 2, 2, 2, 2 })
                .setMaxLevel(5)
                .scaledStat("damage", new double[] { 10, 14, 18, 22, 28 })
                .scaledStat("shield", new double[] { 4, 6, 8, 10, 12 })
                .scaledStat("purifyDamageMult", new double[] { 1.50, 1.75, 2.00, 2.25, 2.50 })
                .scaledStat("purifyShieldBase", new double[] { 6.0, 8.0, 10.0, 12.0, 15.0 })
                .scaledStat("purifyShieldMult", new double[] { 4.0, 6.0, 8.0, 10.0, 12.0 })
                .onActive((context, rawPlayer) -> {
                    ServerPlayer player = (ServerPlayer) rawPlayer;
                    ResolvedTargetData targetData = context.target().getAs(ResolvedTargetData.class);
                    if (targetData == null)
                        return;

                    double baseDamage = context.getStat("damage");
                    double shieldAmount = context.getStat("shield");
                    double purifyDamageMult = context.getStat("purifyDamageMult");
                    double purifyShieldBase = context.getStat("purifyShieldBase");
                    double purifyShieldMult = context.getStat("purifyShieldMult");

                    double holySpellPower = 1.0;
                    Attribute holyPowerAttr = ForgeRegistries.ATTRIBUTES.getValue(
                            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "holy_spell_power"));
                    if (holyPowerAttr != null) {
                        holySpellPower = player.getAttributeValue(holyPowerAttr);
                    }

                    double healShieldPower = player.getAttributeValue(com.complextalents.registry.ModAttributes.HEAL_AND_SHIELD_POWER.get());

                    baseDamage *= holySpellPower;
                    shieldAmount *= (holySpellPower * healShieldPower);

                    Vec3 targetPos;
                    Entity targetEntity = null;
                    if (targetData.hasEntity()) {
                        targetEntity = targetData.getTargetEntity();
                        targetPos = targetEntity.position();
                    } else {
                        targetPos = targetData.getTargetPosition();
                    }

                    SeraphsEdgeEntity sword = SeraphSwordData.getActiveSword(player);
                    int currentCommand = com.complextalents.passive.PassiveManager.getPassiveStacks(player, "command");

                    // Helper method for mana refund
                    java.util.function.Consumer<Integer> refundMana = (consumedCmd) -> {
                        if (consumedCmd <= 0)
                            return;
                        if (com.complextalents.origin.integration.OriginModIntegrationHandler
                                .isIronSpellbooksLoaded()) {
                            try {
                                double refundRate = com.complextalents.origin.OriginManager.getOriginStat(player,
                                        "manaRefundPerCommand");
                                io.redspace.ironsspellbooks.api.magic.MagicData magicData = io.redspace.ironsspellbooks.api.magic.MagicData
                                        .getPlayerMagicData(player);
                                double maxMana = player.getAttributeValue(
                                        io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
                                float manaAmount = (float) (maxMana * (consumedCmd * refundRate));
                                if (manaAmount > 0) {
                                    magicData.setMana(magicData.getMana() + manaAmount);
                                    io.redspace.ironsspellbooks.setup.PacketDistributor.sendToPlayer(player,
                                            new io.redspace.ironsspellbooks.network.SyncManaPacket(magicData));
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    };

                    // Action B: Target is the orb itself -> Variable Pull / Purify (50 Command)
                    if (sword != null && targetEntity == sword) {
                        if (currentCommand < 50) {
                            player.displayClientMessage(net.minecraft.network.chat.Component
                                    .literal("§cNot enough Command (50 required for Pull)"), true);
                            return;
                        }

                        int consumedCommand = currentCommand;
                        // Consumes all current command, then refunds 10 command back
                        com.complextalents.passive.PassiveManager.setPassiveStacks(player, "command", 10);

                        // Refund mana based on total Command consumed
                        refundMana.accept(consumedCommand);

                        // Continuous scaling from 50 (t=0.0) to 100 (t=1.0)
                        double scalingT = Math.min(1.0, Math.max(0.0, (consumedCommand - 50) / 50.0));
                        double absorptionShield = (purifyShieldBase + (purifyShieldMult * holySpellPower)) * scalingT * healShieldPower;

                        int pulledCount = sword.executeVariablePull(scalingT, purifyDamageMult, absorptionShield);

                        if (scalingT >= 0.5) {
                            player.level().playSound(null, sword.getX(), sword.getY(), sword.getZ(),
                                    SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0f, 1.2f);
                        } else {
                            player.level().playSound(null, sword.getX(), sword.getY(), sword.getZ(),
                                    SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0f, 1.5f);
                        }

                        if (pulledCount > 0) {
                            int playerLevel = PlayerLevelingData.get(player.getServer()).getLevel(player.getUUID());
                            double crowdXP = XPFormula.calculateHighPriestCrowdControlXP(pulledCount, playerLevel);
                            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
                            XPContext crowdContext = XPContext.builder()
                                    .source(XPSource.HIGHPRIEST_CROWD_CONTROL)
                                    .chunkPos(chunkPos)
                                    .rawAmount(crowdXP)
                                    .metadata("mobsPulled", pulledCount)
                                    .metadata("playerLevel", playerLevel)
                                    .metadata("scalingT", scalingT)
                                    .build();
                            LevelingService.getInstance().awardXP(player, crowdXP, XPSource.HIGHPRIEST_CROWD_CONTROL,
                                    crowdContext);
                        }
                        return;
                    }

                    // Action A1: Target is a Player -> Attach Mode (20 Command)
                    if (targetEntity instanceof net.minecraft.world.entity.player.Player targetPlayer) {
                        if (currentCommand < 20) {
                            player.displayClientMessage(net.minecraft.network.chat.Component
                                    .literal("§cNot enough Command (20 required to Attach)"), true);
                            return;
                        }

                        com.complextalents.passive.PassiveManager.modifyPassiveStacks(player, "command", -20);
                        refundMana.accept(20);

                        if (sword == null || sword.distanceToSqr(player) > 64 * 64) {
                            sword = new SeraphsEdgeEntity(player.level(), player);
                            sword.configure((float) baseDamage, (float) shieldAmount);

                            Vec3 spawnPos = player.getEyePosition().add(player.getLookAngle().scale(1.5));
                            sword.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

                            player.level().addFreshEntity(sword);
                            SeraphSwordData.setActiveSword(player, sword);
                        } else {
                            sword.configure((float) baseDamage, (float) shieldAmount);
                        }

                        sword.attachToPlayer(targetPlayer);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
                    } else {
                        // Action A2: Move sword to static target position (10 Command)
                        if (currentCommand < 10) {
                            player.displayClientMessage(net.minecraft.network.chat.Component
                                    .literal("§cNot enough Command (10 required to Move)"), true);
                            return;
                        }

                        com.complextalents.passive.PassiveManager.modifyPassiveStacks(player, "command", -10);
                        refundMana.accept(10);

                        if (sword == null || sword.distanceToSqr(player) > 64 * 64) {
                            sword = new SeraphsEdgeEntity(player.level(), player);
                            sword.configure((float) baseDamage, (float) shieldAmount);

                            Vec3 spawnPos = player.getEyePosition().add(player.getLookAngle().scale(1.5));
                            sword.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

                            player.level().addFreshEntity(sword);
                            SeraphSwordData.setActiveSword(player, sword);
                        } else {
                            sword.configure((float) baseDamage, (float) shieldAmount);
                        }

                        sword.moveTo(targetPos);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);
                    }
                })
                .register();
    }
}
