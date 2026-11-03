package com.pigicial.wikirenderer.mixin.screen;

import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.render.screen.ContainerScreenPropertyBundle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Inject(method = "extractSlots", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/inventory/SlotGroup;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"), cancellable = true)
    private void wikirenderer$skipSlotHighlightBack(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (WikiRenderer.inContainerScreenDraw && ContainerScreenPropertyBundle.INSTANCE.hideItems.get()) {
            ci.cancel();
        }
    }
}
