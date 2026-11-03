package com.pigicial.wikirenderer.mixin.access;

import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Invoker("getHoveredSlot")
    SlotWidget wikirenderer$getHoveredSlot();

    @Accessor("menu")
    AbstractContainerMenu menu();
}
