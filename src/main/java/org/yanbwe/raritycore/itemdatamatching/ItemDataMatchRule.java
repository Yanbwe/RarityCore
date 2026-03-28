package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * 物品数据匹配规则定义
 * 描述了特定物品在满足某些物品数据条件时应该具有的稀有度
 */
public class ItemDataMatchRule {
    private final ResourceLocation itemId;
    private final List<ItemDataCondition> conditions;
    private final int priority;
    private final int rarity;
    private final boolean enabled;
    private final boolean fuzzyMatch;  // 整个规则的模糊匹配标志
    private final String description;
    
    /**
     * 构造函数
     * @param itemId 物品ID
     * @param conditions 匹配条件列表
     * @param priority 优先级(数值越大优先级越高)
     * @param rarity 稀有度等级(1-7)
     * @param enabled 是否启用
     * @param fuzzyMatch 是否启用模糊匹配(整个规则级别)
     * @param description 规则描述(可选)
     */
    public ItemDataMatchRule(ResourceLocation itemId, List<ItemDataCondition> conditions, 
                           int priority, int rarity, boolean enabled, boolean fuzzyMatch, String description) {
        this.itemId = Objects.requireNonNull(itemId, "物品ID不能为空");
        this.conditions = Objects.requireNonNull(conditions, "条件列表不能为空");
        this.priority = priority;
        this.rarity = rarity;
        this.enabled = enabled;
        this.fuzzyMatch = fuzzyMatch;
        this.description = description != null ? description : "";
    }
    
    /**
     * 简化构造函数(默认启用,精确匹配,无描述)
     */
    public ItemDataMatchRule(ResourceLocation itemId, List<ItemDataCondition> conditions, 
                           int priority, int rarity) {
        this(itemId, conditions, priority, rarity, true, false, "");
    }
    
    /**
     * 检查物品堆是否匹配此规则
     * @param itemStack 要检查的物品堆
     * @return 是否匹配
     */
    public boolean matches(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        CompoundTag nbt;
        try {
            net.minecraft.nbt.Tag tag = itemStack.save(net.minecraft.core.RegistryAccess.EMPTY);
            if (!(tag instanceof CompoundTag)) {
                return false;
            }
            nbt = (CompoundTag) tag;
        } catch (Exception e) {
            return false;
        }
        
        // 无论是模糊匹配还是精确匹配,条件之间都是AND关系
        // 先检查所有条件是否满足
        for (ItemDataCondition condition : conditions) {
            if (!condition.matches(nbt)) {
                return false; // 任一条件不满足就失败
            }
        }
        
        // 所有条件都满足后,根据匹配类型决定是否最终匹配成功
        if (fuzzyMatch) {
            // 模糊匹配:条件满足即可,允许额外标签
            return true;
        } else {
            // 精确匹配:除了满足条件外,还要检查是否有多余标签
            return hasExactItemDataStructure(itemStack, nbt);
        }
    }
    
    /**
     * 检查物品是否具有精确的物品数据结构(不允许额外标签)
     * @param itemStack 物品堆
     * @param nbt NBT标签
     * @return 是否具有精确结构
     */
    private boolean hasExactItemDataStructure(ItemStack itemStack, CompoundTag nbt) {
        // 这是一个简化的实现
        // 实际应用中可能需要更复杂的逻辑来检查是否有多余标签
        // 目前先返回true,表示暂时不检查额外标签
        return true;
    }
    
    // Getter方法
    public ResourceLocation getItemId() {
        return itemId;
    }
    
    public List<ItemDataCondition> getConditions() {
        return conditions;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public int getRarity() {
        return rarity;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isFuzzyMatch() {
        return fuzzyMatch;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemDataMatchRule that = (ItemDataMatchRule) o;
        return priority == that.priority &&
               rarity == that.rarity &&
               enabled == that.enabled &&
               fuzzyMatch == that.fuzzyMatch &&
               Objects.equals(itemId, that.itemId) &&
               Objects.equals(conditions, that.conditions) &&
               Objects.equals(description, that.description);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(itemId, conditions, priority, rarity, enabled, fuzzyMatch, description);
    }
    
    @Override
    public String toString() {
        return String.format("ItemDataMatchRule{itemId=%s, conditions=%d, priority=%d, rarity=%d, enabled=%s, fuzzyMatch=%s}", 
                           itemId, conditions.size(), priority, rarity, enabled, fuzzyMatch);
    }
    
    /**
     * 构建器模式
     */
    public static class Builder {
        private ResourceLocation itemId;
        private List<ItemDataCondition> conditions;
        private int priority = 0;
        private int rarity = 1;
        private boolean enabled = true;
        private boolean fuzzyMatch = false;  // 默认精确匹配
        private String description = "";
        
        public Builder itemId(ResourceLocation itemId) {
            this.itemId = itemId;
            return this;
        }
        
        public Builder conditions(List<ItemDataCondition> conditions) {
            this.conditions = conditions;
            return this;
        }
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder rarity(int rarity) {
            this.rarity = rarity;
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder fuzzyMatch(boolean fuzzyMatch) {
            this.fuzzyMatch = fuzzyMatch;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public ItemDataMatchRule build() {
            return new ItemDataMatchRule(itemId, conditions, priority, rarity, enabled, fuzzyMatch, description);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}