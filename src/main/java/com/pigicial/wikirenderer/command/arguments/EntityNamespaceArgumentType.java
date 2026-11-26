package com.pigicial.wikirenderer.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pigicial.wikirenderer.command.subcommands.RenderEntitySubCommand;
import com.pigicial.wikirenderer.util.Translate;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class EntityNamespaceArgumentType implements ArgumentType<EntityNamespaceArgumentType.Namespace> {

    private static final SimpleCommandExceptionType NO_SUCH_NAMESPACE = new SimpleCommandExceptionType(Translate.msg("no_such_namespace"));

    public static <S> EntityNamespaceArgumentType.Namespace getNamespace(String name, CommandContext<S> context) {
        return context.getArgument(name, EntityNamespaceArgumentType.Namespace.class);
    }

    @Override
    public EntityNamespaceArgumentType.Namespace parse(StringReader reader) throws CommandSyntaxException {
        String input = reader.readString();
        Set<String> namespaces = getNamespaces();

        if (!namespaces.contains(input)) {
            throw NO_SUCH_NAMESPACE.create();
        }

        return new EntityNamespaceArgumentType.Namespace(input);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SuggestionContext.suggest(getNamespaces(), builder);
    }

    private Set<String> getNamespaces() {
        Set<String> set = new HashSet<>();
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            set.add(id.getNamespace());
        }
        return set;
    }

    public record Namespace(String name) {
        public List<? extends EntityType<?>> getContent() {
            return BuiltInRegistries.ENTITY_TYPE.listElements()
                    .filter(entry -> Objects.equals(entry.key().identifier().getNamespace(), this.name))
                    .map(Holder.Reference::value)
                    .filter(EntityType::canSummon)
                    .filter(type -> !RenderEntitySubCommand.DEFAULT_INVISIBLE_ENTITY_TYPES.contains(type))
                    .toList();
        }
    }
}
