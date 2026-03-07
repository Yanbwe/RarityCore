package org.yanbwe.raritycore.calc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.nbtmatching.ContainsCondition;
import org.yanbwe.raritycore.nbtmatching.EqualsCondition;
import org.yanbwe.raritycore.nbtmatching.NbtCondition;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NBT 规则生成器
 * 根据物品 NBT 生成模糊匹配规则
 */
public class NbtRuleGenerator {
    
    /**
     * 从 NBT 标签生成条件列表
     */
    public static List<NbtCondition> generateConditions(CompoundTag nbtTag) {
        List<NbtCondition> conditions = new ArrayList<>();
        
        if (nbtTag == null || nbtTag.isEmpty()) {
            return conditions;
        }
        
        // 遍历所有 tag
        for (String key : nbtTag.getAllKeys()) {
            Tag tag = nbtTag.get(key);
            
            if (tag == null) {
                continue;
            }
            
            try {
                NbtCondition condition = createConditionForKey(key, tag);
                if (condition != null) {
                    conditions.add(condition);
                }
            } catch (Exception e) {
                RarityCore.LOGGER.debug("Failed to create NBT condition for key: {}", key, e);
            }
        }
        
        return conditions;
    }
    
    /**
     * 为单个 tag 创建条件
     */
    private static NbtCondition createConditionForKey(String key, Tag tag) {
        byte typeId = tag.getId();
        
        // 根据类型创建不同的条件
        switch (typeId) {
            case Tag.TAG_STRING:
                // 字符串使用 contains
                String stringValue = tag.getAsString();
                if (stringValue != null && !stringValue.isEmpty()) {
                    return new ContainsCondition(key, stringValue);
                }
                break;
                
            case Tag.TAG_INT:
            case Tag.TAG_LONG:
            case Tag.TAG_SHORT:
            case Tag.TAG_BYTE:
            case Tag.TAG_FLOAT:
            case Tag.TAG_DOUBLE:
                // 数值类型使用 equals
                return new EqualsCondition(key, getNumericValue(tag));
                
            case Tag.TAG_COMPOUND:
                // 复合标签，递归处理子标签
                CompoundTag compoundTag = (CompoundTag) tag;
                List<NbtCondition> subConditions = generateConditions(compoundTag);
                // 如果有子条件，使用父路径
                if (!subConditions.isEmpty()) {
                    // 这里暂时返回 null，因为需要更复杂的路径处理
                    // TODO: 实现复合路径的条件
                }
                break;
                
            case Tag.TAG_LIST:
                // 列表标签
                ListTag listTag = (ListTag) tag;
                if (!listTag.isEmpty()) {
                    // 对于列表，我们检查是否包含特定元素
                    return new ContainsCondition(key, serializeListTag(listTag));
                }
                break;
                
            default:
                // 其他类型使用 equals
                return new EqualsCondition(key, getNumericValue(tag));
        }
        
        return null;
    }
    
    /**
     * 获取数值标签的值
     */
    private static Number getNumericValue(Tag tag) {
        byte typeId = tag.getId();
        switch (typeId) {
            case Tag.TAG_INT:
                return ((net.minecraft.nbt.IntTag) tag).getAsInt();
            case Tag.TAG_LONG:
                return ((net.minecraft.nbt.LongTag) tag).getAsLong();
            case Tag.TAG_SHORT:
                return ((net.minecraft.nbt.ShortTag) tag).getAsShort();
            case Tag.TAG_BYTE:
                return ((net.minecraft.nbt.ByteTag) tag).getAsByte();
            case Tag.TAG_FLOAT:
                return ((net.minecraft.nbt.FloatTag) tag).getAsFloat();
            case Tag.TAG_DOUBLE:
                return ((net.minecraft.nbt.DoubleTag) tag).getAsDouble();
            default:
                return 0;
        }
    }
    
    /**
     * 序列化 ListTag 为字符串
     */
    private static String serializeListTag(ListTag listTag) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Tag tag : listTag) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            
            if (tag instanceof CompoundTag) {
                sb.append(((CompoundTag) tag).toString());
            } else {
                sb.append(tag.toString());
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
