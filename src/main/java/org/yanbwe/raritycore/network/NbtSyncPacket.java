package org.yanbwe.raritycore.network;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.nbtmatching.NbtCondition;
import org.yanbwe.raritycore.nbtmatching.NbtMatchRule;
import org.yanbwe.raritycore.nbtmatching.SimpleNbtCache;
import org.yanbwe.raritycore.cache.DualCacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * NBT匹配规则同步包
 * 用于将服务端的NBT匹配配置同步到客户端
 */
public class NbtSyncPacket {
    public static SimpleChannel INSTANCE;
    
    public static void initialize() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.NBT_SYNC_CHANNEL),
                () -> NetworkConstants.PROTOCOL_VERSION,
                NetworkConstants.PROTOCOL_VERSION::equals,
                NetworkConstants.PROTOCOL_VERSION::equals
        );
        
        INSTANCE.messageBuilder(NbtSyncPacket.class, 0)
                .encoder(NbtSyncPacket::encode)
                .decoder(NbtSyncPacket::new)
                .consumerMainThread(NbtSyncPacket::handle)
                .add();
    }

    private List<NbtRuleData> rules;
    private boolean isFullSync; // true为全量同步,false为增量同步

    public NbtSyncPacket(List<NbtRuleData> rules, boolean isFullSync) {
        this.rules = rules;
        this.isFullSync = isFullSync;
    }

    public NbtSyncPacket(FriendlyByteBuf buf) {
        this.isFullSync = buf.readBoolean();
        int size = buf.readInt();
        this.rules = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            rules.add(new NbtRuleData(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(isFullSync);
        buf.writeInt(rules.size());
        for (NbtRuleData rule : rules) {
            rule.encode(buf);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            RarityCore.LOGGER.debug("接收NBT匹配规则同步包,规则数量: {}, 全量同步: {}", 
                rules.size(), isFullSync);
            
            if (isFullSync) {
                // 全量同步:清空现有规则并重新加载
                org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher.clearAllRules();
            }
            
            // 应用同步的规则
            int appliedCount = 0;
            for (NbtRuleData ruleData : rules) {
                try {
                    NbtMatchRule rule = ruleData.toRule();
                    if (rule != null) {
                        org.yanbwe.raritycore.nbtmatching.NbtRarityMatcher.registerRule(rule);
                        appliedCount++;
                    }
                } catch (Exception e) {
                    RarityCore.LOGGER.warn("应用NBT规则时出错: {}", e.getMessage());
                }
            }
            
            // 重新初始化客户端缓存
            SimpleNbtCache.reinitializeCache();
            DualCacheManager.handleConfigReload();
            
            RarityCore.LOGGER.info("NBT匹配规则同步完成: 应用 {} 条规则", appliedCount);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    public List<NbtRuleData> getRules() {
        return rules;
    }

    public boolean isFullSync() {
        return isFullSync;
    }

    /**
     * NBT规则数据传输对象
     */
    public static class NbtRuleData {
        private String itemId;
        private int priority;
        private int rarity;
        private boolean enabled;
        private String description;
        private List<ConditionData> conditions;

        public NbtRuleData(NbtMatchRule rule) {
            this.itemId = rule.getItemId().toString();
            this.priority = rule.getPriority();
            this.rarity = rule.getRarity();
            this.enabled = rule.isEnabled();
            this.description = rule.getDescription();
            
            this.conditions = new ArrayList<>();
            for (NbtCondition condition : rule.getConditions()) {
                this.conditions.add(new ConditionData(condition));
            }
        }

        public NbtRuleData(FriendlyByteBuf buf) {
            this.itemId = buf.readUtf();
            this.priority = buf.readInt();
            this.rarity = buf.readInt();
            this.enabled = buf.readBoolean();
            this.description = buf.readUtf();
            
            int conditionCount = buf.readInt();
            this.conditions = new ArrayList<>();
            for (int i = 0; i < conditionCount; i++) {
                conditions.add(new ConditionData(buf));
            }
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(itemId);
            buf.writeInt(priority);
            buf.writeInt(rarity);
            buf.writeBoolean(enabled);
            buf.writeUtf(description);
            
            buf.writeInt(conditions.size());
            for (ConditionData condition : conditions) {
                condition.encode(buf);
            }
        }

        public NbtMatchRule toRule() {
            try {
                ResourceLocation itemLoc = ResourceLocation.parse(itemId);
                List<NbtCondition> nbtConditions = new ArrayList<>();
                
                for (ConditionData conditionData : conditions) {
                    NbtCondition condition = conditionData.toCondition();
                    if (condition != null) {
                        nbtConditions.add(condition);
                    }
                }
                
                return NbtMatchRule.builder()
                    .itemId(itemLoc)
                    .conditions(nbtConditions)
                    .priority(priority)
                    .rarity(rarity)
                    .enabled(enabled)
                    .description(description)
                    .build();
                    
            } catch (Exception e) {
                RarityCore.LOGGER.warn("转换NBT规则数据时出错: {}", e.getMessage());
                return null;
            }
        }
    }

    /**
     * 条件数据传输对象
     */
    public static class ConditionData {
        private String path;
        private String type;
        private boolean fuzzyMatch;
        private String description;
        private String valueData; // JSON格式存储条件特定数据

        public ConditionData(NbtCondition condition) {
            this.path = condition.getPath();
            this.type = condition.getType().name();
            this.description = condition.getDescription();
            
            // 序列化条件特定数据
            this.valueData = serializeConditionData(condition);
        }

        public ConditionData(FriendlyByteBuf buf) {
            this.path = buf.readUtf();
            this.type = buf.readUtf();
            this.valueData = buf.readUtf();
            this.description = buf.readUtf();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(path);
            buf.writeUtf(type);
            buf.writeUtf(valueData);
            buf.writeUtf(description);
        }

        public NbtCondition toCondition() {
            try {
                switch (NbtCondition.MatchType.valueOf(type)) {
                    case EQUALS:
                        return deserializeEqualsCondition();
                    case EXISTS:
                        return new org.yanbwe.raritycore.nbtmatching.ExistsCondition(path, description);
                    case RANGE:
                        return deserializeRangeCondition();
                    case CONTAINS:
                        return deserializeContainsCondition();
                    default:
                        RarityCore.LOGGER.warn("不支持的条件类型: {}", type);
                        return null;
                }
            } catch (Exception e) {
                RarityCore.LOGGER.warn("反序列化条件时出错: {}", e.getMessage());
                return null;
            }
        }

        private String serializeConditionData(NbtCondition condition) {
            // 使用Gson进行安全的JSON序列化,自动处理特殊字符转义
            JsonObject data = new JsonObject();
            
            if (condition instanceof org.yanbwe.raritycore.nbtmatching.EqualsCondition) {
                Object value = ((org.yanbwe.raritycore.nbtmatching.EqualsCondition) condition).getExpectedValue();
                // 根据值类型进行适当的序列化
                if (value instanceof String) {
                    data.addProperty("value", (String) value);
                } else if (value instanceof Number) {
                    data.addProperty("value", (Number) value);
                } else if (value instanceof Boolean) {
                    data.addProperty("value", (Boolean) value);
                } else {
                    data.addProperty("value", value.toString());
                }
            } else if (condition instanceof org.yanbwe.raritycore.nbtmatching.RangeCondition) {
                org.yanbwe.raritycore.nbtmatching.RangeCondition range = 
                    (org.yanbwe.raritycore.nbtmatching.RangeCondition) condition;
                data.addProperty("min", range.getMinValue());
                data.addProperty("max", range.getMaxValue());
            } else if (condition instanceof org.yanbwe.raritycore.nbtmatching.ContainsCondition) {
                String substring = ((org.yanbwe.raritycore.nbtmatching.ContainsCondition) condition).getSubstring();
                data.addProperty("substring", substring);
            }
            
            return data.toString();
        }

        private NbtCondition deserializeEqualsCondition() {
            try {
                // 使用Gson安全解析JSON数据
                JsonObject data = com.google.gson.JsonParser.parseString(valueData).getAsJsonObject();
                if (data.has("value")) {
                    return new org.yanbwe.raritycore.nbtmatching.EqualsCondition(path, data.get("value").toString(), description);
                }
            } catch (Exception e) {
                RarityCore.LOGGER.warn("反序列化等值条件时出错: {}", e.getMessage());
            }
            return null;
        }

        private NbtCondition deserializeRangeCondition() {
            try {
                JsonObject data = com.google.gson.JsonParser.parseString(valueData).getAsJsonObject();
                double min = data.get("min").getAsDouble();
                double max = data.get("max").getAsDouble();
                return new org.yanbwe.raritycore.nbtmatching.RangeCondition(path, min, max, description);
            } catch (Exception e) {
                RarityCore.LOGGER.warn("反序列化范围条件时出错: {}", e.getMessage());
                return null;
            }
        }

        private NbtCondition deserializeContainsCondition() {
            try {
                JsonObject data = com.google.gson.JsonParser.parseString(valueData).getAsJsonObject();
                String substring = data.get("substring").getAsString();
                return new org.yanbwe.raritycore.nbtmatching.ContainsCondition(path, substring, description);
            } catch (Exception e) {
                RarityCore.LOGGER.warn("反序列化包含条件时出错: {}", e.getMessage());
                return null;
            }
        }
    }
}