package com.complextalents.impl.pixie.skills;

import com.complextalents.effect.ModEffects;
import com.complextalents.impl.pixie.data.PixieData;
import com.complextalents.impl.pixie.data.PixieDataManager;
import com.complextalents.impl.pixie.events.PixieEventHandler;
import com.complextalents.skill.SkillBuilder;
import com.complextalents.skill.SkillNature;
import com.complextalents.skill.event.ResolvedTargetData;
import com.complextalents.targeting.TargetType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class FaeSurgeSkill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("complextalents", "fae_surge");

    public static void register() {
        SkillBuilder.create("complextalents", "fae_surge")
                .nature(SkillNature.ACTIVE)
                .displayName("Bộc Phát Tiên Khí & Dấu Ấn Tiên Linh")
                .description("Nhấn để tạo lá chắn và tăng tốc cho đồng minh, đồng thời khắc dấu ấn nổ sát thương phép lên kẻ địch. Nhấn giữ để nhập thể, hủy nhập thể hoặc chuyển đổi mục tiêu bảo vệ.")
                .targeting(TargetType.POSITION)
                .allowSelfTarget(true)
                .maxRange(32.0)
                .minChannelTime(0.0)
                .maxChannelTime(1.0)
                .scaledCooldown(new double[] { 0.5, 0.5, 0.5, 0.5, 0.5 })
                .setMaxLevel(5)
                .scaledStat("speedBuff", "Tăng Tốc Ký Chủ (%)", new double[] { 0.15, 0.20, 0.25, 0.30, 0.40 })
                .scaledStat("statSpeedBuff", "Tốc Thi Phép (%)", new double[] { 0.15, 0.20, 0.25, 0.35, 0.50 })
                .scaledStat("baseShield", "Lá Chắn Tiên Khí", new double[] { 100, 150, 200, 260, 350 })
                .scaledStat("baseDetonationDmg", "ST Bộc Phát Dấu Ấn", new double[] { 40, 60, 80, 110, 150 })
                .onActive((context, rawPlayer) -> {
                    ServerPlayer player = (ServerPlayer) rawPlayer;
                    PixieData pixieData = PixieDataManager.get(player);

                    ResolvedTargetData targetData = context.target().getAs(ResolvedTargetData.class);
                    if (targetData == null) return;

                    double channelDuration = context.channelTime();
                    boolean isHoldCast = channelDuration >= 0.4; // 0.4s channel considered hold

                    if (isHoldCast) {
                        // --- HOLD SKILL: Attach / Detach / Swap Host ---
                        if (targetData.hasEntity() && targetData.getTargetEntity() instanceof LivingEntity targetEntity && targetEntity != player) {
                            // Attach or Swap Host (Player or Mob)
                            pixieData.setPixie(true);
                            if (targetEntity instanceof Player targetPlayer) {
                                pixieData.setHostUUID(targetPlayer.getUUID());
                                pixieData.setHostEntityId(null);
                            } else {
                                pixieData.setHostUUID(null);
                                pixieData.setHostEntityId(targetEntity.getId());
                            }
                            pixieData.setAttached(true);
                            pixieData.setRelativeOffset(new Vec3(0, targetEntity.getBbHeight() + 0.3, 0));

                            player.displayClientMessage(Component.literal("§aAttached to " + targetEntity.getName().getString() + "!"), true);
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.5f);
                        } else {
                            // Target self or empty space -> Detach
                            if (pixieData.isAttached()) {
                                PixieEventHandler.detachPixie(player, pixieData, "Self cast / Unbound space");
                            } else {
                                pixieData.setPixie(true);
                                player.displayClientMessage(Component.literal("§aPixie Flight form active (Detached)."), true);
                            }
                        }
                    } else {
                        // --- TAP SKILL: Fae Surge ---
                        double dustCost = 25.0; // 25 Pixie Dust per buff cast
                        if (!pixieData.consumePixieDust(dustCost)) {
                            player.displayClientMessage(Component.literal("§cNot enough Pixie Dust! (" + (int)pixieData.getPixieDust() + "/" + (int)PixieData.MAX_PIXIE_DUST + ")"), true);
                            return;
                        }

                        double speedBuff = context.getStat("speedBuff");
                        double statSpeedBuff = context.getStat("statSpeedBuff");
                        double shieldAmount = context.getStat("baseShield");

                        double ap = 1.0;
                        Attribute apAttr = ForgeRegistries.ATTRIBUTES.getValue(
                                ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power"));
                        if (apAttr != null) {
                            ap = player.getAttributeValue(apAttr);
                        }

                        shieldAmount += (ap * 0.50);
                        double healShieldPower = player.getAttributeValue(com.complextalents.registry.ModAttributes.HEAL_AND_SHIELD_POWER.get());
                        shieldAmount *= healShieldPower;

                        // Host Buff (Applies ONLY to host)
                        if (pixieData.isAttached() && pixieData.getHostUUID().isPresent()) {
                            ServerPlayer host = player.getServer().getPlayerList().getPlayer(pixieData.getHostUUID().get());
                            if (host != null) {
                                int speedAmp = Math.max(0, (int)(speedBuff * 5.0) - 1);
                                host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, speedAmp, false, true));
                                host.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, (int)(statSpeedBuff * 5.0), false, true));
                                host.setAbsorptionAmount((float)(host.getAbsorptionAmount() + shieldAmount));

                                host.level().playSound(null, host.getX(), host.getY(), host.getZ(),
                                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
                                player.displayClientMessage(Component.literal("§aEmpowered host with Fae Surge! [Dust: " + (int)pixieData.getPixieDust() + "/" + (int)PixieData.MAX_PIXIE_DUST + "]"), true);
                            }
                        }
                    }
                })
                .register();
    }
}
