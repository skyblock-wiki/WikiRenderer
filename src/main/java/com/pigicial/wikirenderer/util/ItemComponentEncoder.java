package com.pigicial.wikirenderer.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ItemComponentEncoder {

    // based on ItemParser
    public static String toGiveString(ItemStack stack) {
        HolderLookup.Provider registries = HolderLookup.Provider.create(Stream.of(BuiltInRegistries.ITEM));
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return itemId;
        }

        RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        List<String> parts = new ArrayList<>();

        DataComponentPatch.SplitResult split = patch.split();

        split.added().stream().forEach(typed -> {
            DataComponentType<?> type = typed.type();
            Optional<ResourceKey<DataComponentType<?>>> key = BuiltInRegistries.DATA_COMPONENT_TYPE.getResourceKey(type);
            if (key.isEmpty()) {
                return;
            }
            String typeId = key.get().identifier().toString();

            encodeComponent(type, typed.value(), registryOps)
                    .ifPresent(tag -> parts.add(typeId + "=" + tag));
        });

        split.removed().forEach(type -> {
            Optional<ResourceKey<DataComponentType<?>>> key = BuiltInRegistries.DATA_COMPONENT_TYPE.getResourceKey(type);
            if (key.isEmpty()) {
                return;
            }
            String typeId = key.get().identifier().toString();
            parts.add("!" + typeId);
        });

        if (parts.isEmpty()) {
            return itemId;
        } else {
            return itemId + "[" + String.join(",", parts) + "]";
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> java.util.Optional<String> encodeComponent(DataComponentType<T> type, Object rawValue, RegistryOps<Tag> ops) {
        // ItemParser.State.readComponentType skips transient components
        if (type.isTransient()) {
            return java.util.Optional.empty();
        }

        return type.codecOrThrow()
                .encodeStart(ops, (T) rawValue)
                .result()
                .map(Tag::toString);
    }
}
