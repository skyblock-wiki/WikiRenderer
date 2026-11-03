package com.pigicial.wikirenderer;

import com.mojang.blaze3d.platform.InputConstants;
import com.pigicial.wikirenderer.command.subcommands.RenderBlockSubCommand;
import com.pigicial.wikirenderer.command.subcommands.RenderEntitySubCommand;
import com.pigicial.wikirenderer.mixin.access.AbstractContainerScreenAccessor;
import com.pigicial.wikirenderer.mixin.access.CreativeModeInventoryScreenAccessor;
import com.pigicial.wikirenderer.property.GlobalProperties;
import com.pigicial.wikirenderer.render.area.AreaSelectionHelper;
import com.pigicial.wikirenderer.render.item.ItemRenderable;
import com.pigicial.wikirenderer.render.item.TooltipRenderable;
import com.pigicial.wikirenderer.render.screen.ContainerScreenRenderable;
import com.pigicial.wikirenderer.render.skyblock.frame_based.SkyBlockTimingDataCacher;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.ScreenSchedulerAndSaver;
import com.pigicial.wikirenderer.screen.SelectRenderTaskScreen;
import com.pigicial.wikirenderer.textures.PlayerTextureUtils;
import com.pigicial.wikirenderer.textures.TextureData;
import com.pigicial.wikirenderer.util.Translate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WikiRendererKeybinds {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "keybinds"));
    public static final KeyMapping KEYBIND_SELECT_AREA = new KeyMapping("key.wikirenderer.area_select", InputConstants.KEY_C, CATEGORY);
    public static final KeyMapping KEYBIND_SELECT_AREA_EXPAND = new KeyMapping("key.wikirenderer.area_select_expand", InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping KEYBIND_RENDER_HOVERED_ITEM_OR_VIEWED_ENTITY = new KeyMapping("key.wikirenderer.render_hovered_item_or_viewed_entity", InputConstants.KEY_H, CATEGORY);
    public static final KeyMapping KEYBIND_RENDER_HOVERED_ITEM_TOOLTIP = new KeyMapping("key.wikirenderer.render_hovered_item_tooltip", InputConstants.KEY_J, CATEGORY);
    public static final KeyMapping KEYBIND_RENDER_TARGETED_BLOCK = new KeyMapping("key.wikirenderer.render_targeted_block", InputConstants.KEY_L, CATEGORY);
    public static final KeyMapping KEYBIND_RENDER_INVENTORY = new KeyMapping("key.wikirenderer.render_inventory", InputConstants.KEY_SEMICOLON, CATEGORY);
    public static final KeyMapping KEYBIND_BATCH_RENDER_INVENTORY_ITEMS = new KeyMapping("key.wikirenderer.batch_render_inventory", InputConstants.KEY_K, CATEGORY);

    public static void registerKeyBinds() {
        KeyMappingHelper.registerKeyMapping(KEYBIND_SELECT_AREA);
        KeyMappingHelper.registerKeyMapping(KEYBIND_SELECT_AREA_EXPAND);
        KeyMappingHelper.registerKeyMapping(KEYBIND_RENDER_HOVERED_ITEM_OR_VIEWED_ENTITY);
        KeyMappingHelper.registerKeyMapping(KEYBIND_RENDER_HOVERED_ITEM_TOOLTIP);
        KeyMappingHelper.registerKeyMapping(KEYBIND_RENDER_TARGETED_BLOCK);
        KeyMappingHelper.registerKeyMapping(KEYBIND_BATCH_RENDER_INVENTORY_ITEMS);
        KeyMappingHelper.registerKeyMapping(KEYBIND_RENDER_INVENTORY);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (KEYBIND_SELECT_AREA.consumeClick()) {
                if (client.player.isShiftKeyDown()) {
                    AreaSelectionHelper.clear();
                } else {
                    AreaSelectionHelper.select();
                }
                return;
            }
            if (KEYBIND_SELECT_AREA_EXPAND.consumeClick()) {
                if (client.player.isShiftKeyDown()) {
                    AreaSelectionHelper.clear();
                } else {
                    AreaSelectionHelper.expand();
                }
                return;
            }

            if (KEYBIND_RENDER_HOVERED_ITEM_OR_VIEWED_ENTITY.consumeClick()) {
                RenderEntitySubCommand.renderTargetedEntity(null);
            }

            if (KEYBIND_RENDER_TARGETED_BLOCK.consumeClick()) {
                RenderBlockSubCommand.renderTargetedBlock(null);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, _, _) -> ScreenKeyboardEvents.afterKeyPress(screen).register((_, key) -> {
            if (Minecraft.getInstance().options.keyDebugModifier.isDown()) {
                return;
            }

            if (KEYBIND_RENDER_HOVERED_ITEM_OR_VIEWED_ENTITY.matches(key)) {
                ItemStack hoveredSlot = getHoveredSlot(client);
                if (hoveredSlot != null) {
                    if (GlobalProperties.get().sbFrameRenderingKeybindOverrides.get()) {
                        TextureData textureData = PlayerTextureUtils.getTextureDataFromPlayerHead(hoveredSlot);
                        if (textureData == null) {
                            Translate.sendMessage("sb_player_head_mark_first_fail");
                        } else {
                            Translate.sendMessage("sb_player_head_mark_first_success");
                            SkyBlockTimingDataCacher.getInstance().markTextureAsFirst(textureData);
                        }

                        return;
                    }
                    ScreenSchedulerAndSaver.openImmediately(new RenderScreen(new ItemRenderable(hoveredSlot)));
                }
            }

            if (KEYBIND_RENDER_HOVERED_ITEM_TOOLTIP.matches(key)) {
                ItemStack hoveredSlot = getHoveredSlot(client);
                if (hoveredSlot != null) {
                    ScreenSchedulerAndSaver.openImmediately(new RenderScreen(new TooltipRenderable(hoveredSlot)));
                }
            }

            if (KEYBIND_BATCH_RENDER_INVENTORY_ITEMS.matches(key)) {
                List<ItemStack> items = getItems(client);
                if (items != null && !items.isEmpty()) {
                    Minecraft.getInstance().setScreenAndShow(new SelectRenderTaskScreen(items));
                }
            }

            if (KEYBIND_RENDER_INVENTORY.matches(key)) {
                Screen currentScreen = client.gui.screen();
                if (currentScreen instanceof AbstractContainerScreen<?> containerScreen) {
                    ScreenSchedulerAndSaver.openImmediately(new RenderScreen(new ContainerScreenRenderable(containerScreen)));
                }
            }
        }));
    }

    @Nullable
    protected static ItemStack getHoveredSlot(Minecraft client) {
        Player player = client.player;
        if (player == null) {
            return null;
        }

        Screen currentScreen = client.gui.screen();
        if (currentScreen instanceof AbstractContainerScreen<?> containerScreen) {
            if (currentScreen.getFocused() instanceof EditBox) return null;
            if (currentScreen instanceof CreativeModeInventoryScreen
                && CreativeModeInventoryScreenAccessor.getSelectedTab() != null
                && CreativeModeInventoryScreenAccessor.getSelectedTab().getType() == CreativeModeTab.Type.SEARCH) {
                return null;
            }

            SlotWidget hoveredSlot = ((AbstractContainerScreenAccessor) containerScreen).wikirenderer$getHoveredSlot();
            if (hoveredSlot == null) {
                return null;
            }

            ItemStack hoveredItem = hoveredSlot.slot().getItem();
            if (!hoveredItem.isEmpty()) {
                return hoveredItem;
            }
        }

        return null;
    }

    @Nullable
    protected static List<ItemStack> getItems(Minecraft client) {
        Player player = client.player;
        if (player == null) {
            return null;
        }

        Screen currentScreen = client.gui.screen();
        if (currentScreen instanceof AbstractContainerScreen<?> containerScreen) {
            if (currentScreen.getFocused() instanceof EditBox) return null;
            if (currentScreen instanceof CreativeModeInventoryScreen
                && CreativeModeInventoryScreenAccessor.getSelectedTab() != null
                && CreativeModeInventoryScreenAccessor.getSelectedTab().getType() == CreativeModeTab.Type.SEARCH) {
                return null;
            }

            AbstractContainerMenu menu = ((AbstractContainerScreenAccessor) containerScreen).menu();
            return menu.slots.stream().map(Slot::getItem).filter(stack -> !stack.isEmpty()).toList();
        }

        return null;
    }
}
