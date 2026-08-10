package com.complextalents.impl.highpriest.entity;

import com.complextalents.TalentsMod;
import com.complextalents.elemental.handlers.DelayedActionHandler;
import com.complextalents.impl.highpriest.data.SeraphSwordData;
import com.complextalents.network.PacketHandler;
import com.complextalents.network.S2CSpawnAAAParticlePacket;
import com.complextalents.network.SpawnSeraphSwordFXPacket;
import com.complextalents.util.AllyHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.DamageTypeTags;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Seraph's Beacon Entity - A divine orb of light that hovers and moves through
 * space.
 * <p>
 * Three casting modes:
 * 1. Spawn & Move: Creates beacon at player position, flies to target, hovers
 * 2. Move from Hovering: Moves existing beacon with enhanced effects (1.5x
 * damage/shield)
 * 3. Pull: Target the beacon to pull enemies toward it
 */
public class SeraphsEdgeEntity extends LivingEntity {

    // Configuration constants
    private static final double MOVE_SPEED = 2;
    private static final double DESPAWN_RANGE = 48.0;
    private static final double PULL_RADIUS = 8.0;

    // State
    private Vec3 targetPos = null;
    private boolean wasHovering = false;
    private @Nullable UUID ownerUUID;
    private @Nullable Entity cachedOwner;

    // Player Attachment state
    private @Nullable UUID attachedPlayerUUID = null;
    private @Nullable Player cachedAttachedPlayer = null;
    private int attachTicksRemaining = 0;

    // Configuration
    private float baseDamage = 10.0f;
    private float shieldAmount = 5.0f;

    // Tracking
    private final Set<UUID> hitEntitiesThisMove = new HashSet<>();

