package com.complextalents.targeting.client;

import com.complextalents.targeting.*;
import com.complextalents.util.AllyHelper;
import com.complextalents.util.KeyHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Single source of targeting truth on the client.
 */
public class ClientTargetingResolver {

    private static final ClientTargetingResolver INSTANCE = new ClientTargetingResolver();
    private final Minecraft minecraft = Minecraft.getInstance();

    private ClientTargetingResolver() {
    }

    public static ClientTargetingResolver getInstance() {
        return INSTANCE;
    }

    public TargetingSnapshot resolve(TargetingRequest request) {
        Player player = request.getPlayer();
        Level level = player.level();

        Vec3 origin = getEyePosition(player);
        Vec3 look = player.getLookAngle();
        Vec3 maxEnd = origin.add(look.scale(request.getMaxRange()));

        EnumSet<TargetType> resolvedTypes = EnumSet.of(TargetType.DIRECTION);

        Vec3 targetPosition = maxEnd;
        int targetEntityId = -1;
        boolean hasEntity = false;
        boolean isAlly = false;
        double distance = request.getMaxRange();
        boolean hitBlock = false;

        /* -------------------- BLOCK RAYCAST -------------------- */
        if (request.getAllowedTypes().contains(TargetType.POSITION)) {
            BlockHitResult blockHit = raycastBlocks(level, origin, maxEnd);
            if (blockHit.getType() != HitResult.Type.MISS) {
                targetPosition = blockHit.getLocation();
                distance = origin.distanceTo(targetPosition);
                hitBlock = true;
            }
            resolvedTypes.add(TargetType.POSITION);
        }

        /* -------------------- ENTITY TARGETING (SMART SELECTION) -------------------- */
        if (!request.isDisableSmartCast() && (request.getAllowedTypes().contains(TargetType.ENTITY)
                || request.getAllowedTypes().contains(TargetType.POSITION))) {

            double maxRange = request.getMaxRange();
            AABB searchArea = player.getBoundingBox().inflate(maxRange);
            Predicate<Entity> predicate = createEntityPredicate(request);

            List<Entity> candidates = level.getEntities((Entity) null, searchArea,
                    entity -> predicate.test(entity) && entity.isPickable() && !entity.isSpectator());

            Entity bestEntity = null;
            Vec3 bestTargetPos = null;
            double maxSearchRadius = request.getEntitySearchRadius() > 0 ? request.getEntitySearchRadius() * 2.5 : 6.0;
            double maxAllowedScore = 0.50; // ~26.5 degrees maximum crosshair deviation (generous angle)
            double bestScore = maxAllowedScore;
            double bestDistance = maxRange;

            // Maximum allowed search cone angle (45 degrees)
            double minCosTheta = Math.cos(Math.toRadians(45.0));

            for (Entity candidate : candidates) {
                Vec3 targetPoint = candidate.getBoundingBox().getCenter();
                Vec3 vecToCandidate = targetPoint.subtract(origin);
                double candidateDist = vecToCandidate.length();

                if (candidateDist > maxRange || candidateDist < 1.0E-5) {
                    continue;
                }

                // Forward direction check
                double dot = vecToCandidate.dot(look);
                if (dot <= 0) {
                    continue;
                }

                // Angle cone check
                double cosTheta = dot / candidateDist;
                if (cosTheta < minCosTheta) {
                    continue;
                }

                // Occlusion by solid block hit
                if (hitBlock && candidateDist > distance + 0.5) {
                    continue;
                }

                // Line of sight check
                if (request.isRequireLineOfSight() && !hasLineOfSight(level, origin, targetPoint, candidate)) {
                    continue;
                }

                // Point on look ray closest to entity center
                double t = Math.min(dot, maxRange);
                Vec3 pointOnRay = origin.add(look.scale(t));

                // 3D distance from look ray to entity's bounding box surface
                double rayDistToBox = distanceToAABB(pointOnRay, candidate.getBoundingBox());
                if (rayDistToBox > maxSearchRadius) {
                    continue;
                }

                // Angular / crosshair offset score (rayDistToBox / t)
                double score = rayDistToBox / Math.max(t, 0.1);

                if (score < bestScore - 1.0E-5) {
                    bestScore = score;
                    bestEntity = candidate;
                    bestTargetPos = targetPoint;
                    bestDistance = candidateDist;
                } else if (Math.abs(score - bestScore) <= 1.0E-5) {
                    if (candidateDist < bestDistance) {
                        bestScore = score;
                        bestEntity = candidate;
                        bestTargetPos = targetPoint;
                        bestDistance = candidateDist;
                    }
                }
            }

            if (bestEntity != null) {
                targetEntityId = bestEntity.getId();
                hasEntity = true;
                targetPosition = bestTargetPos;
                distance = bestDistance;
                isAlly = AllyHelper.isAlly(player, bestEntity);

                resolvedTypes.add(TargetType.ENTITY);
                resolvedTypes.add(TargetType.POSITION);
            }
        }

        // Self-targeting fallback ONLY when shift key is held and skill allows self-target
        boolean isShiftDown = KeyHelper.isShiftDown();
        if (!hasEntity && request.isTargetSelfAllowed() && isShiftDown && (request.getAllowedTypes().contains(TargetType.ENTITY) || request.getAllowedTypes().contains(TargetType.POSITION))) {
            targetEntityId = player.getId();
            hasEntity = true;
            targetPosition = player.position();
            distance = 0.0;
            isAlly = true;

            resolvedTypes.add(TargetType.ENTITY);
            resolvedTypes.add(TargetType.POSITION);
        }

        return new TargetingSnapshot(
                origin,
                look,
                targetPosition,
                targetEntityId,
                hasEntity,
                isAlly,
                distance,
                resolvedTypes);
    }

