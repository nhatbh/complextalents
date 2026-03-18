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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.io.File;
import java.io.IOException;

/**
 * Specialized utility for rendering item icons to PNG files.
 * MUST be called on the main render thread.
 */
public class ItemIconRenderer {

    /**
     * Renders an item to a PNG file.
     * 
     * @param stack  The item stack to render.
     * @param folder The destination folder.
     * @param size   The resolution of the output image.
     */
    public static void renderItemToFile(ItemStack stack, File folder, int size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getItemRenderer() == null || mc.level == null) return;

        // 1. Create a custom Framebuffer (RenderTarget)
        RenderTarget target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F); // Transparent background
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        // 2. Backup current projection and setup an Orthographic matrix for 2D UI rendering
        RenderSystem.backupProjectionMatrix();
        Matrix4f projectionMatrix = new Matrix4f().setOrtho(0.0F, 16.0F, 16.0F, 0.0F, -1000.0F, 3000.0F);
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        modelViewStack.translate(0.0F, 0.0F, 1000.0F); // Move forward to prevent Z-fighting/clipping
        RenderSystem.applyModelViewMatrix();

        // Setup generic GUI lighting
        Lighting.setupForFlatItems(); 

        // 3. Render the item
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack renderPose = new PoseStack();
        
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
        bufferSource.endBatch(); // Force the render queue to draw to the active framebuffer immediately

        // 4. Capture the pixels
        NativeImage nativeImage = new NativeImage(size, size, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        nativeImage.downloadTexture(0, false);
        nativeImage.flipY(); // OpenGL natively renders upside down compared to standard PNG coords

        // 5. Save the file
        if (!folder.exists()) folder.mkdirs();
        
        // Get safe registry name for the file
        String itemName = ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
        File outputFile = new File(folder, itemName + ".png");

        try {
            nativeImage.writeToFile(outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 6. Memory Cleanup & State Restoration (Crucial)
            nativeImage.close();
            target.destroyBuffers();
            
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            mc.getMainRenderTarget().bindWrite(true); // Return control to the main screen
        }
    }
}
