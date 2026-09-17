package com.pigicial.wikirenderer.textures;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import com.pigicial.wikirenderer.render.item.ItemRenderable;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.ScreenSchedulerAndSaver;
import com.pigicial.wikirenderer.screen.WikiRendererUI;
import com.pigicial.wikirenderer.util.ClipboardUtil;
import com.pigicial.wikirenderer.util.Translate;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface TextureDataProvider {

    @NotNull
    Map<String, TextureData> getTextureData(Runnable rebuildCallback);

    void cacheTextureData(Runnable rebuildCallback);

    default void buildTextureGrabSection(RenderScreen screen, FlowLayout layout) {
        Map<String, TextureData> foundTextures = this.getTextureData(() -> Minecraft.getInstance().executeBlocking(() -> screen.guiRebuildScheduled = true));
        if (foundTextures.isEmpty()) return;

        WikiRendererUI.text(layout, "player_textures", true);

        for (Map.Entry<String, TextureData> entry : foundTextures.entrySet()) {
            String textureContext = entry.getKey(); // player or equipment
            TextureData textureData = entry.getValue();
            if (textureData == null) continue; // shouldn't be but just in case

            MinecraftTexturesPayload payload = textureData.payload();
            GameProfile profile = textureData.profile();

            for (Map.Entry<MinecraftProfileTexture.Type, MinecraftProfileTexture> textureEntry : payload.textures().entrySet()) {
                MinecraftProfileTexture.Type type = textureEntry.getKey();
                MinecraftProfileTexture texture = textureEntry.getValue();

                try (WikiRendererUI.RowBuilder builder = WikiRendererUI.autoNewLineRow(layout)) {
                    Component contextText = Translate.gui("texture_context." + textureContext);
                    Component typeText = Translate.gui("texture_type." + type.name().toLowerCase());
                    Component mergedText = Component.literal(contextText.getString() + " " + typeText.getString()); // jank

                    builder.row.child(UIComponents.label(mergedText).margins(Insets.of(5, 0, 0, 10)));

                    builder.row.child(UIComponents.button(Translate.gui("open_url"), button -> Util.getPlatform().openUri(texture.getUrl())));
                    builder.row.child(UIComponents.button(Translate.gui("copy_texture_id"), button -> {
                        screen.notify(Translate.gui("copied_texture_id_to_clipboard"));
                        ClipboardUtil.setClipboard(texture.getHash());
                    }));

                    builder.row.child(UIComponents.button(Translate.gui("copy_json"), button -> {
                        screen.notify(Translate.gui("copied_json_to_clipboard"));
                        ClipboardUtil.setClipboard(PlayerTextureUtils.GSON.toJson(payload));
                    }));

                    if (!(this instanceof ItemRenderable) && type == MinecraftProfileTexture.Type.SKIN) {
                        builder.row.child(UIComponents.button(Translate.gui("render_head"), button -> {
                            ItemStack head = PlayerTextureUtils.createPlayerHead(profile);
                            ScreenSchedulerAndSaver.setSavedScreen(screen);
                            ScreenSchedulerAndSaver.openImmediately(new RenderScreen(new ItemRenderable(head)));
                        }));
                    }
                }
            }
        }
    }
}
