package com.pigicial.wikirenderer.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pigicial.wikirenderer.util.EntityNBTValidityFilter;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public class EntityNBTFilterTypeArgumentType implements ArgumentType<EntityNBTValidityFilter> {

    private static final DynamicCommandExceptionType EXCEPTION = new DynamicCommandExceptionType(tag -> Component.translatable("arguments.entity_filter_type.unknown", tag));

    public static <S> EntityNBTValidityFilter getType(CommandContext<S> context, String name) {
        return context.getArgument(name, EntityNBTValidityFilter.class);
    }

    @Override
    public EntityNBTValidityFilter parse(StringReader reader) throws CommandSyntaxException {
        String first = reader.readString();
        switch (first) {
            case "require_all_valid" -> {
                return EntityNBTValidityFilter.REQUIRE_ALL_APPLIED_VALID;
            }
            case "require_one_valid" -> {
                return EntityNBTValidityFilter.REQUIRE_ONE_APPLIED_VALID;
            }
            case "require_none_valid" -> {
                return EntityNBTValidityFilter.REQUIRE_ALL_APPLIED_INVALID;
            }
        }

        throw EXCEPTION.createWithContext(reader, first);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SuggestionContext.suggest(new String[]{"require_all_valid", "require_one_valid", "require_none_valid"}, builder);
    }

}

