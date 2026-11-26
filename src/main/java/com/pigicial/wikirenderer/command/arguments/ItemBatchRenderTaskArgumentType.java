package com.pigicial.wikirenderer.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pigicial.wikirenderer.render.batch.ItemBatchRenderTask;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public class ItemBatchRenderTaskArgumentType implements ArgumentType<ItemBatchRenderTask> {

    private static final DynamicCommandExceptionType EXCEPTION = new DynamicCommandExceptionType(tag -> Component.translatable("arguments.batch_type.unknown", tag));

    public static <S> ItemBatchRenderTask getTask(String name, CommandContext<S> context) {
        return context.getArgument(name, ItemBatchRenderTask.class);
    }

    @Override
    public ItemBatchRenderTask parse(StringReader reader) throws CommandSyntaxException {
        String first = reader.readString();
        if (first.equals("itematlas")) return ItemBatchRenderTask.ITEM_ATLAS;

        if (first.equals("batch")) {
            reader.expect(' ');
            String second = reader.readString();

            switch (second) {
                case "items" -> {
                    return ItemBatchRenderTask.BATCH_ITEM;
                }
                case "blocks" -> {
                    return ItemBatchRenderTask.BATCH_BLOCK;
                }
                case "tooltips" -> {
                    return ItemBatchRenderTask.BATCH_TOOLTIP;
                }
            }
            throw EXCEPTION.createWithContext(reader, second);
        }

        throw EXCEPTION.createWithContext(reader, first);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining();

        if (input.codePoints().filter(value -> value == ' ').findAny().isPresent() && input.contains("batch")) {
            return SuggestionContext.suggest(new String[]{"items", "blocks", "tooltips"}, builder.createOffset(builder.getStart() + 6));
        } else {
            return SuggestionContext.suggest(new String[]{"itematlas", "batch"}, builder);
        }
    }

}
