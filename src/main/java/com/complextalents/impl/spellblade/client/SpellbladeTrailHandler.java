package com.complextalents.impl.spellblade.client;

import com.complextalents.TalentsMod;
import com.complextalents.impl.spellblade.SpellbladeDataProvider;
import com.complextalents.spellmastery.SpellSchool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.api.client.animation.property.TrailInfo;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.EntityDecorations;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

/**
 * Client-side Epic Fight Sword Trail handler for the Spellblade origin.
 * Modifies sword trail colors dynamically in real-time according to the active elemental spell imbue
 * and Overcharge stance, applying fullbright emissive lighting (blockLight = 15, skyLight = 15).
 */
@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class SpellbladeTrailHandler {

    private static final ResourceLocation TRAIL_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(TalentsMod.MODID, "spellblade_trail_modifier");

    @SubscribeEvent
    public static void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player == null || !player.level().isClientSide()) return;

        // Verify entity patch in Epic Fight
        LivingEntityPatch<?> entitypatch = EpicFightCapabilities.getEntityPatch(player, LivingEntityPatch.class);
        if (entitypatch == null) return;

        var capOpt = player.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).resolve();
        if (capOpt.isEmpty()) return;

        var cap = capOpt.get();
        SpellSchool activeElement = cap.getActiveElement();
        int enhancedTicks = cap.getEnhancedAttackTicks();
        boolean hasCharge = cap.hasImbueCharge();
        boolean isOvercharge = cap.isOverchargeStance() || cap.getOverchargeTicks() > 0;

        boolean isImbueActive = activeElement != null && (enhancedTicks > 0 || hasCharge);

        EntityDecorations decorations = entitypatch.getEntityDecorations();

        if (isImbueActive) {
            // Apply dynamic TrailInfoModifier for elemental color & emissive lighting
            decorations.addTrailInfoModifier(TRAIL_MODIFIER_ID, new EntityDecorations.AnimationPropertyModifier<TrailInfo, CapabilityItem>() {
                @Override
                public TrailInfo getModifiedValue(TrailInfo val, CapabilityItem object) {
                    if (val == null) return null;

                    TrailInfo.Builder builder = val.unpackAsBuilder();
                    float[] rgb = getSchoolRgb(activeElement);

                    // Overcharge stance boosts trail brightness and persistence
                    int extraLifetime = isOvercharge ? 4 : 2;
                    float boost = isOvercharge ? 1.35f : 1.0f;

                    builder.r(Math.min(1.0f, rgb[0] * boost));
                    builder.g(Math.min(1.0f, rgb[1] * boost));
                    builder.b(Math.min(1.0f, rgb[2] * boost));
                    builder.lifetime(val.trailLifetime() + extraLifetime);
                    
                    // Fullbright emissive lighting effect
                    builder.blockLight(15);
                    builder.skyLight(15);

                    return builder.create();
                }

                @Override
                public boolean shouldRemove() {
                    Player p = (Player) entitypatch.getOriginal();
                    if (p == null || p.isRemoved()) return true;
                    var cOpt = p.getCapability(SpellbladeDataProvider.SPELLBLADE_DATA).resolve();
                    if (cOpt.isEmpty()) return true;
                    var c = cOpt.get();
                    SpellSchool elem = c.getActiveElement();
                    boolean active = elem != null && (c.getEnhancedAttackTicks() > 0 || c.hasImbueCharge());
                    return !active;
                }
            });
        } else {
            decorations.removeTrailInfoModifier(TRAIL_MODIFIER_ID);
        }
    }

    private static float[] getSchoolRgb(SpellSchool school) {
        if (school == null) return new float[]{1.0f, 1.0f, 1.0f};
        return switch (school) {
            case FIRE -> new float[]{0.95f, 0.35f, 0.15f};
            case ICE -> new float[]{0.35f, 0.85f, 0.95f};
            case LIGHTNING -> new float[]{0.95f, 0.85f, 0.20f};
            case NATURE -> new float[]{0.25f, 0.90f, 0.35f};
            case AQUA -> new float[]{0.25f, 0.60f, 0.95f};
            case EVOCATION -> new float[]{0.95f, 0.95f, 1.00f};
            case BLOOD -> new float[]{0.90f, 0.08f, 0.12f};
            case ENDER -> new float[]{0.65f, 0.25f, 0.95f};
            case ELDRITCH -> new float[]{0.55f, 0.15f, 0.85f};
            case HOLY -> new float[]{1.00f, 0.88f, 0.40f};
            case ABYSSAL -> new float[]{0.15f, 0.05f, 0.25f};
            case TECHNOMANCY -> new float[]{1.00f, 0.42f, 0.00f};
        };
    }
}
