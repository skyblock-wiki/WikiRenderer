package com.pigicial.wikirenderer.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.concurrent.CompletableFuture;

public class ItemGroupArgumentType implements ArgumentType<CreativeModeTab> {

    private static final DynamicCommandExceptionType NO_ITEMGROUP = new DynamicCommandExceptionType(o -> () -> "No such item group: " + o);

    public static <S> CreativeModeTab getItemGroup(String name, CommandContext<S> context) {
        return context.getArgument(name, CreativeModeTab.class);
    }

    @Override
    public CreativeModeTab parse(StringReader reader) throws CommandSyntaxException {
        Identifier id = Identifier.read(reader);
        return CreativeModeTabs.allTabs().stream().filter(itemGroup -> {
            Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(itemGroup);
            return key != null && key.equals(id);
        }).findAny().orElseThrow(() -> NO_ITEMGROUP.create(id));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SuggestionContext.suggestResource(CreativeModeTabs.allTabs().stream().map(BuiltInRegistries.CREATIVE_MODE_TAB::getKey), builder);
    }
}
