package com.pigicial.wikirenderer.util;

import net.minecraft.client.Minecraft;

import java.awt.*;

public class ClipboardUtil {
    private static final boolean IS_SYSTEM_MAC;

    static {
        IS_SYSTEM_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
    }

    public static boolean hasImageClipboardAccess() {
        return !GraphicsEnvironment.isHeadless() && !IS_SYSTEM_MAC;
    }

    public static void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    public static void setClipboard(ImageTransferable imageTransferable) {
        if (hasImageClipboardAccess()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(imageTransferable, imageTransferable);
        }
    }
}
