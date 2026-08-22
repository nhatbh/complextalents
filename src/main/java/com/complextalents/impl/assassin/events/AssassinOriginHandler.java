package com.complextalents.impl.assassin.events;

import com.complextalents.TalentsMod;
import com.complextalents.impl.assassin.effect.AssassinEffects;
import com.complextalents.impl.assassin.origin.AssassinOrigin;
import com.complextalents.impl.assassin.util.AssassinUtils;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.assassin.AssassinEntitySyncPacket;
import com.complextalents.origin.OriginManager;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles all events related to the Assassin origin and its passives.
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class AssassinOriginHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && AssassinOrigin.isAssassin(attacker)) {
            if (AssassinUtils.isBackstab(attacker, event.getEntity())) {
                handleOriginBackstab(attacker, event.getEntity());
            }
        }
    }

    /**
     * Runs the full Assassin origin backstab pipeline against a target.
     * Called both by the normal hit path and by the poise-negation path so that
     * poise-absorbed hits still trigger the complete set of passive effects:
     * <ol>
     *   <li>Expose Weakness (weakpoint effect)</li>
     *   <li>The Disengage (attacker speed burst)</li>
     *   <li>Backstab FX (sound + particles)</li>
     * </ol>
     *
     * @param attacker the attacking Assassin player
     * @param target   the entity being struck (may have taken 0 HP damage)
     */
    public static void handleOriginBackstab(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
        // 1. Passive: Expose Weakness
        tryApplyExposeWeakness(attacker, target);

        // 2. Passive: The Disengage
        double speedBonus = OriginManager.getOriginStat(attacker, "disengageMoveSpeed");
        double burstDuration = OriginManager.getOriginStat(attacker, "disengageDuration");
        attacker.addEffect(
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, (int) (burstDuration * 20), (int) (speedBonus * 10)));

        // 3. Backstab FX (Consistency with Shadow Walk)
        if (attacker.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() / 2.0;
            double z = target.getZ();

            // Sound
            serverLevel.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);

            // Chunky Splatter (Redstone Block)
            BlockParticleOption bloodSplatter = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
            serverLevel.sendParticles(bloodSplatter, x, y, z, 15, 0.2, 0.3, 0.2, 0.15);

            // Fine Mist (Red Dust)
            DustParticleOptions bloodMist = new DustParticleOptions(new Vector3f(0.6f, 0.0f, 0.0f), 1.2f);
            serverLevel.sendParticles(bloodMist, x, y, z, 10, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /**
     * Applies the Expose Weakness (weakpoint) effect to a target when an Assassin
     * lands a valid backstab. This is factored out so it can also be called when
     * a hit is fully negated by poise — the weakpoint should still register even
     * though no health damage was dealt.
     *
     * @param attacker the attacking Assassin player
     * @param target   the entity being struck
     */
    public static void tryApplyExposeWeakness(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
        int level = OriginManager.getOriginLevel(attacker);
        long gameTime = attacker.level().getGameTime();

        if (!AssassinUtils.isEntityOnCooldown(target, gameTime)) {
            double duration = OriginManager.getOriginStat(attacker, "exposeDuration");
            target.addEffect(
                    new MobEffectInstance(AssassinEffects.EXPOSE_WEAKNESS.get(), (int) (duration * 20), level - 1));

            double cooldown = OriginManager.getOriginStat(attacker, "exposeCooldown");
            long expiration = gameTime + (long) (cooldown * 20);
            AssassinUtils.setEntityCooldown(target, gameTime, expiration);

            if (attacker.level() instanceof net.minecraft.server.level.ServerLevel levelServer) {
                PacketHandler.sendToNearby(new AssassinEntitySyncPacket(target.getId(), gameTime, expiration),
                        levelServer, target.position());
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().hasEffect(AssassinEffects.EXPOSE_WEAKNESS.get())) {
            if (event.getSource().getEntity() instanceof ServerPlayer attacker && AssassinOrigin.isAssassin(attacker)) {
                double ampMultiplier = OriginManager.getOriginStat(attacker, "exposeDamageAmp");
                event.setAmount((float) (event.getAmount() * (1.0 + ampMultiplier)));
            } else {
                // Global fallback for teammates (uses level 1 amp from the registry)
                event.setAmount((float) (event.getAmount() * (1.0 + AssassinOrigin.getExposeAmp(1))));
            }
        }
    }
}