    public SeraphsEdgeEntity(EntityType<? extends SeraphsEdgeEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public SeraphsEdgeEntity(Level level, LivingEntity owner) {
        this(HighPriestEntities.SERAPHS_EDGE.get(), level);
        this.setOwner(owner);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
    }

    // === OWNER HANDLING ===

    public void setOwner(@Nullable Entity owner) {
        this.ownerUUID = owner == null ? null : owner.getUUID();
        this.cachedOwner = owner;
    }

    @Nullable
    public Entity getOwner() {
        if (this.cachedOwner != null && !this.cachedOwner.isRemoved()) {
            return this.cachedOwner;
        } else if (this.ownerUUID != null && this.level() instanceof ServerLevel serverLevel) {
            this.cachedOwner = serverLevel.getEntity(this.ownerUUID);
            return this.cachedOwner;
        } else {
            return null;
        }
    }

    // === LIVING ENTITY BOILERPLATE ===

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isEffectiveAi() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Only allow discard from owner or high-level damage (creative/void)
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    // === PUBLIC API ===

    public void configure(float damage, float shield) {
        this.baseDamage = damage;
        this.shieldAmount = shield;
    }

    public void attachToPlayer(Player player) {
        this.attachedPlayerUUID = player.getUUID();
        this.cachedAttachedPlayer = player;
        this.attachTicksRemaining = 140; // 7 seconds (140 ticks)
        this.targetPos = null;
        this.wasHovering = true;
        this.hitEntitiesThisMove.clear();

        Vec3 headPos = player.position().add(0, player.getBbHeight() + 0.1, 0);
        setPos(headPos.x, headPos.y, headPos.z);
        this.xo = headPos.x;
        this.yo = headPos.y;
        this.zo = headPos.z;
        this.xOld = headPos.x;
        this.yOld = headPos.y;
        this.zOld = headPos.z;
        setDeltaMovement(Vec3.ZERO);

        applyAttachedAuraEffects();

        if (level() instanceof ServerLevel serverLevel) {
            PacketHandler.sendToNearby(
                    new SpawnSeraphSwordFXPacket(position().add(0, 0.5, 0), null, 5),
                    serverLevel,
                    position());
        }
    }

    private void applyAttachedAuraEffects() {
        if (level().isClientSide)
            return;

        List<Player> nearbyPlayers = level().getEntitiesOfClass(
                Player.class,
                getBoundingBox().inflate(3.0),
                p -> p.isAlive());

        float shieldAmplifier = shieldAmount * 1.5f;
        int amp = Math.max(0, (int) (shieldAmplifier / 4.0f));
        int speedAmplifier = 2;

        for (Player player : nearbyPlayers) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.ABSORPTION,
                    140, // 7 seconds duration (140 ticks)
                    amp,
                    false,
                    true));
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    140, // 7 seconds duration (140 ticks)
                    speedAmplifier,
                    false,
                    true));
        }
    }

    public void detachPlayer() {
        this.attachedPlayerUUID = null;
        this.cachedAttachedPlayer = null;
        this.attachTicksRemaining = 0;
    }

    @Nullable
    public Player getAttachedPlayer() {
        if (this.cachedAttachedPlayer != null && !this.cachedAttachedPlayer.isRemoved()) {
            return this.cachedAttachedPlayer;
        } else if (this.attachedPlayerUUID != null && this.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.getEntity(this.attachedPlayerUUID) instanceof Player player) {
                this.cachedAttachedPlayer = player;
                return this.cachedAttachedPlayer;
            }
        }
        return null;
    }

    public void moveTo(Vec3 target) {
        detachPlayer();
        this.wasHovering = (this.targetPos == null);
        this.targetPos = target;
        this.hitEntitiesThisMove.clear();

        Vec3 direction = targetPos.subtract(position()).normalize();
        Vec3 velocity = direction.scale(MOVE_SPEED);
        setDeltaMovement(velocity);
    }

    public int executeVariablePull(double scalingT, double purifyDamageMult, double absorptionShield) {
        if (level().isClientSide)
            return 0;

        Entity owner = getOwner();
        List<LivingEntity> enemies = level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(PULL_RADIUS),
                e -> e.isAlive() && !(e instanceof Player));

        float pullForce = (float) (0.3 + 0.2 * scalingT);
        float damageMult = (float) (1.0 + (purifyDamageMult - 1.0) * scalingT);
        float burstDamage = baseDamage * damageMult;
        int slowDuration = (int) (40 + 20 * scalingT);
        int slowAmp = (int) (1 + scalingT);

        for (LivingEntity enemy : enemies) {
            Vec3 pullVec = position().subtract(enemy.position());
            enemy.setDeltaMovement(enemy.getDeltaMovement().add(pullVec.scale(pullForce)));
            enemy.hurtMarked = true;

            enemy.hurt(
                    level().damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null),
                    burstDamage);
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowDuration, slowAmp));

            PacketHandler.sendToNearby(
                    new S2CSpawnAAAParticlePacket(
                            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "hiteffect"),
                            enemy.position().add(0, enemy.getBbHeight() * 0.5, 0)),
                    (ServerLevel) level(),
                    enemy.position());
        }

        if (absorptionShield > 0) {
            List<Player> players = level().getEntitiesOfClass(
                    Player.class,
                    getBoundingBox().inflate(PULL_RADIUS),
                    p -> p.isAlive());

            int amp = Math.max(0, (int) (absorptionShield / 4.0));
            for (Player player : players) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, amp));
                PacketHandler.sendToNearby(
                        new S2CSpawnAAAParticlePacket(
                                ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "hiteffect"),
                                player.position().add(0, player.getBbHeight() * 0.5, 0)),
                        (ServerLevel) level(),
                        player.position());
            }
        }

        PacketHandler.sendToNearby(
                new S2CSpawnAAAParticlePacket(
                        ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "orbpull"),
                        position().add(0, 0.6, 0)),
                (ServerLevel) level(),
                position());

        PacketHandler.sendToNearby(
                new SpawnSeraphSwordFXPacket(position().add(0, 0, 0), null, 4),
                (ServerLevel) level(),
                position());

        return enemies.size();
    }

    public int pullEnemies() {
        if (level().isClientSide)
            return 0;

        List<LivingEntity> enemies = level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(PULL_RADIUS),
                e -> e.isAlive() && !(e instanceof Player));

        for (LivingEntity enemy : enemies) {
            // Scale pull with distance: further away = stronger pull
            Vec3 pullVec = position().subtract(enemy.position());
            enemy.setDeltaMovement(enemy.getDeltaMovement().add(pullVec.scale(0.3)));
            enemy.hurtMarked = true;

            // Delayed damage and slowdown after 10 ticks
            DelayedActionHandler.queueAction((ServerLevel) level(), 10, () -> {
                if (enemy.isAlive()) {
                    Entity currentOwner = getOwner();
                    enemy.hurt(
                            level().damageSources().mobProjectile(this,
                                    currentOwner instanceof LivingEntity l ? l : null),
                            baseDamage * 0.8f);
                    enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                }
            });
        }

        // Play orb pull particle effect
        PacketHandler.sendToNearby(
                new S2CSpawnAAAParticlePacket(
                        ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "orbpull"),
                        position().add(0, 0.6, 0)),
                (ServerLevel) level(),
                position());

        PacketHandler.sendToNearby(
                new SpawnSeraphSwordFXPacket(position().add(0, 0, 0), null, 4),
                (ServerLevel) level(),
                position());

        return enemies.size();
    }

    public int purifyEnemies(double damageMultiplier, double absorptionShield) {
        if (level().isClientSide)
            return 0;

        Entity owner = getOwner();
        List<LivingEntity> enemies = level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(PULL_RADIUS),
                e -> e.isAlive() && !(e instanceof Player));

        float purifyDamage = (float) (baseDamage * damageMultiplier);

        for (LivingEntity enemy : enemies) {
            Vec3 pullVec = position().subtract(enemy.position());
            enemy.setDeltaMovement(enemy.getDeltaMovement().add(pullVec.scale(0.5)));
            enemy.hurtMarked = true;

            // Instant burst damage on Purify
            enemy.hurt(
                    level().damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null),
                    purifyDamage);
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));

            PacketHandler.sendToNearby(
                    new S2CSpawnAAAParticlePacket(
                            ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "hiteffect"),
                            enemy.position().add(0, enemy.getBbHeight() * 0.5, 0)),
                    (ServerLevel) level(),
                    enemy.position());
        }

        // Grant brief Absorption shield (5s / 100 ticks) to all nearby players
        // regardless of team
        if (absorptionShield > 0) {
            List<Player> players = level().getEntitiesOfClass(
                    Player.class,
                    getBoundingBox().inflate(PULL_RADIUS),
                    p -> p.isAlive());

            int amp = Math.max(0, (int) (absorptionShield / 4.0));
            for (Player player : players) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, amp)); // 5 seconds brief duration
                PacketHandler.sendToNearby(
                        new S2CSpawnAAAParticlePacket(
                                ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "hiteffect"),
                                player.position().add(0, player.getBbHeight() * 0.5, 0)),
                        (ServerLevel) level(),
                        player.position());
            }
        }

        PacketHandler.sendToNearby(
                new S2CSpawnAAAParticlePacket(
                        ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "orbpull"),
                        position().add(0, 0.6, 0)),
                (ServerLevel) level(),
                position());

        PacketHandler.sendToNearby(
                new SpawnSeraphSwordFXPacket(position().add(0, 0, 0), null, 5),
                (ServerLevel) level(),
                position());

        return enemies.size();
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 prevPos = position();

        if (!level().isClientSide) {
            checkOwnerDistance();

            if (attachedPlayerUUID != null) {
                Player targetPlayer = getAttachedPlayer();
                Entity owner = getOwner();

                boolean invalidTarget = targetPlayer == null || !targetPlayer.isAlive() || targetPlayer.isRemoved();
                boolean outOfTimer = attachTicksRemaining <= 0;
                boolean outOfRange = owner instanceof Player priest && (priest
                        .distanceToSqr(targetPlayer != null ? targetPlayer : this) > DESPAWN_RANGE * DESPAWN_RANGE);

                if (invalidTarget || outOfTimer || outOfRange) {
                    discardAndClear();
                    return;
                }

                Vec3 headPos = targetPlayer.position().add(0, targetPlayer.getBbHeight() + 0.1, 0);
                setPos(headPos.x, headPos.y, headPos.z);
                this.xo = headPos.x;
                this.yo = headPos.y;
                this.zo = headPos.z;
                this.xOld = headPos.x;
                this.yOld = headPos.y;
                this.zOld = headPos.z;
                setDeltaMovement(Vec3.ZERO);
                targetPos = null;
                attachTicksRemaining--;

                applyAttachedAuraEffects();
            } else if (targetPos != null) {
                Vec3 currentPos = position();
                Vec3 toTarget = targetPos.subtract(currentPos);
                double distance = toTarget.length();

                if (distance < MOVE_SPEED) {
                    setDeltaMovement(Vec3.ZERO);
                    setPos(targetPos);
                    targetPos = null;

                    PacketHandler.sendToNearby(
                            new SpawnSeraphSwordFXPacket(position().add(0, 0.5, 0), null, 5),
                            (ServerLevel) level(),
                            position());
                } else {
                    Vec3 direction = toTarget.normalize();
                    Vec3 velocity = direction.scale(MOVE_SPEED);
                    setDeltaMovement(velocity);
                }
            }
        }

        Vec3 vel = getDeltaMovement();
        if (vel.lengthSqr() > 1.0E-7) {
            move(MoverType.SELF, vel);

            if (!level().isClientSide) {
                applyPathEffects(prevPos, position());
            }
        }

        if (!level().isClientSide) {
            sendParticleFX();
        }
    }

    // === PATH EFFECTS ===

    private void applyPathEffects(Vec3 start, Vec3 end) {
        AABB sweepBox = new AABB(start, end).inflate(1.5);
        List<LivingEntity> entities = level().getEntitiesOfClass(
                LivingEntity.class,
                sweepBox,
                e -> e.isAlive() && e != this && !(e instanceof Player) && !hitEntitiesThisMove.contains(e.getUUID()));

        for (LivingEntity entity : entities) {
            hitEntitiesThisMove.add(entity.getUUID());
            applyEnemyEffects(entity);
        }
    }

    private void applyEnemyEffects(LivingEntity enemy) {
        float damage = wasHovering ? baseDamage * 1.5f : baseDamage;
        Entity owner = getOwner();
        enemy.hurt(
                level().damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null),
                damage);

        int slowAmplifier = wasHovering ? 2 : 1;
        int duration = wasHovering ? 60 : 40;
        enemy.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                duration,
                slowAmplifier));

        // Play hit particle effect
        PacketHandler.sendToNearby(
                new S2CSpawnAAAParticlePacket(
                        ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "hiteffect"),
                        enemy.position().add(0, enemy.getBbHeight() * 0.5, 0)),
                (ServerLevel) level(),
                enemy.position());
    }

    private void checkOwnerDistance() {
        Entity owner = getOwner();
        if (!(owner instanceof Player player)) {
            discardAndClear();
            return;
        }

        if (player.distanceToSqr(this) > DESPAWN_RANGE * DESPAWN_RANGE) {
            discardAndClear();
        }
    }

    private void discardAndClear() {
        Entity owner = getOwner();
        if (owner instanceof Player p) {
            SeraphSwordData.clearActiveSword(p.getUUID());
        }
        discard();
    }

    private void sendParticleFX() {
        if (tickCount % 2 != 0)
            return;
        int fxType = (targetPos != null) ? 0 : 3;

        PacketHandler.sendToNearby(
                new SpawnSeraphSwordFXPacket(position().add(0, 0.5, 0), getDeltaMovement(), fxType),
                (ServerLevel) level(),
                position());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("WasHovering", wasHovering);
        if (targetPos != null) {
            compound.putBoolean("HasTarget", true);
            compound.putDouble("TargetX", targetPos.x);
            compound.putDouble("TargetY", targetPos.y);
            compound.putDouble("TargetZ", targetPos.z);
        } else {
            compound.putBoolean("HasTarget", false);
        }
        compound.putFloat("BaseDamage", baseDamage);
        compound.putFloat("ShieldAmount", shieldAmount);
        if (ownerUUID != null) {
            compound.putUUID("OwnerUUID", ownerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        wasHovering = compound.getBoolean("WasHovering");
        if (compound.getBoolean("HasTarget")) {
            targetPos = new Vec3(
                    compound.getDouble("TargetX"),
                    compound.getDouble("TargetY"),
                    compound.getDouble("TargetZ"));
        } else {
            targetPos = null;
        }
        baseDamage = compound.getFloat("BaseDamage");
        shieldAmount = compound.getFloat("ShieldAmount");
        if (compound.hasUUID("OwnerUUID")) {
            ownerUUID = compound.getUUID("OwnerUUID");
        }
    }
}
