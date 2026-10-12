package com.pigicial.wikirenderer.render.export.animation.gifski;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.property.GlobalProperties;
import com.pigicial.wikirenderer.property.Property;
import com.pigicial.wikirenderer.render.Renderable;
import com.pigicial.wikirenderer.render.export.*;
import com.pigicial.wikirenderer.render.export.animation.AnimationHandler;
import com.pigicial.wikirenderer.render.skyblock.frame_based.DyedArmorFrameBasedRenderable;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.util.Translate;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MemoryBasedGifskiAnimationHandler extends AnimationHandler {
    private final List<CompletableFuture<NativeImage>> frameExportFutures = new ArrayList<>();

    public MemoryBasedGifskiAnimationHandler(RenderScreen screen, Renderable<?> renderable, int framesToRender) {
        super(screen, renderable, framesToRender);
    }

    public void renderAndSaveFrame(float effectiveTickDelta) {
        if (this.closed || this.remainingAnimationFrames <= 0) return;

        if (GlobalProperties.get().syncTextureAnimationsToAnimation.get()) {
            Minecraft.getInstance().getAtlasManager().tick();
        }

        WikiRenderer.skipWorldRender = true;
        CompletableFuture<NativeImage> future;
        if (renderable instanceof DyedArmorFrameBasedRenderable dyedArmorFrameBasedRenderable) {
            Property<ImageRescaleMode> rescaleMode = dyedArmorFrameBasedRenderable.getProperties().getRescaleMode();
            ImageRescaleMode currentRescaleMode = rescaleMode.get();
            // hardcode proper skyblock animated armor set rescaling

            rescaleMode.set(ImageRescaleMode.LONGER_SIDE);
            future = RenderableDispatcher.drawIntoImage(this.screen, this.renderable, effectiveTickDelta, screen.getTimeSinceCreationMs(), renderable.getExportResolution(), true, null)
                    .thenApply(image -> {
                        this.collectedCropData.add(ImageCropper.getCropData(image));
                        return image;
                    });
            rescaleMode.set(currentRescaleMode);
        } else {
            GpuTexture texture = RenderableDispatcher.drawIntoTexture(this.screen, this.renderable, effectiveTickDelta, screen.getTimeSinceCreationMs(), renderable.getExportResolution());
            future = RenderableDispatcher.copyTextureIntoImage(texture)
                    .whenComplete((_, _) -> texture.close())
                    .thenApply(image -> {
                        this.collectedCropData.add(ImageCropper.getCropData(image));
                        return image;
                    });
        }

        this.frameExportFutures.add(future);

        if (--this.remainingAnimationFrames == 0) {
            Minecraft.getInstance().getFramerateLimitTracker().setFramerateLimit(Minecraft.getInstance().options.framerateLimit().get());
            CompletableFuture.allOf(frameExportFutures.toArray(CompletableFuture[]::new))
                    .whenComplete((v, t) -> {
                        List<CompletableFuture<File>> fileFutures = new ArrayList<>();

                        GlobalProperties globalProperties = GlobalProperties.get();
                        Boolean overwriteValue = globalProperties.overwriteLatest.get();
                        globalProperties.overwriteLatest.set(false);

                        CropData cropData = ImageCropper.combineCropDataIfNecessary(this.renderable, this.collectedCropData);

                        for (int i = 0, frameExportFuturesSize = frameExportFutures.size(); i < frameExportFuturesSize; i++) {
                            NativeImage rawImage = frameExportFutures.get(i).join();
                            NativeImage image = cropData == null ? rawImage : ImageCropper.cropTransparentAndCloseSource(rawImage, cropData);

                            fileFutures.add(FileIO.saveImage(image, ExportPathSpec.forced(this.framesFolderName, "seq_" + i))
                                    .whenComplete((f, t_) -> image.close()));
                        }

                        this.mergeFilesIntoFinalResult(fileFutures, overwriteValue);
                    });
        }
    }

    protected final void mergeFilesIntoFinalResult(List<CompletableFuture<File>> fileFutures, boolean overwriteValue) {
        this.finished = true;
        ExportPathSpec defaultExportPath = this.renderable.getExportPath();
        ExportPathSpec exportPath = defaultExportPath.differentFileName(renderable.getCustomFileName());

        CompletableFuture.allOf(fileFutures.toArray(CompletableFuture[]::new))
                .whenComplete((v_, throwable) -> {
                    GlobalProperties globalProperties = GlobalProperties.get();
                    globalProperties.overwriteLatest.set(overwriteValue);

                    boolean keepingFiles = globalProperties.saveIndividualFrames.get();
                    if (throwable != null || closed) {
                        FileIO.deleteSequenceFilesFromPath(this.framesFolder);
                        return;
                    }

                    this.screen.exportAnimationButton.setMessage(Translate.gui("converting"));
                    Minecraft.getInstance().execute(() -> screen.notify(Translate.gui("converting_image_sequence")));

                    GifskiDispatcher.exportAnimation(
                            exportPath,
                            this.framesFolder,
                            this
                    ).whenComplete((animationFile, animationThrowable) -> {
                        Path framesFolderToLinkTo = keepingFiles ? this.framesFolder : null;
                        this.finishAndCleanup(animationFile, animationThrowable, framesFolderToLinkTo);
                    });
                });
    }
}