package com.pigicial.wikirenderer.mixin.screen;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.pigicial.wikirenderer.WikiRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// allows item atlases to render properly during gui screen renders without having the custom model view stack data applied to it
@Mixin(RenderType.class)
public class RenderTypeMixin {

    @Inject(method = "writeDynamicTransforms", at = @At("HEAD"))
    private void wikirenderer$onDraw(Matrix4f modelViewMatrix, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (WikiRenderer.inContainerScreenDraw && (WikiRenderer.inGuiItemAtlasDraw || WikiRenderer.inGuiPictureInPictureDraw)) {
            modelViewMatrix.identity();
        }
    }
}
