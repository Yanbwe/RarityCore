package org.yanbwe.raritycore;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.yanbwe.raritycore.compat.CompatibilityManager;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.event.RarityCoreEventHandler;
import org.yanbwe.raritycore.network.EditModeRequestPacket;
import org.yanbwe.raritycore.network.IncrementalSyncPacket;
import org.yanbwe.raritycore.network.NbtSyncPacket;
import org.yanbwe.raritycore.network.RaritySyncPacket;
import org.yanbwe.raritycore.service.ServiceFactory;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RarityCore.MODID)
public class RarityCore {

    // 在公共位置定义模组ID,供所有引用使用
    public static final String MODID = "raritycore";
    // 直接引用slf4j日志记录器
    public static final Logger LOGGER = LogUtils.getLogger();

    public RarityCore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        
        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(new RarityCoreEventHandler());

        // 初始化服务
        initializeServices();
    }
    
    /**
     * 初始化所有服务
     */
    private void initializeServices() {
        // 使用服务工厂初始化所有服务
        ServiceFactory.getInstance().initializeAllServices();
    }
    
    /**
     * 通用设置方法
     * 在模组加载时执行初始化操作
     * @param event FML通用设置事件
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 提前执行兼容性诊断
        event.enqueueWork(org.yanbwe.raritycore.compat.CompatibilityChecker::performCompatibilityCheck);
        
        // 初始化网络数据包
        event.enqueueWork(RaritySyncPacket::initialize);
        event.enqueueWork(IncrementalSyncPacket::initialize);
        event.enqueueWork(NbtSyncPacket::initialize);
        event.enqueueWork(EditModeRequestPacket::initialize);
        

        
        // 初始化兼容性适配器
        event.enqueueWork(CompatibilityManager::initializeCompatibilityAdapters);
    }
}