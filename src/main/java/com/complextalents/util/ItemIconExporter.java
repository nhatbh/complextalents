package com.complextalents.util;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@Mod.EventBusSubscriber(modid = com.complextalents.TalentsMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ItemIconExporter {

    private static final Queue<ItemStack> exportQueue = new LinkedList<>();
    private static File outputDirectory;
    private static int resolution = 64; // Default to 64x64
    private static int exportCounter = 0;

    public static void startExport(List<ItemStack> items, File folder, int size) {
        exportQueue.addAll(items);
        outputDirectory = folder;
        resolution = size;
        exportCounter = 0; // Reset counter for new batch

        // Delete existing mapping file if it exists to start fresh
        File mappingFile = new File(folder, "mapping.txt");
        if (mappingFile.exists()) mappingFile.delete();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || exportQueue.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            exportQueue.clear();
            return;
        }

        ItemStack stack = exportQueue.poll();
        if (stack != null) {
            renderAndSaveItem(stack, outputDirectory, resolution);
            
            if (exportQueue.isEmpty()) {
                if (mc.gui != null) {
                    mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("\u00A76[Complex Talents] \u00A7aFinished exporting all item icons to: " + outputDirectory.getAbsolutePath()));
                }
                com.complextalents.TalentsMod.LOGGER.info("Finished exporting all item icons to: {}", outputDirectory.getAbsolutePath());
            }
        }
    }

    /**
     * The Original Rendering Engine Approach - Fixed Z-Clipping
     */
    private static void renderAndSaveItem(ItemStack stack, File folder, int size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getItemRenderer() == null || mc.level == null) return;

        // 1. FRAMEBUFFER SETUP
        RenderTarget target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F); 
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        // 2. PROJECTION MATRIX (16x16 GUI Grid)
        RenderSystem.backupProjectionMatrix();
        Matrix4f projectionMatrix = new Matrix4f().setOrtho(0.0F, 16.0F, 16.0F, 0.0F, -1000.0F, 3000.0F);
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);

        // Reset the ModelView stack so it doesn't interfere
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        // 3. PROPER LIGHTING (Prevents 3D blocks from rendering pitch black)
        BakedModel bakedModel = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        boolean isFlatItem = !bakedModel.usesBlockLight();
        if (isFlatItem) {
            Lighting.setupForFlatItems();
        } else {
            Lighting.setupFor3DItems();
        }

        // 4. TRANSFORM AND RENDER
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack renderPose = new PoseStack();

        renderPose.pushPose();
        // THE FIX: Move to center (8, 8), and PUSH Z FORWARD (150). 
        // If Z is 0, the scale factor pushes the item out of the camera's clipping bounds.
        renderPose.translate(8.0F, 8.0F, 150.0F); 
        renderPose.scale(16.0F, -16.0F, 16.0F);

        mc.getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.GUI,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                renderPose,
                bufferSource,
                mc.level,
                0
        );
        renderPose.popPose();
        bufferSource.endBatch(); // Flush draw calls

        // 5. RESTORE DEFAULT LIGHTING
        Lighting.setupFor3DItems();

        // 6. CAPTURE & SAVE
        NativeImage nativeImage = new NativeImage(size, size, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        nativeImage.downloadTexture(0, false);
        nativeImage.flipY(); 

        if (!folder.exists()) folder.mkdirs();
        
        exportCounter++;
        String fileName = exportCounter + ".png";
        File outputFile = new File(folder, fileName);

        // Record mapping
        String itemName = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        try {
            java.nio.file.Files.writeString(
                new File(folder, "mapping.txt").toPath(), 
                exportCounter + ": " + itemName + System.lineSeparator(), 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            nativeImage.writeToFile(outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 7. CLEANUP
            nativeImage.close();
            target.destroyBuffers();
            
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
