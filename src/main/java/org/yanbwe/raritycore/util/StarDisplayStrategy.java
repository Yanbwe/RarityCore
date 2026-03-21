package org.yanbwe.raritycore.util;

/**
 * 星星显示策略接口
 * 定义不同显示模式的统一接口
 */
public interface StarDisplayStrategy {
    
    /**
     * 根据稀有度获取显示字符串
     * @param rarity 稀有度等级
     * @return 显示的字符串，如果不应显示则返回空字符串
     */
    String getDisplayString(int rarity);
    
    /**
     * 获取策略名称
     * @return 策略名称
     */
    String getName();
}