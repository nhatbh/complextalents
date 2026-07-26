package com.complextalents.impl.darkmage.client;

import com.complextalents.TalentsMod;
import com.complextalents.elemental.client.renderers.entities.CustomRenderTypes;
import com.complextalents.impl.darkmage.util.BloodParticleHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID, value = Dist.CLIENT)
public class BloodOrbRenderer {

    private static final Map<UUID, ClientBloodOrbData> ACTIVE_ORBS = new ConcurrentHashMap<>();

    public static void addOrb(UUID orbId, Vec3 pos, double densityV, UUID ownerUUID, int lifetime) {
        ACTIVE_ORBS.put(orbId, new ClientBloodOrbData(orbId, pos, densityV, ownerUUID, lifetime));
    }

    public static void removeOrb(UUID orbId, boolean detonate) {
        ACTIVE_ORBS.remove(orbId);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ACTIVE_ORBS.clear();
            return;
        }

        Iterator<Map.Entry<UUID, ClientBloodOrbData>> it = ACTIVE_ORBS.entrySet().iterator();
        while (it.hasNext()) {
            ClientBloodOrbData orb = it.next().getValue();
            orb.tick();

            if (orb.currentTick >= orb.lifetime) {
                it.remove();
                continue;
            }

            // Spawn subtle dispersed ambient blood mist aura every 3 ticks
            if (orb.currentTick % 3 == 0) {
                double spread = 0.3 + 0.1 * Math.sqrt(Math.max(0.1, orb.densityV));
                float bobOffset = (float) Math.sin(orb.currentTick * 0.1f) * 0.04f;
                double px = orb.pos.x + (level.random.nextDouble() - 0.5) * spread;
                double py = orb.pos.y + 0.4 + bobOffset + (level.random.nextDouble() - 0.5) * spread * 0.4;
                double pz = orb.pos.z + (level.random.nextDouble() - 0.5) * spread;
                level.addParticle(BloodParticleHelper.BLOOD_MIST, px, py, pz, 0, 0.01, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ACTIVE_ORBS.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        float partialTicks = event.getPartialTick();

        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

        for (ClientBloodOrbData orb : ACTIVE_ORBS.values()) {
            poseStack.pushPose();

            double x = orb.pos.x - camPos.x;
            double y = orb.pos.y - camPos.y;
            double z = orb.pos.z - camPos.z;

            float time = orb.currentTick + partialTicks;
            float bobOffset = (float) Math.sin(time * 0.1f) * 0.04f;

            poseStack.translate(x, y + 0.4 + bobOffset, z);

            // Orb radius starts off tiny for weak mobs (0.065m at V=0.1) and grows smoothly/slowly (up to 0.40m at V=20)
            float coreRadius = (float) (0.04f + 0.08f * Math.sqrt(Math.max(0.1, orb.densityV)));
            float glowRadius = coreRadius * 1.45f;

            // Render core 3D sphere (solid deep blood/crimson)
            VertexConsumer coreConsumer = buffer.getBuffer(CustomRenderTypes.sphereNoCull());
            renderSphere(poseStack, coreConsumer, coreRadius, 16, 16, 240, 150, 10, 25, 230);

            // Render outer glow 3D sphere (translucent blood aura scaling with density V)
            VertexConsumer glowConsumer = buffer.getBuffer(CustomRenderTypes.sphereGlow());
            renderSphere(poseStack, glowConsumer, glowRadius, 14, 14, 240, 190, 20, 40, 80);

            poseStack.popPose();
        }

        buffer.endBatch();
    }

    private static void renderSphere(PoseStack poseStack, VertexConsumer consumer,
                                       float radius, int stacks, int slices, int packedLight,
                                       int r, int g, int b, int a) {
        Matrix4f matrix4f = poseStack.last().pose();
        Matrix3f matrix3f = poseStack.last().normal();

        for (int i = 0; i < stacks; i++) {
            float lat0 = (float) Math.PI * (-0.5f + (float) i / stacks);
            float z0 = (float) Math.sin(lat0);
            float zr0 = (float) Math.cos(lat0);

            float lat1 = (float) Math.PI * (-0.5f + (float) (i + 1) / stacks);
            float z1 = (float) Math.sin(lat1);
            float zr1 = (float) Math.cos(lat1);

            for (int j = 0; j < slices; j++) {
                float lng0 = 2 * (float) Math.PI * (float) j / slices;
                float x0 = (float) Math.cos(lng0);
                float y0 = (float) Math.sin(lng0);

                float lng1 = 2 * (float) Math.PI * (float) (j + 1) / slices;
                float x1 = (float) Math.cos(lng1);
                float y1 = (float) Math.sin(lng1);

                addVertex(consumer, matrix4f, matrix3f,
                        x0 * zr0 * radius, y0 * zr0 * radius, z0 * radius,
                        x0 * zr0, y0 * zr0, z0, packedLight, r, g, b, a);
                addVertex(consumer, matrix4f, matrix3f,
                        x1 * zr0 * radius, y1 * zr0 * radius, z0 * radius,
                        x1 * zr0, y1 * zr0, z0, packedLight, r, g, b, a);
                addVertex(consumer, matrix4f, matrix3f,
                        x1 * zr1 * radius, y1 * zr1 * radius, z1 * radius,
                        x1 * zr1, y1 * zr1, z1, packedLight, r, g, b, a);

                addVertex(consumer, matrix4f, matrix3f,
                        x0 * zr0 * radius, y0 * zr0 * radius, z0 * radius,
                        x0 * zr0, y0 * zr0, z0, packedLight, r, g, b, a);
                addVertex(consumer, matrix4f, matrix3f,
                        x1 * zr1 * radius, y1 * zr1 * radius, z1 * radius,
                        x1 * zr1, y1 * zr1, z1, packedLight, r, g, b, a);
                addVertex(consumer, matrix4f, matrix3f,
                        x0 * zr1 * radius, y0 * zr1 * radius, z1 * radius,
                        x0 * zr1, y0 * zr1, z1, packedLight, r, g, b, a);
            }
        }
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f matrix4f, Matrix3f matrix3f,
                                   float x, float y, float z, float nx, float ny, float nz, int packedLight,
                                   int r, int g, int b, int a) {
        consumer.vertex(matrix4f, x, y, z)
                .color(r, g, b, a)
                .uv(0, 0)
                .overlayCoords(0)
                .uv2(packedLight)
                .normal(matrix3f, nx, ny, nz)
                .endVertex();
    }

    private static class ClientBloodOrbData {
        private final UUID id;
        private final Vec3 pos;
        private final double densityV;
        private final UUID ownerUUID;
        private final int lifetime;
        private int currentTick = 0;

        public ClientBloodOrbData(UUID id, Vec3 pos, double densityV, UUID ownerUUID, int lifetime) {
            this.id = id;
            this.pos = pos;
            this.densityV = densityV;
            this.ownerUUID = ownerUUID;
            this.lifetime = lifetime;
        }

        public void tick() { currentTick++; }
    }
}

