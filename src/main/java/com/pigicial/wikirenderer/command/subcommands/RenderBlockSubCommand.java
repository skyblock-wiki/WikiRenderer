package com.pigicial.wikirenderer.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.pigicial.wikirenderer.mixin.access.BlockInputAccessor;
import com.pigicial.wikirenderer.render.item.BlockStateRenderable;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.ScreenSchedulerAndSaver;
import com.pigicial.wikirenderer.util.Translate;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;

public class RenderBlockSubCommand extends WikiRendererSubCommand {
    @Override
    public String getName() {
        return "block";
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> register(LiteralArgumentBuilder<FabricClientCommandSource> source, CommandBuildContext access) {
        return source.executes(context -> {
                    RenderBlockSubCommand.renderTargetedBlock(context);
                    return 0;
                })
                .then(argument("block", BlockStateArgument.block())
                        .executes(context -> {
                            this.renderBlockWithArgument(context);
                            return 0;
                        }));
    }

    public static void renderTargetedBlock(CommandContext<FabricClientCommandSource> context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        AttackRange attackRange = new AttackRange(0, 20, 0, 20, 0, 1);
        HitResult hitResult = attackRange.getClosesetHit(player, 1, _ -> false);

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockState blockState = client.level.getBlockState(blockHitResult.getBlockPos());
            if (blockState.getRenderShape() == RenderShape.INVISIBLE) {
                Translate.commandError(context, "no_block");
                return;
            }

            BlockPos hitPos = blockHitResult.getBlockPos();
            BlockStateRenderable renderable = BlockStateRenderable.copyOf(client.level, hitPos);
            if (renderable != null) {
                ScreenSchedulerAndSaver.schedule(new RenderScreen(renderable));
            }
        } else {
            Translate.commandError(context, "no_block");
        }
    }

    private void renderBlockWithArgument(CommandContext<FabricClientCommandSource> context) {
        BlockInput stateArg = context.getArgument("block", BlockInput.class);
        BlockState state = stateArg.getState();
        CompoundTag data = ((BlockInputAccessor) stateArg).wikirenderer$getTag();

        BlockStateRenderable renderable = BlockStateRenderable.of(state, data);
        if (renderable == null) return;

        ScreenSchedulerAndSaver.schedule(new RenderScreen(renderable));
    }
}
