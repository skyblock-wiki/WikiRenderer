package com.pigicial.wikirenderer.command.subcommands;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.services.response.MinecraftTexturesPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pigicial.wikirenderer.render.item.ItemRenderable;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.ScreenSchedulerAndSaver;
import com.pigicial.wikirenderer.textures.PlayerTextureUtils;
import com.pigicial.wikirenderer.util.Translate;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class RenderItemSubCommand extends WikiRendererSubCommand {
    @Override
    public String getName() {
        return "item";
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> register(LiteralArgumentBuilder<FabricClientCommandSource> source, CommandBuildContext access) {
        return source.executes(context -> {
                    this.renderItemInHand(context);
                    return 0;
                })
                .then(literal("id").then(argument("item", ItemArgument.item())
                        .executes(context -> {
                            this.renderItemWithArgument(context);
                            return 0;
                        })))
                .then(literal("texture").then(argument("texture", StringArgumentType.greedyString())
                        .executes(context -> {
                            this.renderTexturedHeadItem(context);
                            return 0;
                        })));
    }

    private void renderItemInHand(CommandContext<FabricClientCommandSource> context) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.isEmpty()) {
            Translate.commandError(context, "no_held_item");
            return;
        }

        ScreenSchedulerAndSaver.schedule(new RenderScreen(new ItemRenderable(mainHandItem)));
    }

    private void renderItemWithArgument(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        ScreenSchedulerAndSaver.schedule(new RenderScreen(
                new ItemRenderable(ItemArgument.getItem(context, "item").createItemStack(1))
        ));
    }

    private void renderTexturedHeadItem(CommandContext<FabricClientCommandSource> context) {
        String texture = StringArgumentType.getString(context, "texture");

        GameProfile gameProfile;
        boolean base64 = this.isBase64(texture);
        if (base64) {
            gameProfile = PlayerTextureUtils.createTexturedGameProfileFromBase64(texture);
        } else {
            gameProfile = PlayerTextureUtils.createTexturedGameProfileFromID(texture);
        }

        MinecraftProfileTextures textures = Minecraft.getInstance().services().sessionService().getTextures(gameProfile);
        if (textures == MinecraftProfileTextures.EMPTY) {
            Translate.commandFeedback(context, "loading_texture_fail");
            return;
        }

        CompletableFuture<ItemRenderable> future = Minecraft.getInstance().getSkinManager()
                .get(gameProfile)
                .exceptionally(_ -> Optional.empty())
                .thenApply(s -> {
                    if (s.isPresent()) {
                        ItemStack stack = PlayerTextureUtils.createPlayerHead(gameProfile);
                        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Player Head"));
                        return new ItemRenderable(stack);
                    } else {
                        return null;
                    }
                })
                .orTimeout(8, TimeUnit.SECONDS);

        future.whenComplete((renderable, throwable) -> {
            if (throwable != null || renderable == null) {
                Minecraft.getInstance().executeBlocking(() -> Translate.commandFeedback(context, "loading_texture_fail"));
            } else {
                ScreenSchedulerAndSaver.schedule(new RenderScreen(renderable));
            }
        });
    }

    private boolean isBase64(@NotNull String input) {
        if (input.isEmpty()) return false;

        try {
            Base64.getDecoder().decode(input);
            PlayerTextureUtils.GSON.fromJson(input, MinecraftTexturesPayload.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
