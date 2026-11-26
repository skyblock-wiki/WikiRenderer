package com.pigicial.wikirenderer.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pigicial.wikirenderer.util.Translate;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ItemNamespaceArgumentType implements ArgumentType<ItemNamespaceArgumentType.Namespace> {

    private static final SimpleCommandExceptionType NO_SUCH_NAMESPACE = new SimpleCommandExceptionType(Translate.msg("no_such_namespace"));

    public static <S> Namespace getNamespace(String name, CommandContext<S> context) {
        return context.getArgument(name, Namespace.class);
    }

    @Override
    public Namespace parse(StringReader reader) throws CommandSyntaxException {
        String input = reader.readString();
        Set<String> namespaces = getNamespaces();

        if (!namespaces.contains(input)) {
            throw NO_SUCH_NAMESPACE.create();
        }

        return new Namespace(input);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SuggestionContext.suggest(getNamespaces(), builder);
    }

    private Set<String> getNamespaces() {
        Set<String> set = new HashSet<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            set.add(id.getNamespace());
        }
        return set;
    }

    public record Namespace(String name) {
        public List<ItemStack> getContent() {
            return BuiltInRegistries.ITEM.listElements()
                    .filter(entry -> Objects.equals(entry.key().identifier().getNamespace(), this.name))
                    .map(Holder.Reference::value)
                    .map(Item::getDefaultInstance)
                    .toList();
        }
    }
}
