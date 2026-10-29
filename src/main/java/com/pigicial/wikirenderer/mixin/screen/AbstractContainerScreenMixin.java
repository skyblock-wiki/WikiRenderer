package com.pigicial.wikirenderer.mixin.screen;

import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.render.screen.ContainerScreenPropertyBundle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void wikirenderer$skipSlotHighlightBack(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (WikiRenderer.inContainerScreenDraw && ContainerScreenPropertyBundle.INSTANCE.hideItems.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void wikirenderer$skipSlots(GuiGraphicsExtractor graphics, Slot slot, CallbackInfo ci) {
        if (WikiRenderer.inContainerScreenDraw && ContainerScreenPropertyBundle.INSTANCE.hideItems.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void wikirenderer$skipSlotHighlightFront(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (WikiRenderer.inContainerScreenDraw && ContainerScreenPropertyBundle.INSTANCE.hideItems.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void wikirenderer$skipTooltip(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (WikiRenderer.inContainerScreenDraw && ContainerScreenPropertyBundle.INSTANCE.hideItems.get()) {
            ci.cancel();
        }
    }
}
