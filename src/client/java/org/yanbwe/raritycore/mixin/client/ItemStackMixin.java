package org.yanbwe.raritycore.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.yanbwe.raritycore.client.RaritycoreClient;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.util.SimpleCacheManager;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void modifyItemNameColor(CallbackInfoReturnable<Text> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        
        // 检查是否启用物品名称变色功能
        if (!ConfigManager.isEnableItemNameColor()) {
            return;
        }
        
        // 获取物品的稀有度
        int rarity = SimpleCacheManager.getCachedRarity(stack);
        
        // 如果是普通稀有度（1），则不修改名称颜色
        if (rarity <= 1) {
            return;
        }
        
        // 获取对应的颜色格式
        Formatting color = getRarityFormatting(rarity);
        Text originalName = cir.getReturnValue();
        
        // 创建带颜色的文本
        MutableText coloredName = originalName.copy();
        coloredName.setStyle(Style.EMPTY.withColor(color));
        
        // 设置修改后的名称并取消默认返回值
        cir.setReturnValue(coloredName);
    }
    
    /**
     * 根据稀有度等级获取对应的颜色格式
     * @param rarity 稀有度等级 (2-7)
     * @return Formatting颜色
     */
    private Formatting getRarityFormatting(int rarity) {
        return switch (rarity) {
            case 1 -> Formatting.GRAY;          // 普通 - 灰色
            case 2 -> Formatting.GREEN;         // 稀有 - 绿色
            case 3 -> Formatting.DARK_AQUA;     // 罕见 - 深青色
            case 4 -> Formatting.LIGHT_PURPLE;  // 史诗 - 浅紫色
            case 5 -> Formatting.GOLD;          // 传说 - 金色
            case 6 -> Formatting.RED;           // 神话 - 红色
            case 7 -> Formatting.DARK_RED;      // 唯一 - 深红色
            default -> Formatting.WHITE;        // 默认白色
        };
    }
}