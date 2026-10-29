package com.pigicial.wikirenderer.mixin.screen;

import com.pigicial.wikirenderer.WikiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void wikirenderer$skipBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (WikiRenderer.inContainerScreenDraw) {
            ci.cancel();
        }
    }
}
