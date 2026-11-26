package com.pigicial.wikirenderer.render.entity.player;

import com.mojang.authlib.GameProfile;
import com.pigicial.wikirenderer.textures.PlayerTextureUtils;
import com.pigicial.wikirenderer.textures.TextureData;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.ItemActivation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class RenderablePlayerEntity extends LocalPlayer {

    private final CompletableFuture<TextureData> skinGrabber;
    protected PlayerSkin skinTextures;

    public RenderablePlayerEntity(GameProfile profile, ProfileFetchMode fetchMode) {
        super(
                Minecraft.getInstance(),
                Objects.requireNonNull(Minecraft.getInstance().level),
                new ClientPacketListener(Minecraft.getInstance(),
                        new net.minecraft.network.Connection(PacketFlow.CLIENTBOUND),
                        new CommonListenerCookie(
                                new LevelLoadTracker(0),
                                profile, new WorldSessionTelemetryManager(TelemetryEventSender.DISABLED, false, Duration.ZERO, "", null),
                                Minecraft.getInstance().level.registryAccess().freeze(),
                                Minecraft.getInstance().level.enabledFeatures(),
                                "Wisp Forest Enterprises", null, null, Map.of(), null, Map.of(), ServerLinks.EMPTY, Map.of(),
                                true, null
                        )),
                new StatsCounter(),
                new ClientRecipeBook(),
                Input.EMPTY,
                false,
                new ChatAbilities.Builder().build(),
                new ItemActivation()
        );

        this.setId(Integer.MAX_VALUE);

        if (profile.name().equals("Steve")) {
            skinTextures = DefaultPlayerSkin.getDefaultSkin();
            skinGrabber = CompletableFuture.completedFuture(null);
        } else {
            this.skinTextures = DefaultPlayerSkin.get(profile);
            this.skinGrabber = CompletableFuture.supplyAsync(() -> {
                ProfileResolver profileResolver = Minecraft.getInstance().services().profileResolver();
                return switch (fetchMode) {
                    case NAME -> profileResolver.fetchByName(profile.name()).orElse(profile);
                    case UUID -> profileResolver.fetchById(profile.id()).orElse(profile);
                    case TEXTURE -> {
                        ClientPacketListener connection = Minecraft.getInstance().getConnection();
                        if (connection != null) {
                            PlayerInfo playerInfo = connection.getPlayerInfo(profile.id());
                            yield playerInfo != null ? playerInfo.getProfile() : profile;
                        } else {
                            yield profile;
                        }
                    }
                };
            }, Util.backgroundExecutor()).thenCompose(completeProfile -> {
                this.skinTextures = DefaultPlayerSkin.get(completeProfile);

                // Step C: Return the skin manager's future (this is why we use thenCompose)
                return this.minecraft.getSkinManager().get(completeProfile).thenApply(optionalSkin -> {
                    this.skinTextures = optionalSkin.orElse(DefaultPlayerSkin.get(completeProfile));
                    return PlayerTextureUtils.getTextureDataFromGameProfile(completeProfile);
                });
            });
        }
    }

    public CompletableFuture<TextureData> getSkinGrabber() {
        return skinGrabber;
    }

    @Override
    public @NonNull PlayerSkin getSkin() {
        return this.skinTextures;
    }

    @Override
    public boolean isModelPartShown(@NonNull PlayerModelPart part) {
        return true;
    }

    @Nullable
    @Override
    protected PlayerInfo getPlayerInfo() {
        return null;
    }
}