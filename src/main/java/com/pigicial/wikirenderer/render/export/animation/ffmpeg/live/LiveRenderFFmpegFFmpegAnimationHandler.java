package com.pigicial.wikirenderer.render.export.animation.ffmpeg.live;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.property.GlobalProperties;
import com.pigicial.wikirenderer.render.Renderable;
import com.pigicial.wikirenderer.render.export.ExportPathSpec;
import com.pigicial.wikirenderer.render.export.ImageCropper;
import com.pigicial.wikirenderer.render.export.RenderableDispatcher;
import com.pigicial.wikirenderer.render.export.animation.AnimationFormat;
import com.pigicial.wikirenderer.render.export.animation.ffmpeg.FFmpegAnimationHandler;
import com.pigicial.wikirenderer.render.export.animation.ffmpeg.FFmpegDispatcher;
import com.pigicial.wikirenderer.screen.RenderScreen;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class LiveRenderFFmpegFFmpegAnimationHandler extends FFmpegAnimationHandler {

    private final FFmpegSession session;
    private final List<CompletableFuture<Void>> frameFileExportFutures = new ArrayList<>();
    private final Path tempData;

    public LiveRenderFFmpegFFmpegAnimationHandler(RenderScreen screen, Renderable<?> renderable, int framesToRender) {
        super(screen, renderable, framesToRender);
        try {
            Path rendersFolder = ExportPathSpec.exportRoot();

            File rendersFolderAsFile = rendersFolder.toFile();
            if (rendersFolderAsFile.mkdirs()) {
                WikiRenderer.LOGGER.info("Made renders folder {}", rendersFolderAsFile);
            }

            this.tempData = rendersFolder.resolve(this.framesFolderName + ".mov");
            int exportResolution = renderable.getExportResolution();
            this.session = new FFmpegSession(this.tempData, exportResolution, exportResolution);
            this.remainingAnimationFrames = framesToRender;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void renderAndSaveFrame(float effectiveTickDelta) {
        if (this.closed || this.remainingAnimationFrames <= 0) return;

        GlobalProperties globalProperties = GlobalProperties.get();
        if (globalProperties.syncTextureAnimationsToAnimation.get()) {
            Minecraft.getInstance().getAtlasManager().tick();
        }

        GpuTexture texture = RenderableDispatcher.drawIntoTexture(this.screen, this.renderable, effectiveTickDelta, screen.getTimeSinceCreationMs(), renderable.getExportResolution());
        WikiRenderer.skipWorldRender = true;

        // makes new file each frame
        CompletableFuture<Void> future = RenderableDispatcher.copyTextureIntoImage(texture)
                .whenComplete((_, _) -> texture.close())
                .thenAccept(image -> {
                    this.collectedCropData.add(ImageCropper.getCropData(image));
                    try {
                        this.session.pushFrame(image);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    image.close();
                });

        this.frameFileExportFutures.add(future);

        if (--this.remainingAnimationFrames == 0) {
            CompletableFuture.allOf(this.frameFileExportFutures.toArray(CompletableFuture[]::new))
                    .whenComplete((_, _) -> {
                        try {
                            this.frameFileExportFutures.clear();
                            this.session.close();
                            Minecraft.getInstance().getFramerateLimitTracker().setFramerateLimit(Minecraft.getInstance().options.framerateLimit().get());
                            this.exportFinalFromMaster(globalProperties.animationFormat, ImageCropper.getFFmpegCropSize(this.renderable, collectedCropData));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private void exportFinalFromMaster(AnimationFormat format, String cropFilter) {
        ExportPathSpec defaultExportPath = this.renderable.getExportPath();
        ExportPathSpec exportPath = defaultExportPath.differentFileName(renderable.getCustomFileName());

        File exportDirectory = exportPath.resolveOffset().toFile();
        if (exportDirectory.mkdirs()) {
            WikiRenderer.LOGGER.info("Made export directory {}", exportDirectory);
        }

        File animationFile = exportPath.resolveFile(format.extension);

        String ffmpegPath = FFmpegDispatcher.getResolvedOrFallbackFFmpegPath();
        List<String> args = new ArrayList<>(List.of(
                ffmpegPath,
                "-y",
                "-threads",
                String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors())),
                "-i", this.tempData.toString()
        ));

        boolean hasCrop = cropFilter != null && !cropFilter.isBlank();

        if (format == AnimationFormat.GIF) {
            args.add("-filter_complex");
            String cropNode = hasCrop ? "[0:v]" + cropFilter + "[cropped];[cropped]" : "[0:v]";
            String chain = cropNode + "format=rgba,split[split1][split2];" +
                           "[split1]drawbox=c=white@0.2:t=fill[bg];" +
                           "[bg][split2]overlay[v1];" +
                           "[v1]split[pal_in][out_in];" +
                           "[pal_in]palettegen=reserve_transparent=1:stats_mode=diff[p];" +
                           "[out_in][p]paletteuse=alpha_threshold=1:dither=bayer:bayer_scale=5";

            args.add(chain);
        } else if (hasCrop) {
            args.add("-vf");
            args.add(cropFilter);
        }

        if (format.ffmpegArguments.length != 0) {
            args.addAll(Arrays.asList(format.ffmpegArguments));
        }

        args.add(animationFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(args).redirectErrorStream(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        FFmpegDispatcher.parseFFmpegProgress(this, line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    File tempDataFile = tempData.toFile();
                    if (tempDataFile.exists()) {
                        if (tempDataFile.delete()) {
                            WikiRenderer.LOGGER.info("Deleted temporary live export file {}", tempDataFile);
                        }
                    }
                    return animationFile;
                } else {
                    throw new RuntimeException("FFmpeg failed with exit code " + exitCode);
                }
            } catch (Exception e) {
                throw new RuntimeException("FFmpeg export failed", e);
            }
        }).whenComplete((f, animationThrowable) -> this.finishAndCleanup(f, animationThrowable, null));
    }

    @Override
    protected void finishAndCleanup(@Nullable File animationFile, @Nullable Throwable error, @Nullable Path framesFolderToLinkTo) {
        super.finishAndCleanup(animationFile, error, framesFolderToLinkTo);
        try {
            this.session.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close FFmpeg session", e);
        }
    }

    @Override
    public void close() {
        super.close();
        try {
            this.session.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close FFmpeg session", e);
        }
    }
}