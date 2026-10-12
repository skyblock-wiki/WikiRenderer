package com.pigicial.wikirenderer.render.export;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.property.CroppablePropertyBundle;
import com.pigicial.wikirenderer.property.GlobalProperties;
import com.pigicial.wikirenderer.render.Renderable;
import com.pigicial.wikirenderer.render.area.AreaRenderable;
import com.pigicial.wikirenderer.render.area.side_view.MinimapCalibratorData;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.util.DrawProjectionDataCache;
import com.pigicial.wikirenderer.util.DrawType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RenderableDispatcher {

    public static final Map<DrawType, DrawProjectionDataCache> PROJECTION_CACHE = new HashMap<>();

    private static final ProjectionMatrixBuffer PROJECTION_MATRIX_BUFFER = new ProjectionMatrixBuffer("RenderableDispatcher");
    private static final Matrix4f ORTHOGRAPHIC_MATRIX = new Matrix4f();
    private static RenderTarget previewTarget = null;

    public static void drawIntoActiveFramebuffer(DrawType drawType, RenderScreen renderScreen, Renderable<?> renderable, float aspectRatio, float tickDelta, long timeSinceCreationMs, @Nullable Consumer<Matrix4fStack> transformer) {
        renderable.prepare();

        // view matrix = position/rotation/scale of camera
        // model/object matrix = position/rotation/scale of the model/object
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        if (transformer != null) transformer.accept(modelViewStack);

        WikiRenderer.currentDrawType = drawType;
        renderable.getProperties().applyToViewMatrix(renderable, modelViewStack);

        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        Matrix4f projectionMatrix = ORTHOGRAPHIC_MATRIX.setOrtho(-aspectRatio, aspectRatio, 1, -1, -1000, 1000, zZeroToOne);
        WikiRenderer.beginRenderableDraw(PROJECTION_MATRIX_BUFFER, projectionMatrix, drawType);
        WikiRenderer.setSortingMethod(projectionMatrix, modelViewStack);

        PROJECTION_CACHE.put(drawType, new DrawProjectionDataCache(new Matrix4f(projectionMatrix), new Matrix4f(modelViewStack), WikiRenderer.mainTargetOverride.width, WikiRenderer.mainTargetOverride.height));

        renderable.drawSubmittedRenderFeatures();

        renderable.setupLighting();
        renderable.emitVerticesThenDraw(renderScreen, modelViewStack, new PoseStack(), tickDelta, timeSinceCreationMs);
        renderable.drawSubmittedRenderFeatures();

        WikiRenderer.endRenderableDraw();
        modelViewStack.popMatrix();
        renderable.cleanUp();
    }

    public static RenderTarget drawPreview(RenderScreen renderScreen, Renderable<?> renderable, float tickDelta, long timeSinceCreationMs, @Nullable Consumer<Matrix4fStack> transformer) {
        Window window = Minecraft.getInstance().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        if (!renderable.renderPreviewToEntireScreenWidth()) {
            int widthAvailable = (renderScreen.width - (renderScreen.viewportEndX - renderScreen.viewportBeginX)) * window.getGuiScale();
            width -= widthAvailable;
        }

        if (previewTarget == null) {
            previewTarget = new TextureTarget("WikiRenderer RenderableDispatcher Preview Framebuffer", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        } else {
            if (previewTarget.width != width || previewTarget.height != height) {
                previewTarget.resize(width, height);
            }
        }
        float aspectRatio = width / (float) height;

        GlobalProperties globalProperties = GlobalProperties.get();
        int backgroundColor = globalProperties.showBackgroundColorInExports.get() ? globalProperties.backgroundColor : 0;
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                Objects.requireNonNull(previewTarget.getColorTexture()),
                new Vector4f(backgroundColor),
                Objects.requireNonNull(previewTarget.getDepthTexture()),
                0.0
        );

        WikiRenderer.mainTargetOverride = previewTarget;
        RenderableDispatcher.drawIntoActiveFramebuffer(DrawType.PREVIEW, renderScreen, renderable, aspectRatio, tickDelta, timeSinceCreationMs, transformer);
        WikiRenderer.mainTargetOverride = null;

        return previewTarget;
    }

    public static GpuTexture drawIntoTexture(RenderScreen renderScreen, Renderable<?> renderable, float tickDelta, long timeSinceCreationMs, int size) {
        int width = renderable.optionallyOverrideExportWidth(size);
        int height = renderable.optionallyOverrideExportHeight(size);
        float aspectRatio = width / (float) height;
        TextureTarget target = new TextureTarget("WikiRenderer RenderableDispatcher.drawIntoTexture Framebuffer", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);

        GlobalProperties globalProperties = GlobalProperties.get();
        int backgroundColor = globalProperties.showBackgroundColorInExports.get() ? globalProperties.backgroundColor : 0;
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                Objects.requireNonNull(target.getColorTexture()),
                new Vector4f(backgroundColor),
                Objects.requireNonNull(target.getDepthTexture()),
                0.0
        );

        WikiRenderer.mainTargetOverride = target;
        RenderableDispatcher.drawIntoActiveFramebuffer(DrawType.EXPORT, renderScreen, renderable, aspectRatio, tickDelta, timeSinceCreationMs, null);
        WikiRenderer.mainTargetOverride = null;

        // Release depth attachment and FBO to save on VRAM - we only need
        // the color attachment texture to later turn into an image
        GpuTexture texture = RenderableDispatcher.cloneColorAttachment(target);
        target.destroyBuffers();
        return texture;
    }

    public static CompletableFuture<NativeImage> drawIntoImage(RenderScreen renderScreen, Renderable<?> renderable, float tickDelta, long timeSinceCreationMs, int size, boolean crop, Consumer<MinimapCalibratorData> calibrationDataCallback) {
        return drawIntoImage(renderScreen, renderable, tickDelta, timeSinceCreationMs, size, size, 0, crop, calibrationDataCallback);
    }

    public static CompletableFuture<NativeImage> drawIntoImage(RenderScreen renderScreen, Renderable<?> renderable, float tickDelta, long timeSinceCreationMs, int size, int targetSize, int iterationIndex, boolean crop, Consumer<MinimapCalibratorData> calibrationDataCallback) {
        GpuTexture texture = drawIntoTexture(renderScreen, renderable, tickDelta, timeSinceCreationMs, size);
        CompletableFuture<NativeImage> image = copyTextureIntoImage(texture).handle((i, t) -> {
            texture.close();
            if (t != null) {
                if (i != null) {
                    i.close();
                }
                throw new RuntimeException(t);
            }
            return i;
        });

        boolean sideRendering = renderable instanceof AreaRenderable areaRenderable && areaRenderable.getProperties().perPixel90DegreeRendering.get();
        boolean exportMinimapData = calibrationDataCallback != null && sideRendering;

        if (!crop && exportMinimapData) {
            image = image.thenApply(i -> {
                MinimapCalibratorData calibrationData = MinimapCalibratorData.getCalibrationData((AreaRenderable) renderable, null, i);
                calibrationDataCallback.accept(calibrationData);
                return i;
            });
        }

        if (crop) {
            // resize image to target height by regenerating it with an increased size
            image = image.thenApply(uncropped -> {
                CropData cropData = ImageCropper.getCropData(uncropped);
                NativeImage cropped = ImageCropper.cropTransparentAndCloseSource(uncropped, cropData);
                // uncropped no longer valid by this point

                if (exportMinimapData) {
                    MinimapCalibratorData calibrationData = MinimapCalibratorData.getCalibrationData((AreaRenderable) renderable, cropData, cropped);
                    calibrationDataCallback.accept(calibrationData);
                }

                return cropped;
            }).thenCompose(croppedImage -> {
                CroppablePropertyBundle croppablePropertyBundle = (CroppablePropertyBundle) renderable.getProperties();
                ImageRescaleMode rescaleMode = croppablePropertyBundle.getRescaleMode().get();
                if (!croppablePropertyBundle.allowForRescaling()) {
                    rescaleMode = ImageRescaleMode.DISABLED;
                }

                int axisSize = switch (rescaleMode) {
                    case VERTICAL -> croppedImage.getHeight();
                    case HORIZONTAL -> croppedImage.getWidth();
                    case SHORTER_SIDE -> Math.min(croppedImage.getWidth(), croppedImage.getHeight());
                    case LONGER_SIDE -> Math.max(croppedImage.getWidth(), croppedImage.getHeight());
                    case DISABLED -> 0;
                };

                double multiplier = (double) size / (double) axisSize;
                int newSize = (int) Math.ceil(targetSize * multiplier);

                boolean rescalingDisabled = rescaleMode == ImageRescaleMode.DISABLED;
                boolean sameSize = axisSize == targetSize;
                boolean likelyOscillating = iterationIndex > 2;
                boolean batchSecondPass = WikiRenderer.inBatchRender && iterationIndex > 0;
                boolean isAreaTopdown = renderable instanceof AreaRenderable areaRenderable && areaRenderable.getProperties().perPixel90DegreeRendering.get();

                boolean dontRescale = rescalingDisabled || sameSize || likelyOscillating || batchSecondPass || isAreaTopdown;

                int maxTextureSize = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
                boolean tooLarge = newSize >= maxTextureSize;
                if (tooLarge && !dontRescale) {
                    croppedImage.close();
                    return CompletableFuture.failedFuture(new RuntimeException("Failed to rescale image (too big, " + newSize + " >= max " + maxTextureSize + ")"));
                }

                if (dontRescale) {
                    return CompletableFuture.completedFuture(croppedImage);
                } else {
                    croppedImage.close();
                    return drawIntoImage(renderScreen, renderable, tickDelta, timeSinceCreationMs, newSize, targetSize, iterationIndex + 1, true, null)
                            .thenApply(ImageCropper::cropTransparentAndCloseSource);
                }
            });
        }

        return image;
    }

    /**
     * Copies the given color attachment from video
     * memory in to system memory, wrapped in a {@link NativeImage}
     *
     * @param gpuTexture The texture to copy
     * @return The created image
     */

    public static CompletableFuture<NativeImage> copyTextureIntoImage(@NotNull GpuTexture gpuTexture) {
        CompletableFuture<NativeImage> future = new CompletableFuture<>();

        int width = gpuTexture.getWidth(0);
        int height = gpuTexture.getHeight(0);

        // Optimized version of vanilla's ScreenshotRecorder.takeScreenshot
        // that simply copies an RGBA8 GpuTexture's contents to an RGBA NativeImage, with vertical flipping.

        // Color attachments [in vanilla] are always RGBA8, therefore != RGBA8 implies non-color attachment
        if (gpuTexture.getFormat() != GpuFormat.RGBA8_UNORM) {
            throw new IllegalStateException("Tried to copy non-compatible texture into image");
        }

        try {
            GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(() -> "WikiRenderer RenderableDispatcher.copyTextureIntoImage buffer", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 4L * width * height);
            try {
                CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
                commandEncoder.copyTextureToBuffer(gpuTexture, gpuBuffer, 0, 0);
                RenderSystem.queueFencedTask(() -> {
                    try (GpuBufferSlice.MappedView mappedView = gpuBuffer.map(true, false)) {
                        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);

                        // Skip redundant safety checks, do the memory copies directly.
                        long stride = 4L * width;
                        long srcBuf = MemoryUtil.memAddress(mappedView.data());
                        long dstBuf = nativeImage.getPointer();

                        long src = srcBuf;
                        long dst = dstBuf + stride * (height - 1);

                        for (int y = 0; y < height; y++) {
                            MemoryUtil.memCopy(src, dst, stride);
                            src += stride;
                            dst -= stride;
                        }

                        future.complete(nativeImage);
                    } catch (Throwable throwable) {
                        future.completeExceptionally(throwable);
                    } finally {
                        gpuBuffer.close();
                    }
                });
            } catch (Throwable throwable) {
                gpuBuffer.close();
                future.completeExceptionally(throwable);
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }

        return future;
    }

    private static GpuTexture cloneColorAttachment(RenderTarget renderTarget) {
        GpuTexture original = renderTarget.getColorTexture();
        assert original != null;

        GpuTexture copy = RenderSystem.getDevice().createTexture(() -> "[IsometricRenders] Copy of: " + original.getLabel(),
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                GpuFormat.RGBA8_UNORM, renderTarget.width, renderTarget.height, 1, 1);

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(original, copy, 0, 0, 0, 0, 0, renderTarget.width, renderTarget.height);

        return copy;
    }
}
