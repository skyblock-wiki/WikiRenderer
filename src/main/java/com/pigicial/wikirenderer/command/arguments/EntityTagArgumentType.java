package com.pigicial.wikirenderer.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

public class EntityTagArgumentType implements ArgumentType<EntityTagArgumentType.TagArgument> {

    private static final DynamicCommandExceptionType UNKNOWN_TAG_EXCEPTION = new DynamicCommandExceptionType(
            tag -> Component.translatable("arguments.entity.tag.unknown", tag)
    );

    private final HolderLookup.RegistryLookup<EntityType<?>> registryWrapper;

    public EntityTagArgumentType() {
        this.registryWrapper = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.ENTITY_TYPE);
    }

    public static <S> EntityTagArgumentType.TagArgument getTag(String name, CommandContext<S> context) {
        return context.getArgument(name, EntityTagArgumentType.TagArgument.class);
    }

    @Override
    public EntityTagArgumentType.TagArgument parse(StringReader reader) throws CommandSyntaxException {
        reader.expect('#');
        Identifier tagId = Identifier.read(reader);
        return registryWrapper.get(TagKey.create(Registries.ENTITY_TYPE, tagId))
                .map(entryList -> new EntityTagArgumentType.TagArgument(tagId, entryList))
                .orElseThrow(() -> UNKNOWN_TAG_EXCEPTION.createWithContext(reader, tagId));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SuggestionContext.suggestResource(this.registryWrapper.listTags().map(named -> named.key().location()), builder, String.valueOf('#'));
    }

    public record TagArgument(Identifier id, HolderSet<EntityType<?>> entries) {}
}

