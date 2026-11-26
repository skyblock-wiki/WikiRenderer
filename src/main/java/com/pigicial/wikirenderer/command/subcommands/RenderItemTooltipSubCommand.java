package com.pigicial.wikirenderer.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pigicial.wikirenderer.render.item.TooltipRenderable;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.ScreenSchedulerAndSaver;
import com.pigicial.wikirenderer.util.Translate;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;

public class RenderItemTooltipSubCommand extends WikiRendererSubCommand {
    @Override
    public String getName() {
        return "tooltip";
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> register(LiteralArgumentBuilder<FabricClientCommandSource> source, CommandBuildContext access) {
        return source.executes(context -> {
                    renderHeldItemTooltip(context);
                    return 0;
                })
                .then(argument("item", ItemArgument.item())
                        .executes(context -> {
                            renderItemTooltipWithArgument(context);
                            return 0;
                        }));
    }

    private void renderItemTooltipWithArgument(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        ScreenSchedulerAndSaver.schedule(new RenderScreen(
                new TooltipRenderable(ItemArgument.getItem(context, "item").createItemStack(1))
        ));
    }

    private void renderHeldItemTooltip(CommandContext<FabricClientCommandSource> context) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.isEmpty()) {
            Translate.commandError(context, "no_held_item");
            return;
        }

        ScreenSchedulerAndSaver.schedule(new RenderScreen(new TooltipRenderable(mainHandItem)));
    }
}
