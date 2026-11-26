package com.pigicial.wikirenderer.screen.owo.util;

import com.pigicial.wikirenderer.WikiRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class UISounds {

    public static final SoundEvent UI_INTERACTION = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "ui.owo.interaction"));

    private UISounds() {}

    @Environment(EnvType.CLIENT)
    public static void play(SoundEvent event) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forPreview(event, SoundSource.UI, 1));
    }

    @Environment(EnvType.CLIENT)
    public static void playButtonSound() {
        play(SoundEvents.UI_BUTTON_CLICK.value());
    }

    @Environment(EnvType.CLIENT)
    public static void playInteractionSound() {
        play(UI_INTERACTION);
    }
}
