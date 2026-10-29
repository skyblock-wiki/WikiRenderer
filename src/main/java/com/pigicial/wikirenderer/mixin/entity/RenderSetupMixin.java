package com.pigicial.wikirenderer.mixin.entity;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.pigicial.wikirenderer.WikiRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.pipeline.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(RenderSetup.class)
public abstract class RenderSetupMixin {

    @Final
    @Shadow
    Function<@Nullable OitStage, RenderPipeline> pipelineProvider;

    // Fixes player skins sometimes having an extra line on the top (only appears at certain scales/rotations/positions)
    @Redirect(
            method = "prepareTextures",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderSetup$TextureBinding;sampler()Ljava/util/function/Supplier;")
    )
    private Supplier<GpuSampler> wikirenderer$overrideSampler(RenderSetup.TextureBinding instance) {
        GpuSampler original = instance.sampler().get();
        // todo: test this swirl thing
        if (WikiRenderer.inEntityDraw && original == RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST) && pipelineProvider.apply(OitStage.TRANSMITTANCE).getLocation().getPath().contains("energy_swirl")) {
            return () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }

        return instance.sampler();
    }
}
