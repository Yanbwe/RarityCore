package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;

/**
 * 物品数据条件表达式抽象类
 * 完全基于 DataComponent API，不再使用 NBT CompoundTag.
 */
public abstract class ItemDataCondition {
    
    /**
     * 匹配类型枚举
     */
    public enum MatchType {
        EQUALS,      // 等值匹配
        RANGE,       // 范围匹配
        CONTAINS,    // 包含匹配
        EXISTS,      // 存在性匹配
        REGEX,       // 正则表达式匹配
        GREATER_THAN, // 大于匹配
        LESS_THAN    // 小于匹配
    }
    
    protected final String path;
    protected final MatchType type;
    protected final String description;
    
    /**
     * 构造函数
     * @param path 物品数据路径
     * @param type 匹配类型
     * @param description 条件描述
     */
    public ItemDataCondition(String path, MatchType type, String description) {
        this.path = path != null ? path : "";
        this.type = type != null ? type : MatchType.EQUALS;
        this.description = description != null ? description : "";
    }
    
    /**
     * 简化构造函数
     */
    public ItemDataCondition(String path, MatchType type) {
        this(path, type, "");
    }
    
    /**
     * 执行匹配逻辑 (基于 DataComponent API)
     * @param components 物品的 DataComponentMap
     * @param itemStack  物品堆 (用于 id/count 等路径)
     * @return 是否匹配成功
     */
    public abstract boolean matches(DataComponentMap components, ItemStack itemStack);
    
    /**
     * 获取物品数据路径
     */
    public String getPath() {
        return path;
    }
    
    /**
     * 获取匹配类型
     */
    public MatchType getType() {
        return type;
    }
    
    /**
     * 获取条件描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 工厂方法：创建等值匹配条件
     */
    public static ItemDataCondition equals(String path, Object value) {
        return new EqualsCondition(path, value);
    }
    
    /**
     * 工厂方法：创建范围匹配条件
     */
    public static ItemDataCondition range(String path, Number min, Number max) {
        return new RangeCondition(path, min, max);
    }
    
    /**
     * 工厂方法：创建包含匹配条件
     */
    public static ItemDataCondition contains(String path, String substring) {
        return new ContainsCondition(path, substring);
    }
    
    /**
     * 工厂方法：创建存在性匹配条件
     */
    public static ItemDataCondition exists(String path) {
        return new ExistsCondition(path);
    }
    
    @Override
    public String toString() {
        return String.format("ItemDataCondition{path='%s', type=%s}", 
                           path, type);
    }
}