package com.pigicial.wikirenderer.render.item.models;

import com.pigicial.wikirenderer.mixin.access.CompositeModelAccessor;
import com.pigicial.wikirenderer.mixin.access.ConditionalItemModelAccessor;
import com.pigicial.wikirenderer.mixin.access.CuboidItemModelWrapperAccessor;
import com.pigicial.wikirenderer.mixin.access.RangeSelectItemModelAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ItemModelsProcessor {

    @Nullable
    public static List<ItemModel> getModels(ItemStack itemStack) {
        Identifier modelIdentifier = itemStack.get(DataComponents.ITEM_MODEL);
        if (modelIdentifier == null) {
            return null;
        }

        ItemModel itemModel = Minecraft.getInstance().getModelManager().getItemModel(modelIdentifier);
        Set<ItemModel> itemModelsSet = flattenModel(itemModel);

        return new ArrayList<>(itemModelsSet);
    }

    private static Set<ItemModel> flattenModel(@NotNull ItemModel model) {
        Set<ItemModel> modelsToFlatten = new LinkedHashSet<>();
        switch (model) {
            case SelectItemModel<?> selectItemModel -> {
                ItemModel fallback = ((SelectItemModelAccessor) selectItemModel).wikirenderer$getFallback();
                Object2ObjectMap<?, ItemModel> bakedModels = ((SelectItemModelAccessor) selectItemModel).wikirenderer$getBakedModels();

                if (fallback != null) {
                    modelsToFlatten.add(fallback);
                }
                if (bakedModels != null) {
                    modelsToFlatten.addAll(bakedModels.values());
                }
            }
            case RangeSelectItemModel rangeSelectItemModel -> {
                ItemModel fallback = ((RangeSelectItemModelAccessor) rangeSelectItemModel).wikirenderer$getFallback();
                ItemModel[] itemModels = ((RangeSelectItemModelAccessor) rangeSelectItemModel).wikirenderer$getModels();

                if (fallback != null) {
                    modelsToFlatten.add(fallback);
                }
                if (itemModels != null) {
                    modelsToFlatten.addAll(Arrays.asList(itemModels));
                }
            }
            case ConditionalItemModel conditionalItemModel -> {
                ItemModel onFalseModel = ((ConditionalItemModelAccessor) conditionalItemModel).wikirenderer$getOnFalse();
                ItemModel onTrueModel = ((ConditionalItemModelAccessor) conditionalItemModel).wikirenderer$getOnTrue();

                modelsToFlatten.add(onFalseModel);
                modelsToFlatten.add(onTrueModel);
            }
            case CompositeModel compositeModel -> {
                List<ItemModel> children = ((CompositeModelAccessor) compositeModel).wikirenderer$getModels();

                Set<List<ItemModel>> combinations = new LinkedHashSet<>();
                combinations.add(new ArrayList<>());

                for (ItemModel child : children) {
                    Set<ItemModel> childOutcomes = dedup(flattenModel(child));
                    Set<List<ItemModel>> newCombinations = new LinkedHashSet<>();

                    for (List<ItemModel> existing : combinations) {
                        for (ItemModel childOutcome : childOutcomes) {
                            List<ItemModel> combined = new ArrayList<>(existing);
                            if (childOutcome instanceof CompositeModel c) {
                                combined.addAll(((CompositeModelAccessor) c).wikirenderer$getModels());
                            } else {
                                combined.add(childOutcome);
                            }
                            newCombinations.add(combined);
                        }
                    }
                    combinations = newCombinations;
                }

                return combinations.stream().map(CompositeModel::new).collect(Collectors.toSet());
            }
            default -> {
                return Set.of(model);
            }
        }

        Set<ItemModel> flattenedModels = new LinkedHashSet<>();
        for (ItemModel itemModel : modelsToFlatten) {
            flattenedModels.addAll(flattenModel(itemModel));
        }
        flattenedModels = dedup(flattenedModels);

        return flattenedModels;
    }

    private static Set<ItemModel> dedup(Set<ItemModel> models) {
        Map<String, ItemModel> seen = new LinkedHashMap<>();
        for (ItemModel model : models) {
            String key = getCompositeKey(model);
            seen.putIfAbsent(key, model);
        }

        return new LinkedHashSet<>(seen.values());
    }

    private static String getCompositeKey(ItemModel model) {
        if (model instanceof CompositeModel c) {
            return ((CompositeModelAccessor) c).wikirenderer$getModels()
                    .stream()
                    .map(ItemModelsProcessor::getLayerKey)
                    .collect(Collectors.joining("+"));
        }
        return getLayerKey(model);
    }

    private static String getLayerKey(ItemModel model) {
        if (model instanceof CuboidItemModelWrapper wcuboidItemModelWrapper) {
            ModelRenderProperties props = ((CuboidItemModelWrapperAccessor) wcuboidItemModelWrapper).wikirenderer$getProperties();
            return props.particleMaterial().sprite().contents().name().toString();
        }
        return String.valueOf(System.identityHashCode(model));
    }
}
