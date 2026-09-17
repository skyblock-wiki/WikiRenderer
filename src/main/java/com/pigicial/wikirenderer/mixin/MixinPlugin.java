package com.pigicial.wikirenderer.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {

    public static final Logger LOGGER = LoggerFactory.getLogger("WikiRenderer MixinPlugin");
    static {
        // We force-initialize AWT here so that we can copy stuff to clipboard
        // on macos though, copying images to clipboard isnt supported

        // calling this method on macos can cause the game to sometimes not launcher
        // (fixed in 26.3 but whatever im too lazy to properly fix it here (aka add glfw image clipboard support) so this works)
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            if (!GraphicsEnvironment.isHeadless()) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard();
                } catch (Exception e) {
                    LOGGER.info("Couldn't initialize AWT", e);
                }
            }
        }
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
