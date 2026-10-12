package com.pigicial.wikirenderer.mixin.texture;

import com.pigicial.wikirenderer.property.GlobalProperties;
import com.pigicial.wikirenderer.screen.RenderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtlasManager.class)
public class AtlasManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void stopTick(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof RenderScreen && !GlobalProperties.get().tickTextureAnimations.get()) {
            ci.cancel();
        }
    }

}
