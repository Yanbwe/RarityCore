package org.yanbwe.raritycore.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.bindings.event.StartupEvents;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import org.yanbwe.raritycore.RarityCore;

/**
 * RarityCore KubeJS 集成插件
 * 通过 kubejs.plugins.txt 注册，提供 JS 绑定和事件
 */
public class RarityCoreKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void init() {
        RarityCore.LOGGER.info("RarityCore KubeJS plugin initialized");
    }

    @Override
    public void registerEvents() {
        RarityCoreKubeJSEvents.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("RarityCore", RarityCoreWrapper.class);
    }
}
