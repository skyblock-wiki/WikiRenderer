package com.pigicial.wikirenderer.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.ParseContext;
import net.minecraft.commands.arguments.SerializableArgument;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.concurrent.CompletableFuture;

public class ItemTagArgumentType implements SerializableArgument<ItemTagArgumentType.TagArgument> {

    public static final ItemTagArgumentType INSTANCE = new ItemTagArgumentType();
    public static final SerializableArgument.Type<ItemTagArgumentType> TYPE = SerializableArgument.singletonType(INSTANCE);

    private static final DynamicCommandExceptionType UNKNOWN_TAG_EXCEPTION = new DynamicCommandExceptionType(
            tag -> Component.translatable("arguments.item.tag.unknown", tag)
    );

    public static ArgumentType<TagArgument> itemTag() {
        return INSTANCE.toBrigadier();
    }

    public static <S> TagArgument getTag(String name, CommandContext<S> context) {
        return context.getArgument(name, TagArgument.class);
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(final CommandContext<? extends SuggestionContext> context, final SuggestionsBuilder builder) throws CommandSyntaxException {
        return SuggestionContext.suggestResource(context.getSource().holderLookups().lookupOrThrow(Registries.ITEM).listTags().map(named -> named.key().location()), builder, String.valueOf('#'));
    }

    @Override
    public TagArgument parse(StringReader reader, ParseContext context) throws CommandSyntaxException {
        reader.expect('#');
        Identifier tagId = Identifier.read(reader);
        return context.holderGetters().lookupOrThrow(Registries.ITEM).get(TagKey.create(Registries.ITEM, tagId))
                .map(entryList -> new TagArgument(tagId, entryList))
                .orElseThrow(() -> UNKNOWN_TAG_EXCEPTION.createWithContext(reader, tagId));
    }

    @Override
    public Type<? extends SerializableArgument<TagArgument>> type() {
        return TYPE;
    }

    public record TagArgument(Identifier id, HolderSet<Item> entries) {}
}
