package com.complextalents.skill.server;

import com.complextalents.skill.*;
import com.complextalents.skill.capability.IPlayerSkillData;
import com.complextalents.skill.capability.SkillDataProvider;
import com.complextalents.skill.event.*;
import com.complextalents.skill.form.SkillFormManager;
import com.complextalents.skill.form.SkillFormRegistry;
import com.complextalents.targeting.TargetType;
import com.complextalents.targeting.TargetingSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

/**
 * Server-side handler for skill casting.
 *
 * <p>Processes skill casting entirely via events:</p>
 * <pre>
 * SkillCastPacket
 *   ↓
 * SkillCastRequestEvent (cooldowns, resources, permissions)
 *   ↓
 * TargetResolutionEvent (normalizes targeting data)
 *   ↓
 * SkillPreExecuteEvent (final validation gate)
 *   ↓
 * SkillExecuteEvent (core execution)
 *   ↓
 * SkillPostExecuteEvent (after-effects)
 * </pre>
 */
public class SkillTargetingHandler {

    /**
     * Handle a skill cast packet from the client.
     *
     * @param player The player who cast the skill
     * @param skillId The ID of the skill being cast
     * @param slotIndex The hotbar slot index (0-3)
     * @param snapshot The targeting snapshot from client resolution
     * @param channelTime The channel time in seconds (0 for instant skills)
     */
    public static void handleSkillCast(ServerPlayer player, ResourceLocation skillId,
                                        int slotIndex, TargetingSnapshot snapshot, double channelTime) {


        // Get skill nature and targeting type (placeholder - integrate with your skill registry)
        SkillNature nature = getSkillNature(skillId);
        TargetType targetingType = getTargetingType(skillId);


        // Passives shouldn't send cast packets
        if (nature == SkillNature.PASSIVE) {
            return;
        }

        // Stage 1: Cast Request Event
        // Used for: cooldowns, resource checks, silence/stun, permissions
        SkillCastRequestEvent requestEvent = new SkillCastRequestEvent(player, skillId, slotIndex);
        MinecraftForge.EVENT_BUS.post(requestEvent);

        if (requestEvent.isCanceled()) {
            // Cast failed at request stage (cooldown, no resources, etc.)
            sendCastFailure(player, skillId, requestEvent.getFailureReason());
            return;
        }

        // Check if this is a form skill
        if (SkillFormRegistry.getInstance().isFormSkill(skillId)) {
            IPlayerSkillData data = player.getCapability(SkillDataProvider.SKILL_DATA).orElse(null);
            if (data != null) {
                // Create a self-targeted data for form skills (they don't need targeting)
                Vec3 playerPos = player.position();
                Vec3 lookDir = player.getLookAngle();
                ResolvedTargetData selfTarget = new ResolvedTargetData(
                        player, player, playerPos, lookDir, true, true, 0.0
                );

                if (!data.isToggleActive(skillId)) {
                    // Activate form
                    if (SkillFormManager.activateForm(player, skillId)) {
                        data.setToggleActive(skillId, true);
                        // Send post-event for form activation
                        SkillPostExecuteEvent postEvent = new SkillPostExecuteEvent(
                                player, skillId, selfTarget, true
                        );
                        MinecraftForge.EVENT_BUS.post(postEvent);
                    }
                } else {
                    // Deactivate form manually
                    SkillFormManager.deactivateForm(player);
                    data.setToggleActive(skillId, false);
                    // Send post-event for form deactivation
                    SkillPostExecuteEvent postEvent = new SkillPostExecuteEvent(
                            player, skillId, selfTarget, true
                    );
                    MinecraftForge.EVENT_BUS.post(postEvent);
                }
            }
            return;  // Don't proceed with normal cast flow
        }

        // Stage 2: Target Resolution Event
        // Normalizes targeting data into guaranteed-valid form
        TargetResolutionEvent resolutionEvent = new TargetResolutionEvent(
                player,
                skillId,
                targetingType,
                snapshot
        );
        MinecraftForge.EVENT_BUS.post(resolutionEvent);

        // Get resolved target data
        ResolvedTargetData targetData = resolutionEvent.getResolvedTarget();


        // Validate: ENTITY-targeted skills must have an entity target
        if (targetingType == TargetType.ENTITY && !targetData.hasEntity()) {
            sendCastFailure(player, skillId, "No target");
            return;
        }

        // Stage 3: Pre-Execute Event
        // Final validation gate: ally protection, area restrictions, conditional failures
        SkillPreExecuteEvent preEvent = new SkillPreExecuteEvent(
                player,
                skillId,
                targetData
        );
        MinecraftForge.EVENT_BUS.post(preEvent);

        if (preEvent.isCanceled()) {
            // Cast failed at pre-execution stage
            sendCastFailure(player, skillId, preEvent.getFailureReason());
            return;
        }

        // Stage 4: Execute Event
        // Core execution - skills listen for this event
        SkillExecuteEvent executeEvent = new SkillExecuteEvent(
                player,
                skillId,
                targetData,
                channelTime
        );
        MinecraftForge.EVENT_BUS.post(executeEvent);


        // Stage 5: Post-Execute Event
        // After-effects: cooldowns, resource consumption, passive triggers
        SkillPostExecuteEvent postEvent = new SkillPostExecuteEvent(
                player,
                skillId,
                targetData,
                !executeEvent.isCanceled()
        );
        MinecraftForge.EVENT_BUS.post(postEvent);

    }

    /**
     * Get the skill nature for a given skill ID.
     */
    private static SkillNature getSkillNature(ResourceLocation skillId) {
        return SkillRegistry.getInstance().getSkillNature(skillId);
    }

    /**
     * Get the targeting type for a given skill ID.
     */
    private static TargetType getTargetingType(ResourceLocation skillId) {
        return SkillRegistry.getInstance().getTargetingType(skillId);
    }

    /**
     * Send a cast failure notification to the client.
     * TODO: Implement failure feedback packet.
     */
    private static void sendCastFailure(ServerPlayer player, ResourceLocation skillId, String reason) {
        // For now, just log to chat
        player.sendSystemMessage(Component.literal("§cCast failed: " + reason));
    }
}
