package com.pigicial.wikirenderer.mixin.screen;

import com.pigicial.wikirenderer.WikiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public class PictureInPictureRendererMixin {

    @Inject(method = "prepare", at = @At("HEAD"))
    private void wikirenderer$beginPipDraw(CallbackInfo ci) {
        if (WikiRenderer.inContainerScreenDraw) {
            WikiRenderer.inGuiPictureInPictureDraw = true;
        }
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void wikirenderer$endPipDraw(CallbackInfo ci) {
        WikiRenderer.inGuiPictureInPictureDraw = false;
    }
}
