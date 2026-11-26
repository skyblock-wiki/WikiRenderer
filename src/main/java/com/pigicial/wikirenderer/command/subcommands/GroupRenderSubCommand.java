package com.pigicial.wikirenderer.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.pigicial.wikirenderer.command.arguments.*;
import com.pigicial.wikirenderer.render.batch.BatchRenderable;
import com.pigicial.wikirenderer.render.batch.ItemBatchRenderTask;
import com.pigicial.wikirenderer.render.entity.EntityRenderable;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.ScreenSchedulerAndSaver;
import com.pigicial.wikirenderer.util.EntityNBTValidityFilter;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class GroupRenderSubCommand extends WikiRendererSubCommand {
    @Override
    public String getName() {
        return "group";
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> register(LiteralArgumentBuilder<FabricClientCommandSource> source, CommandBuildContext access) {
        return source
                .then(literal("item")
                        .then(literal("namespace")
                                .then(argument("namespace", new ItemNamespaceArgumentType())
                                        .then(argument("task", new ItemBatchRenderTaskArgumentType())
                                                .executes(context -> {
                                                    this.renderItemNamespace(context);
                                                    return 0;
                                                }))))
                        .then(literal("creative_tab")
                                .then(argument("itemgroup", new ItemGroupArgumentType())
                                        .then(argument("task", new ItemBatchRenderTaskArgumentType())
                                                .executes(context -> {
                                                    this.renderCreativeTab(context, access);
                                                    return 0;
                                                }))))
                        .then(literal("tag")
                                .then(argument("tag", ItemTagArgumentType.itemTag())
                                        .then(argument("task", new ItemBatchRenderTaskArgumentType())
                                                .executes(context -> {
                                                    this.renderItemTagContents(context);
                                                    return 0;
                                                })))))
                .then(literal("entity")
                        .then(literal("namespace")
                                .then(argument("namespace", new EntityNamespaceArgumentType())
                                        .executes(context -> {
                                            this.renderEntityNamespace(context, false, false);
                                            return 0;
                                        })
                                        .then(literal("nbt")
                                                .then(argument("nbt", CompoundTagArgument.compoundTag())
                                                        .executes(context -> {
                                                            this.renderEntityNamespace(context, true, false);
                                                            return 0;
                                                        })))
                                        .then(literal("nbt_filter")
                                                .then(argument("nbt_filter", new EntityNBTFilterTypeArgumentType())
                                                        .then(argument("nbt", CompoundTagArgument.compoundTag())
                                                                .executes(context -> {
                                                                    this.renderEntityNamespace(context, true, true);
                                                                    return 0;
                                                                }))))))
                        .then(literal("tag")
                                .then(argument("tag", new EntityTagArgumentType())
                                        .executes(context -> {
                                            this.renderEntityTagContents(context, false, false);
                                            return 0;
                                        })
                                        .then(literal("nbt")
                                                .then(argument("nbt", CompoundTagArgument.compoundTag())
                                                        .executes(context -> {
                                                            this.renderEntityTagContents(context, true, false);
                                                            return 0;
                                                        })))
                                        .then(literal("nbt_filter")
                                                .then(argument("nbt_filter", new EntityNBTFilterTypeArgumentType())
                                                        .then(argument("nbt", CompoundTagArgument.compoundTag())
                                                                .executes(context -> {
                                                                    this.renderEntityTagContents(context, true, true);
                                                                    return 0;
                                                                })))))));
    }

    private void renderCreativeTab(CommandContext<FabricClientCommandSource> context, CommandBuildContext access) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        ItemBatchRenderTask task = ItemBatchRenderTaskArgumentType.getTask("task", context);
        CreativeModeTab creativeTab = ItemGroupArgumentType.getItemGroup("itemgroup", context);
        if (creativeTab.getDisplayItems().isEmpty()) {
            CreativeModeTabs.tryRebuildTabContents(access.enabledFeatures(), true, level.registryAccess());
        }

        List<ItemStack> items = new ArrayList<>(creativeTab.getDisplayItems());

        String name = "creative-tab_" + Objects.requireNonNull(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(creativeTab)).toShortLanguageKey();
        task.action.accept(name, items);
    }

    private void renderItemNamespace(CommandContext<FabricClientCommandSource> context) {
        ItemNamespaceArgumentType.Namespace namespace = ItemNamespaceArgumentType.getNamespace("namespace", context);
        ItemBatchRenderTaskArgumentType.getTask("task", context).action.accept("namespace_" + namespace.name(), namespace.getContent());
    }

    private void renderItemTagContents(CommandContext<FabricClientCommandSource> context) {
        ItemTagArgumentType.TagArgument tag = ItemTagArgumentType.getTag("tag", context);
        ItemBatchRenderTaskArgumentType.getTask("task", context).action.accept(
                "tag_" + tag.id().getNamespace() + "/" + tag.id().getPath(),
                tag.entries().stream()
                        .map(Holder::value)
                        .map(Item::getDefaultInstance)
                        .filter(item -> !item.isEmpty())
                        .toList()
        );
    }

    private void renderEntityTagContents(CommandContext<FabricClientCommandSource> context, boolean useNbt, boolean useFilter) {
        EntityTagArgumentType.TagArgument tag = EntityTagArgumentType.getTag("tag", context);
        String source = "tag_" + tag.id().getNamespace() + "/" + tag.id().getPath();

        List<EntityRenderable> renderables = tag.entries()
                .stream()
                .map(Holder::value)
                .filter(EntityType::canSummon)
                .filter(type -> !RenderEntitySubCommand.DEFAULT_INVISIBLE_ENTITY_TYPES.contains(type))
                .map(type -> {
                    CompoundTag entityNbt = useNbt ? CompoundTagArgument.getCompoundTag(context, "nbt") : null;
                    EntityNBTValidityFilter filterType = useFilter ? EntityNBTFilterTypeArgumentType.getType(context, "nbt_filter") : null;
                    return EntityRenderable.fromEntityType(type, entityNbt, filterType);
                })
                .filter(Objects::nonNull)
                .toList();

        ScreenSchedulerAndSaver.schedule(new RenderScreen(BatchRenderable.of(source, renderables)));
    }

    private void renderEntityNamespace(CommandContext<FabricClientCommandSource> context, boolean useNbt, boolean useFilter) {
        EntityNamespaceArgumentType.Namespace namespace = EntityNamespaceArgumentType.getNamespace("namespace", context);
        String source = "namespace_" + namespace.name();

        List<EntityRenderable> renderables = namespace.getContent()
                .stream()
                .map(type -> {
                    CompoundTag entityNbt = useNbt ? CompoundTagArgument.getCompoundTag(context, "nbt") : null;
                    EntityNBTValidityFilter filterType = useFilter ? EntityNBTFilterTypeArgumentType.getType(context, "nbt_filter") : null;
                    return EntityRenderable.fromEntityType(type, entityNbt, filterType);
                })
                .filter(Objects::nonNull)
                .toList();

        ScreenSchedulerAndSaver.schedule(new RenderScreen(BatchRenderable.of(source, renderables)));
    }

}
