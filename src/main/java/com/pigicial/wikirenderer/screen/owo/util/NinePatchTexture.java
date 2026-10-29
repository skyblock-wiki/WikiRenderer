package com.pigicial.wikirenderer.screen.owo.util;

import com.google.gson.*;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.screen.owo.core.Color;
import com.pigicial.wikirenderer.screen.owo.core.OwoUIGraphics;
import com.pigicial.wikirenderer.screen.owo.core.PositionedRectangle;
import com.pigicial.wikirenderer.screen.owo.core.Size;
import net.minecraft.client.gui.GuiPipeline;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.BufferedReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class NinePatchTexture {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(NinePatchTexture.class, new Deserializer())
            .create();

    private final Identifier texture;
    private final int u;
    private final int v;
    private final Size textureSize;
    private final boolean repeat;

    @Nullable
    private final Size patchSize;

    @Nullable
    private final Size cornerPatchSize;

    @Nullable
    private final Size centerPatchSize;

    public NinePatchTexture(Identifier texture, int u, int v, Size textureSize, @Nullable Size patchSize, @Nullable Size cornerPatchSize, @Nullable Size centerPatchSize, boolean repeat) {
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.textureSize = textureSize;
        this.patchSize = patchSize;
        this.cornerPatchSize = cornerPatchSize;
        this.centerPatchSize = centerPatchSize;
        this.repeat = repeat;

        if (patchSize == null) {
            if ((cornerPatchSize != null && centerPatchSize == null)) {
                throw new IllegalStateException("Missing center Patch Size while providing corner Patch Size!");
            } else if ((cornerPatchSize == null && centerPatchSize != null)) {
                throw new IllegalStateException("Missing corner Patch Size while providing center Patch Size!");
            } else if (cornerPatchSize == null) {
                throw new IllegalStateException("Missing base patch Size or patch size for both corner and center!");
            }
        }
    }

    private Size cornerPatchSize() {
        return (this.cornerPatchSize != null) ? this.cornerPatchSize : this.patchSize;
    }

    private Size centerPatchSize() {
        return (this.centerPatchSize != null) ? this.centerPatchSize : this.patchSize;
    }

    public void draw(OwoUIGraphics context, PositionedRectangle rectangle) {
        this.draw(context, rectangle, Color.WHITE);
    }

    public void draw(OwoUIGraphics context, PositionedRectangle rectangle, Color color) {
        this.draw(context, rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height(), color);
    }

    public void draw(OwoUIGraphics context, int x, int y, int width, int height) {
        this.draw(context, x, y, width, height, Color.WHITE);
    }

    public void draw(OwoUIGraphics context, int x, int y, int width, int height, Color color) {
        this.draw(context, GuiPipeline.TEXTURED, x, y, width, height, color);
    }

    public void draw(OwoUIGraphics context, GuiPipeline pipeline, int x, int y, int width, int height) {
        this.draw(context, pipeline, x, y, width, height, Color.WHITE);
    }

    public void draw(OwoUIGraphics context, GuiPipeline pipeline, int x, int y, int width, int height, Color color) {
        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        context.blit(pipeline, this.texture, x, y, this.u, this.v, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height(), color.argb());
        context.blit(pipeline, this.texture, x + width - this.cornerPatchSize().width(), y, this.u + rightEdge, this.v, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height(), color.argb());
        context.blit(pipeline, this.texture, x, y + height - this.cornerPatchSize().height(), this.u, this.v + bottomEdge, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height(), color.argb());
        context.blit(pipeline, this.texture, x + width - this.cornerPatchSize().width(), y + height - this.cornerPatchSize().height(), this.u + rightEdge, this.v + bottomEdge, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height(), color.argb());

        if (this.repeat) {
            this.drawRepeated(context, pipeline, x, y, width, height, color);
        } else {
            this.drawStretched(context, pipeline, x, y, width, height, color);
        }
    }

    protected void drawStretched(OwoUIGraphics context, GuiPipeline pipeline, int x, int y, int width, int height, Color color) {
        int doubleCornerHeight = this.cornerPatchSize().height() * 2;
        int doubleCornerWidth = this.cornerPatchSize().width() * 2;

        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        if (width > doubleCornerWidth && height > doubleCornerHeight) {
            context.blit(pipeline, this.texture, x + this.cornerPatchSize().width(), y + this.cornerPatchSize().height(),
                this.u + this.cornerPatchSize().width(), this.v + this.cornerPatchSize().height(),
                width - doubleCornerWidth, height - doubleCornerHeight,
                this.centerPatchSize().width(), this.centerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
        }

        if (width > doubleCornerWidth) {
            context.blit(pipeline, this.texture, x + this.cornerPatchSize().width(), y,
                this.u + this.cornerPatchSize().width(), this.v,
                width - doubleCornerWidth, this.cornerPatchSize().height(),
                this.centerPatchSize().width(), this.cornerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
            context.blit(pipeline, this.texture, x + this.cornerPatchSize().width(), y + height - this.cornerPatchSize().height(),
                this.u + this.cornerPatchSize().width(), this.v + bottomEdge,
                width - doubleCornerWidth, this.cornerPatchSize().height(),
                this.centerPatchSize().width(), this.cornerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
        }

        if (height > doubleCornerHeight) {
            context.blit(pipeline, this.texture, x, y + this.cornerPatchSize().height(),
                this.u, this.v + this.cornerPatchSize().height(),
                this.cornerPatchSize().width(), height - doubleCornerHeight,
                this.cornerPatchSize().width(), this.centerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
            context.blit(pipeline, this.texture, x + width - this.cornerPatchSize().width(), y + this.cornerPatchSize().height(),
                this.u + rightEdge, this.v + this.cornerPatchSize().height(),
                this.cornerPatchSize().width(), height - doubleCornerHeight,
                this.cornerPatchSize().width(), this.centerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
        }
    }

    protected void drawRepeated(OwoUIGraphics context, GuiPipeline pipeline, int x, int y, int width, int height, Color color) {
        int doubleCornerHeight = this.cornerPatchSize().height() * 2;
        int doubleCornerWidth = this.cornerPatchSize().width() * 2;

        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        if (width > doubleCornerWidth && height > doubleCornerHeight) {
            int leftoverHeight = height - doubleCornerHeight;
            while (leftoverHeight > 0) {
                int drawHeight = Math.min(this.centerPatchSize().height(), leftoverHeight);

                int leftoverWidth = width - doubleCornerWidth;
                while (leftoverWidth > 0) {
                    int drawWidth = Math.min(this.centerPatchSize().width(), leftoverWidth);
                    context.blit(pipeline, this.texture,
                        x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                        this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                        drawWidth, drawHeight,
                        drawWidth, drawHeight,
                        this.textureSize.width(), this.textureSize.height(), color.argb());

                    leftoverWidth -= this.centerPatchSize().width();
                }
                leftoverHeight -= this.centerPatchSize().height();
            }
        }

        if (width > doubleCornerWidth) {
            int leftoverWidth = width - doubleCornerWidth;
            while (leftoverWidth > 0) {
                int drawWidth = Math.min(this.centerPatchSize().width(), leftoverWidth);

                context.blit(pipeline, this.texture, x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y,
                    this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v,
                    drawWidth, this.cornerPatchSize().height(),
                    drawWidth, this.cornerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height(), color.argb());
                context.blit(pipeline, this.texture, x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y + height - this.cornerPatchSize().height(),
                    this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v + bottomEdge,
                    drawWidth, this.cornerPatchSize().height(),
                    drawWidth, this.cornerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height(), color.argb());

                leftoverWidth -= this.centerPatchSize().width();
            }
        }

        if (height > doubleCornerHeight) {
            int leftoverHeight = height - doubleCornerHeight;
            while (leftoverHeight > 0) {
                int drawHeight = Math.min(this.centerPatchSize().height(), leftoverHeight);
                context.blit(pipeline, this.texture, x, y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                    this.u, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                    this.cornerPatchSize().width(), drawHeight,
                    this.cornerPatchSize().width(), drawHeight,
                    this.textureSize.width(), this.textureSize.height(), color.argb());
                context.blit(pipeline, this.texture, x + width - this.cornerPatchSize().width(), y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                    this.u + rightEdge, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                    this.cornerPatchSize().width(), drawHeight,
                    this.cornerPatchSize().width(), drawHeight,
                    this.textureSize.width(), this.textureSize.height(), color.argb());

                leftoverHeight -= this.centerPatchSize().height();
            }
        }
    }

    public static void draw(Identifier texture, OwoUIGraphics context, int x, int y, int width, int height) {
        draw(texture, context, GuiPipeline.TEXTURED, x, y, width, height);
    }

    public static void draw(Identifier texture, OwoUIGraphics context, int x, int y, int width, int height, Color color) {
        draw(texture, context, GuiPipeline.TEXTURED, x, y, width, height, color);
    }

    public static void draw(Identifier texture, OwoUIGraphics context, GuiPipeline pipeline, int x, int y, int width, int height) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, pipeline, x, y, width, height));
    }

    public static void draw(Identifier texture, OwoUIGraphics context, GuiPipeline pipeline, int x, int y, int width, int height, Color color) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, pipeline, x, y, width, height, color));
    }

    public static void draw(Identifier texture, OwoUIGraphics context, PositionedRectangle rectangle) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, rectangle));
    }

    public static void draw(Identifier texture, OwoUIGraphics context, PositionedRectangle rectangle, Color color) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, rectangle, color));
    }

    private static void ifPresent(Identifier texture, Consumer<NinePatchTexture> action) {
        NinePatchTexture ninePatchTexture = MetadataLoader.LOADED_TEXTURES.get(texture);
        if (ninePatchTexture != null) {
            action.accept(ninePatchTexture);
        }
    }

    private static class Deserializer implements JsonDeserializer<NinePatchTexture> {
        @Override
        public NinePatchTexture deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            Identifier texture = Identifier.parse(GsonHelper.getAsString(obj, "texture"));
            int u = GsonHelper.getAsInt(obj, "u", 0);
            int v = GsonHelper.getAsInt(obj, "v", 0);

            Size textureSize = parseSize(GsonHelper.getAsJsonObject(obj, "texture_size"));
            Size patchSize = obj.has("patch_size") ? parseSize(obj.getAsJsonObject("patch_size")) : null;
            Size cornerPatchSize = obj.has("corner_patch_size") ? parseSize(obj.getAsJsonObject("corner_patch_size")) : null;
            Size centerPatchSize = obj.has("center_patch_size") ? parseSize(obj.getAsJsonObject("center_patch_size")) : null;
            boolean repeat = GsonHelper.getAsBoolean(obj, "repeat", false);

            return new NinePatchTexture(texture, u, v, textureSize, patchSize, cornerPatchSize, centerPatchSize, repeat);
        }

        private static Size parseSize(JsonObject obj) {
            return Size.of(GsonHelper.getAsInt(obj, "width"), GsonHelper.getAsInt(obj, "height"));
        }
    }

    public static class MetadataLoader extends SimplePreparableReloadListener<Map<Identifier, NinePatchTexture>> implements PreparableReloadListener {

        private static final FileToIdConverter CONVERTER = FileToIdConverter.json("nine_patch_textures");
        private static final Map<Identifier, NinePatchTexture> LOADED_TEXTURES = new HashMap<>();

        @Override
        protected Map<Identifier, NinePatchTexture> prepare(@NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
            Map<Identifier, NinePatchTexture> result = new HashMap<>();

            for (Map.Entry<Identifier, Resource> entry : CONVERTER.listMatchingResources(resourceManager).entrySet()) {
                Identifier fileId = entry.getKey();
                Identifier id = CONVERTER.fileToId(fileId);

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    NinePatchTexture texture = GSON.fromJson(reader, NinePatchTexture.class);
                    result.put(id, texture);
                } catch (Exception e) {
                    WikiRenderer.LOGGER.error("Couldn't parse nine patch texture {}", id, e);
                }
            }

            return result;
        }

        @Override
        protected void apply(Map<Identifier, NinePatchTexture> prepared, @NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
            LOADED_TEXTURES.clear();
            LOADED_TEXTURES.putAll(prepared);
        }
    }

}