    public TargetingSnapshot resolveForLocalPlayer(
            double maxRange,
            EnumSet<TargetType> allowedTypes,
            TargetRelation relationFilter) {
        Player localPlayer = minecraft.player;
        if (localPlayer == null) {
            return TargetingSnapshot.createEmpty();
        }

        boolean isShiftDown = KeyHelper.isShiftDown();
        boolean disableSmartCast = !SmartCastManager.isSmartCastEnabled();

        return resolve(TargetingRequest.builder(localPlayer)
                .maxRange(maxRange)
                .allowedTypes(allowedTypes)
                .relationFilter(relationFilter)
                .targetPlayerOnly(isShiftDown)
                .disableSmartCast(disableSmartCast)
                .build());
    }

    /**
     * Calculates the shortest 3D distance from a point to an Axis-Aligned Bounding Box (AABB).
     * Returns 0.0 if the point is inside the bounding box.
     */
    private double distanceToAABB(Vec3 point, AABB box) {
        double dx = Math.max(0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /* ========================================================= */
    /* ===================== RAYCASTING ======================== */
    /* ========================================================= */

    private Vec3 getEyePosition(Player player) {
        return new Vec3(
                player.getX(),
                player.getEyeY(),
                player.getZ());
    }

    private BlockHitResult raycastBlocks(Level level, Vec3 start, Vec3 end) {
        Vec3 currentStart = start;
        Vec3 dir = end.subtract(start);
        double totalDistance = dir.length();
        if (totalDistance < 1.0E-6) {
            return level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, null));
        }
        Vec3 unitDir = dir.scale(1.0 / totalDistance);

        int maxIter = 16;
        while (maxIter-- > 0) {
            BlockHitResult result = level.clip(new ClipContext(
                    currentStart,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    null));

            if (result.getType() == HitResult.Type.MISS) {
                return result;
            }

            BlockPos pos = result.getBlockPos();
            BlockState state = level.getBlockState(pos);

            if (shouldIgnoreBlock(state)) {
                currentStart = result.getLocation().add(unitDir.scale(0.05));
                if (start.distanceToSqr(currentStart) >= totalDistance * totalDistance) {
                    return BlockHitResult.miss(end, result.getDirection(), pos);
                }
            } else {
                return result;
            }
        }
        return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
    }

    private boolean shouldIgnoreBlock(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock) {
            return true;
        }
        if (state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS)
                || state.getBlock() instanceof BushBlock) {
            return true;
        }
        return false;
    }

    /**
     * Entity raycast using vanilla ProjectileUtil.
     */
    private EntityHitResult raycastEntities(
            Level level,
            Vec3 start,
            Vec3 end,
            Predicate<Entity> predicate) {
        return ProjectileUtil.getEntityHitResult(
                level,
                null, // no projectile owner
                start,
                end,
                new AABB(start, end).inflate(1.0D),
                entity -> predicate.test(entity)
                        && entity.isPickable()
                        && !entity.isSpectator());
    }

    /* ========================================================= */
    /* ===================== FILTERING ========================= */
    /* ========================================================= */

    private Predicate<Entity> createEntityPredicate(TargetingRequest request) {
        Player player = request.getPlayer();

        return entity -> {
            if (entity == player && !request.isTargetSelfAllowed()) {
                return false;
            }

            if (!(entity instanceof LivingEntity living)) {
                return false;
            }

            if (!living.isAlive()) {
                return false;
            }

            // Filter by player-only if enabled
            if (request.isTargetPlayerOnly() && !(entity instanceof Player)) {
                return false;
            }

            boolean ally = AllyHelper.isAlly(player, entity);

            // Filter by ally-only if enabled
            if (request.isTargetAllyOnly() && !ally) {
                return false;
            }

            return request.getRelationFilter().matches(ally);
        };
    }

    private boolean hasLineOfSight(
            Level level,
            Vec3 start,
            Vec3 end,
            Entity target) {
        BlockHitResult result = raycastBlocks(level, start, end);

        return result.getType() == HitResult.Type.MISS
                || result.getLocation().distanceTo(start) >= end.distanceTo(start) - 0.1;
    }
}
