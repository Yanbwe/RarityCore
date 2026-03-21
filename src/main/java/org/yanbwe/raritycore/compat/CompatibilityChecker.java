package org.yanbwe.raritycore.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.yanbwe.raritycore.RarityCore;

/**
 * 兼容性检查器
 * 处理与其他模组和原版API的兼容性检查
 */
public class CompatibilityChecker {
    
    // 环境兼容性检测标志
    private static boolean vanillaRarityApiChecked = false;
    private static boolean isVanillaRarityApiSupported = true;
    private static String compatibilityFailureReason = null;
    private static boolean hasNotifiedPlayer = false; // 添加玩家通知状态
    
    /**
     * 向所有在线玩家发送兼容性提示消息
     */
    @SuppressWarnings("null")
    public static void notifyPlayersOfCompatibilityIssue() {
        // 检查配置是否启用警告
        if (!org.yanbwe.raritycore.config.ServerConfigManager.isEnableGetRarityWarning()) {
            return; // 配置禁用则不发送警告
        }
        
        if (!isVanillaRarityApiSupported && !hasNotifiedPlayer) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                // 向所有玩家发送世界消息(使用本地化字符串)
                server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.translatable("raritycore.message.vanilla_rarity_unavailable"),
                    false
                );
                server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.translatable("raritycore.message.vanilla_rarity_cause"),
                    false
                );
                hasNotifiedPlayer = true;
            }
        }
    }
    
    /**
     * 检测原版稀有度API是否可用
     * @return API是否可用
     */
    public static boolean isVanillaRarityApiAvailable() {
        if (vanillaRarityApiChecked) {
            return isVanillaRarityApiSupported;
        }
        
        try {
            // 测试API调用
            ItemStack testStack = ItemStack.EMPTY;
            Rarity testRarity = testStack.getRarity(); // 这会触发NoSuchMethodError如果API不可用
            
            // 验证返回值
            if (testRarity == null) {
                throw new IllegalStateException("getRarity() returned null");
            }
            
            vanillaRarityApiChecked = true;
            isVanillaRarityApiSupported = true;
            compatibilityFailureReason = null;
            return true;
        } catch (NoSuchMethodError e) {
            vanillaRarityApiChecked = true;
            isVanillaRarityApiSupported = false;
            compatibilityFailureReason = "NoSuchMethodError: " + e.getMessage();

            return false;
        } catch (LinkageError e) {
            vanillaRarityApiChecked = true;
            isVanillaRarityApiSupported = false;
            compatibilityFailureReason = "LinkageError: " + e.getMessage();

            return false;
        } catch (Throwable e) {
            vanillaRarityApiChecked = true;
            isVanillaRarityApiSupported = false;
            compatibilityFailureReason = "Unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage();

            return false;
        }
    }
    
    /**
     * 执行兼容性检查并记录诊断信息
     */
    public static void performCompatibilityCheck() {
        // 静默执行兼容性检查,只在DEBUG级别记录必要信息
        boolean apiAvailable = isVanillaRarityApiAvailable();
        
        if (!apiAvailable) {
            // 只在DEBUG级别输出基本信息
            RarityCore.LOGGER.debug("Vanilla rarity API unavailable - operating in reduced functionality mode");
        }
    }
    
    /**
     * 获取原版稀有度API是否支持
     * @return 原版稀有度API是否支持
     */
    public static boolean isVanillaRarityApiSupported() {
        return isVanillaRarityApiSupported;
    }
    
    /**
     * 获取兼容性失败原因
     * @return 兼容性失败原因
     */
    public static String getCompatibilityFailureReason() {
        return compatibilityFailureReason;
    }
}
