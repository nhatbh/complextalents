package com.complextalents.impl.marksman.events;

import com.complextalents.TalentsMod;
import com.complextalents.effect.ModEffects;
import com.complextalents.impl.marksman.data.MarksmanAdrenalineData;
import com.complextalents.impl.marksman.network.S2CKillBannerPacket;
import com.complextalents.impl.marksman.skill.RelentlessPursuitSkill;
import com.complextalents.network.PacketHandler;
import com.complextalents.skill.event.SkillCastRequestEvent;
import com.complextalents.tacz.GunType;
import com.complextalents.tacz.HeartRateManager;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Event Handler for Marksman Active Skill: Relentless Pursuit & Segmented Dismiss Mechanics.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class RelentlessPursuitEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSkillCastRequest(SkillCastRequestEvent event) {
        if (!RelentlessPursuitSkill.ID.equals(event.getSkillId())) {
            return;
        }

        ServerPlayer player = event.getPlayer();

        // Check if player is already in Adrenaline Mode
        if (MarksmanAdrenalineData.isActive(player)) {
            event.setCanceled(true); // Intercept standard cast execution

            if (MarksmanAdrenalineData.canDismiss(player)) {
                // Re-activation at 100 resource: Trigger Dismissed State!
                player.addEffect(new MobEffectInstance(ModEffects.DISMISSED.get(), 20, 0, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false));

                // Clear targeting from nearby mobs within 32 blocks
                AABB searchBox = player.getBoundingBox().inflate(32.0);
                List<Mob> nearbyMobs = player.level().getEntitiesOfClass(Mob.class, searchBox, mob -> mob.getTarget() == player);
                for (Mob mob : nearbyMobs) {
                    mob.setTarget(null);
                }

                // Consume 1 Dismiss Charge (100 resource)
                MarksmanAdrenalineData.consumeDismissCharge(player);

                // FX: Smoke vanish sound & particle burst
                ServerLevel level = player.serverLevel();
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8f, 1.6f);
                level.sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        25, 0.3, 0.5, 0.3, 0.05);
            } else {
                float resource = MarksmanAdrenalineData.getDismissResource(player);
                event.setFailureReason(String.format("%.0f", resource));
            }
        }
    }

    /**
     * Shooting instantly cancels Dismissed state & Invisibility.
     */
    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        LivingEntity shooter = event.getShooter();
        if (shooter != null && !shooter.level().isClientSide()) {
            if (shooter.hasEffect(ModEffects.DISMISSED.get())) {
                shooter.removeEffect(ModEffects.DISMISSED.get());
                shooter.removeEffect(MobEffects.INVISIBILITY);
            }
        }
    }

    /**
     * Miss Penalty (-1.0s): Bullet hits terrain/block without hitting an entity.
     */
    @SubscribeEvent
    public static void onAmmoHitBlock(AmmoHitBlockEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getAmmo() != null && event.getAmmo().getOwner() instanceof ServerPlayer player) {
            if (MarksmanAdrenalineData.isActive(player)) {
                MarksmanAdrenalineData.deductDuration(player, 1.0f);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (MarksmanAdrenalineData.isActive(player)) {
            // Tick server-side Adrenaline duration
            MarksmanAdrenalineData.tickServer(player);

            // Lock Heart Rate to resting state (60 BPM)
            HeartRateManager.setHeartRate(player, HeartRateManager.RESTING_BPM);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && entity.hasEffect(ModEffects.DISMISSED.get())) {
            event.setCanceled(true); // 100% Invulnerable during Dismissed state
        }

        // Attacking or dealing damage cancels Dismissed state & Invisibility
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            if (attacker.hasEffect(ModEffects.DISMISSED.get())) {
                attacker.removeEffect(ModEffects.DISMISSED.get());
                attacker.removeEffect(MobEffects.INVISIBILITY);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (MarksmanAdrenalineData.isActive(player)) {
                // Extend Adrenaline duration (+3.0s, capped at double base duration)
                MarksmanAdrenalineData.addDuration(player, 3.0f);

                // Dispatch Kill Banner Packet to client
                int killStreak = MarksmanAdrenalineData.incrementKillCount(player);
                PacketHandler.sendTo(new S2CKillBannerPacket(killStreak), player);
            }
        }
    }

    /**
     * Hit Resolution:
     * - Bodyshot: Deducts -0.5s directly.
     * - Headshot: Deducts 0.0s (No duration loss) + Grants Dismiss resource.
     */
    @SubscribeEvent
    public static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
        LivingEntity attacker = event.getAttacker();
        if (!(attacker instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (!MarksmanAdrenalineData.isActive(player)) {
            return;
        }

        if (event.isHeadShot()) {
            // Headshot: No duration loss (0.0s penalty) + Grant Dismiss resource
            ItemStack mainStack = player.getMainHandItem();
            GunType gunType = GunType.fromItemStack(mainStack);
            float gain = switch (gunType) {
                case SNIPER -> 25.0f; // 4 headshots per dismiss
                case SHOTGUN -> 15.0f;
                case RIFLE -> 12.5f; // 8 headshots per dismiss
                case PISTOL -> 10.0f; // 10 headshots per dismiss
                case SMG -> 6.0f;
                case MG -> 4.0f;
                default -> 10.0f;
            };
            MarksmanAdrenalineData.addDismissResource(player, gain);
        } else {
            // Bodyshot: Deduct 0.5s directly
            MarksmanAdrenalineData.deductDuration(player, 0.5f);
        }
    }
}
