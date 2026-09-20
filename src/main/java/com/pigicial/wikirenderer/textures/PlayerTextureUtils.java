package com.pigicial.wikirenderer.textures;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.services.response.MinecraftTexturesPayload;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.util.NullSafeUUIDTypeAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;

public class PlayerTextureUtils {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new NullSafeUUIDTypeAdapter())
            .setPrettyPrinting()
            .create();

    @Nullable
    public static TextureData getTextureDataFromPlayerHead(ItemStack itemStack) {
        ResolvableProfile profile = itemStack.get(DataComponents.PROFILE);
        if (profile == null) return null;

        TextureData data = getTextureDataFromGameProfile(profile.partialProfile());
        if (data != null) return data;

        PlayerSkinRenderCache.RenderInfo renderInfo = Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile);
        return getTextureDataFromGameProfile(renderInfo.gameProfile());
    }

    @Nullable
    public static TextureData getTextureDataFromGameProfile(GameProfile gameProfile) {
        try {
            Collection<Property> textures = gameProfile.properties().get("textures");
            if (textures.isEmpty()) {
                return null;
            }

            String skin = textures.iterator().next().value();
            byte[] byteArray;
            try {
                byteArray = Base64.getDecoder().decode(skin);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
            String decodedSkin = new String(byteArray, StandardCharsets.UTF_8);

            return new TextureData(GSON.fromJson(decodedSkin, MinecraftTexturesPayload.class), gameProfile);
        } catch (Exception e) {
            WikiRenderer.LOGGER.error("Error when grabbing texture data from game profile", e);
            return null;
        }
    }

    public static ItemStack createPlayerHead(GameProfile gameProfile) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gameProfile));
        return stack;
    }

    public static GameProfile createTexturedGameProfileFromID(String texture) {
        String textureUrl = "https://textures.minecraft.net/texture/" + texture;
        String json = String.format("{\"textures\":{\"SKIN\":{\"url\":\"%s\"}}}", textureUrl);
        String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        Multimap<String, Property> propertyMap = ArrayListMultimap.create();
        propertyMap.put("textures", new Property("textures", base64));

        byte[] textureHash = texture.getBytes(StandardCharsets.UTF_8);
        return new GameProfile(UUID.nameUUIDFromBytes(textureHash), "WikiRendererTexture/" + texture, new PropertyMap(propertyMap));
    }

    public static GameProfile createTexturedGameProfileFromBase64(String base64) {
        Multimap<String, Property> propertyMap = ArrayListMultimap.create();
        propertyMap.put("textures", new Property("textures", base64));

        byte[] textureHash = base64.getBytes(StandardCharsets.UTF_8);
        return new GameProfile(UUID.nameUUIDFromBytes(textureHash), "WikiRendererTexture/" + base64, new PropertyMap(propertyMap));
    }
}
