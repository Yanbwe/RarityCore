package org.yanbwe.raritycore.network;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.itemdatamatching.*;

import java.util.ArrayList;
import java.util.List;

public record ItemDataSyncPayload(List<ItemDataRuleDataPayload> rules, boolean isFullSync) implements CustomPacketPayload {
    /** 紧凑构造函数 — 对规则列表大小进行边界检查，超限仅记录警告不阻止发送 */
    public ItemDataSyncPayload {
        int size = rules.size();
        if (size > NetworkConstants.MAX_ITEM_DATA_RULES) {
            RarityCore.LOGGER.warn("ItemDataSyncPayload: Rules list size {} exceeds recommended limit of {} entries. "
                + "This may cause network performance degradation or client buffer overflow.",
                size, NetworkConstants.MAX_ITEM_DATA_RULES);
        }
    }

    public static final CustomPacketPayload.Type<ItemDataSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.ITEM_DATA_SYNC_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, ItemDataSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ItemDataRuleDataPayload.STREAM_CODEC),
            ItemDataSyncPayload::rules,
            ByteBufCodecs.BOOL,
            ItemDataSyncPayload::isFullSync,
            ItemDataSyncPayload::new);

    @Override
    public Type<ItemDataSyncPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            RarityCore.LOGGER.debug("接收物品数据匹配规则同步包,规则数量: {}, 全量同步: {}",
                rules.size(), isFullSync);

            if (isFullSync) {
                ItemDataRarityMatcher.clearAllRules();
            }

            int appliedCount = 0;
            for (ItemDataRuleDataPayload ruleData : rules) {
                try {
                    ItemDataMatchRule rule = ruleData.toRule();
                    if (rule != null) {
                        ItemDataRarityMatcher.registerRule(rule);
                        appliedCount++;
                    }
                } catch (Exception e) {
                    RarityCore.LOGGER.warn("应用物品数据规则时出错: {}", e.getMessage());
                }
            }

            SimpleItemDataCache.reinitializeCache();

            RarityCore.LOGGER.info("物品数据匹配规则同步完成: 应用 {} 条规则", appliedCount);
        });
    }

    public record ItemDataRuleDataPayload(String itemId, int priority, int rarity, boolean enabled,
                                     String description, List<ConditionDataPayload> conditions) {
        public static final StreamCodec<FriendlyByteBuf, ItemDataRuleDataPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                ItemDataRuleDataPayload::itemId,
                ByteBufCodecs.VAR_INT,
                ItemDataRuleDataPayload::priority,
                ByteBufCodecs.VAR_INT,
                ItemDataRuleDataPayload::rarity,
                ByteBufCodecs.BOOL,
                ItemDataRuleDataPayload::enabled,
                ByteBufCodecs.STRING_UTF8,
                ItemDataRuleDataPayload::description,
                ByteBufCodecs.collection(ArrayList::new, ConditionDataPayload.STREAM_CODEC),
                ItemDataRuleDataPayload::conditions,
                ItemDataRuleDataPayload::new);

        public ItemDataMatchRule toRule() {
            try {
                ResourceLocation itemLoc = ResourceLocation.parse(itemId);
                List<ItemDataCondition> itemDataConditions = new ArrayList<>();

                for (ConditionDataPayload conditionData : conditions) {
                    ItemDataCondition condition = conditionData.toCondition();
                    if (condition != null) {
                        itemDataConditions.add(condition);
                    }
                }

                return ItemDataMatchRule.builder()
                    .itemId(itemLoc)
                    .conditions(itemDataConditions)
                    .priority(priority)
                    .rarity(rarity)
                    .enabled(enabled)
                    .description(description)
                    .build();

            } catch (Exception e) {
                RarityCore.LOGGER.warn("转换物品数据规则数据时出错: {}", e.getMessage());
                return null;
            }
        }
    }

    public record ConditionDataPayload(String path, String type, String description, String valueData) {
        public static final StreamCodec<FriendlyByteBuf, ConditionDataPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                ConditionDataPayload::path,
                ByteBufCodecs.STRING_UTF8,
                ConditionDataPayload::type,
                ByteBufCodecs.STRING_UTF8,
                ConditionDataPayload::description,
                ByteBufCodecs.STRING_UTF8,
                ConditionDataPayload::valueData,
                ConditionDataPayload::new);

        public ItemDataCondition toCondition() {
            try {
                switch (ItemDataCondition.MatchType.valueOf(type)) {
                    case EQUALS:
                        return deserializeEqualsCondition();
                    case EXISTS:
                        return new ExistsCondition(path, description);
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

        private EqualsCondition deserializeEqualsCondition() {
            JsonObject data = parseJson(valueData);
            if (data.has("value")) {
                var valueElement = data.get("value");
                if (valueElement.isJsonPrimitive()) {
                    var primitive = valueElement.getAsJsonPrimitive();
                    if (primitive.isString()) {
                        return new EqualsCondition(path, primitive.getAsString(), description);
                    } else if (primitive.isNumber()) {
                        return new EqualsCondition(path, primitive.getAsNumber().longValue(), description);
                    } else if (primitive.isBoolean()) {
                        return new EqualsCondition(path, primitive.getAsBoolean(), description);
                    }
                }
            }
            return new EqualsCondition(path, valueData, description);
        }

        private RangeCondition deserializeRangeCondition() {
            JsonObject data = parseJson(valueData);
            long min = data.has("min") ? data.get("min").getAsLong() : Long.MIN_VALUE;
            long max = data.has("max") ? data.get("max").getAsLong() : Long.MAX_VALUE;
            return new RangeCondition(path, min, max, description);
        }

        private ContainsCondition deserializeContainsCondition() {
            JsonObject data = parseJson(valueData);
            String value = data.has("value") ? data.get("value").getAsString() : "";
            return new ContainsCondition(path, value, description);
        }

        private JsonObject parseJson(String json) {
            if (json == null || json.isEmpty()) {
                return new JsonObject();
            }
            try {
                return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            } catch (Exception e) {
                return new JsonObject();
            }
        }
    }
}