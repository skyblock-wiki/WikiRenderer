package com.pigicial.wikirenderer.render.export;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.pigicial.wikirenderer.WikiRenderer;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.pipeline.RenderPipelines;
import net.minecraft.client.renderer.pipeline.variant.TextRenderPipelineVariant;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

public class CustomRenderPipelines {
    // copied from RenderPipelines.terrain but with a different vertex shader to fix transparency issues
    public static RenderPipeline terrain(final RenderPipelines.TerrainParameters params) {
        return RenderPipelines.dynamicPipeline(
                params,
                "wikirenderer_terrain_no_transparency",
                parameters -> {
                    RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                            .withBindGroupLayout(BindGroupLayouts.FOG)
                            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                            .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withDepthStencilState(DepthStencilState.DEFAULT)
                            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                            .withBindGroupLayout(BindGroupLayouts.TERRAIN_INFO)
                            .withFragmentShader(Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "core_terrain_no_transparency"))
                            .withFragmentShader("core/terrain");
                    if (parameters.multiDraw()) {
                        builder.withVertexBinding(1, DefaultVertexFormat.CHUNK_DATA_INSTANCED).withShaderDefine("MULTIDRAW_TERRAIN");
                    } else {
                        builder.withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION);
                    }

                    if (parameters.variant().hasAlphaCutout()) {
                        builder.withShaderDefine("ALPHA_CUTOUT", parameters.variant().alphaCutoutThreshold());
                    }

                    String locationPostfix = parameters.variant().name()
                                             + (parameters.improvedFog() ? "_improved_fog" : "")
                                             + (parameters.multiDraw() ? "_multi_draw" : "")
                                             + (parameters.wireframe() ? "_wireframe" : "");
                    return RenderPipelines.finishDynamicPipeline(builder, "terrain", parameters.variant().supportsOit(), parameters.oitStage(), nonOitBuilder -> {
                        nonOitBuilder.withBindGroupLayout(BindGroupLayouts.SAMPLER2);
                        if (!parameters.wireframe()) {
                            nonOitBuilder.withColorTargetState(parameters.variant().nonOitColorTargetState());
                            if (parameters.improvedFog()) {
                                builder.withSnippet(RenderPipelines.IMPROVED_FOG_SNIPPET);
                            }
                        } else {
                            nonOitBuilder.withPolygonMode(PolygonMode.WIREFRAME).withColorTargetState(ColorTargetState.DEFAULT);
                        }
                    }, null, oitAccumulateBuilder -> oitAccumulateBuilder.withSnippet(RenderPipelines.IMPROVED_FOG_SNIPPET).withBindGroupLayout(BindGroupLayouts.SAMPLER2), locationPostfix);
                }
        );
    }

    /*
    // to fix leaves with transparent leaves turned off
    public static final RenderPipeline CORE_SOLID_TERRAIN_NO_TRANSPARENCY = RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET, RenderPipelines.SOLID_SNIPPET, RenderPipelines.SINGLEDRAW_TERRAIN_SNIPPET)
            .withLocation("pipeline/wikirenderer_solid_terrain_no_transparency")
            .withVertexShader("core/terrain")
            .withFragmentShader(Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "core_terrain_no_transparency"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline CORE_CUTOUT_TERRAIN_NO_TRANSPARENCY = RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET, RenderPipelines.CUTOUT_SNIPPET, RenderPipelines.SINGLEDRAW_TERRAIN_SNIPPET)
            .withLocation("pipeline/wikirenderer_cutout_terrain_no_transparency")
            .withVertexShader("core/terrain")
            .withFragmentShader(Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "core_terrain_no_transparency"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    // to fix leaves with transparent leaves turned off
    public static final RenderPipeline CORE_SOLID_TERRAIN_MULTIDRAW_NO_TRANSPARENCY = RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET, RenderPipelines.SOLID_SNIPPET, RenderPipelines.MULTIDRAW_TERRAIN_SNIPPET)
            .withLocation("pipeline/wikirenderer_solid_terrain_multidraw_no_transparency")
            .withVertexShader("core/terrain")
            .withFragmentShader(Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "core_terrain_no_transparency"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline CORE_CUTOUT_TERRAIN_MULTIDRAW_NO_TRANSPARENCY = RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET, RenderPipelines.CUTOUT_SNIPPET, RenderPipelines.MULTIDRAW_TERRAIN_SNIPPET)
            .withLocation("pipeline/wikirenderer_cutout_terrain_multidraw_no_transparency")
            .withVertexShader("core/terrain")
            .withFragmentShader(Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "core_terrain_no_transparency"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

     */

    // based on RenderPipelines.TEXT, just without SAMPLER2
    /*
    public static final RenderPipeline ITEM_FRAME_MAP_FULL_BRIGHTNESS = RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
            .withLocation("pipeline/wikirenderer_item_frame_custom_brightness")
            .withVertexShader(Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "item_frame_full_bright"))
            .withFragmentShader("core/text")
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR)
            .build();
     */

    private static final Function<Identifier, RenderType> ITEM_FRAME_RENDER_TYPE_CACHE = Util.memoize(
            identifier -> RenderType.create(
                    "wikirenderer_item_frame_brightness_override",
                    RenderSetup.builder(stage -> RenderPipelines.text(new RenderPipelines.TextParameters(TextRenderPipelineVariant.GUI, stage)))
                            .withTexture("Sampler0", identifier)
                            .createRenderSetup()
            )
    );

    public static RenderType getCustomMapPipeline(Identifier id) {
        return ITEM_FRAME_RENDER_TYPE_CACHE.apply(id);
    }

    public static void register() {
        //RenderPipelines.register(CORE_CUTOUT_TERRAIN_NO_TRANSPARENCY);
        //RenderPipelines.register(CORE_SOLID_TERRAIN_NO_TRANSPARENCY);
        // RenderPipelines.register(ITEM_FRAME_MAP_FULL_BRIGHTNESS);
    }
}
