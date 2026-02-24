package org.yanbwe.raritycore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.datapack.RarityDatapackLoader;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.config.FinalRarityConfigLoader;
import org.yanbwe.raritycore.network.RaritySyncPacket;
import org.yanbwe.raritycore.registry.RarityRegistry;

public class Raritycore implements ModInitializer {

    public static final String MOD_ID = "raritycore";
    public static final Logger LOGGER = LoggerFactory.getLogger("RarityCore");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing RarityCore...");
        
        // 初始化配置系统
        ConfigManager.initializeConfigs();
        
        // 按照Forge版本的真实加载顺序：
        // 1. 注册数据包加载器（最高优先级，通过Minecraft的资源重载系统自动加载）
        // 2. 在数据包加载完成后，依次加载FinalRarityConfig文件夹和FinalRarity.json
        // 3. 后加载的配置会覆盖先加载的同名物品配置
        
        // 注册数据包加载器
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new RarityDatapackLoader());
        
        // FinalRarityConfig文件夹和FinalRarity.json将在数据包加载完成后手动加载
        // 这模拟了Forge版本RarityDataLoader.apply()中的加载顺序
        
        // 初始化网络包
        RaritySyncPacket.register();
        
        // 注册命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> 
            RarityCoreCommands.register(dispatcher)
        );
        
        // 服务器启动事件
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("RarityCore server starting");
        });
        
        // 玩家连接事件
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 发送稀有度数据给新连接的玩家
            RarityRegistry.syncRarityToClient(handler.player);
        });
        
        LOGGER.info("RarityCore initialized successfully!");
    }
}
