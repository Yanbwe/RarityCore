package org.yanbwe.raritycore.network;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.nbtmatching.*;

import java.util.ArrayList;
import java.util.List;

public record NbtSyncPayload(List<NbtRuleDataPayload> rules, boolean isFullSync) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NbtSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.NBT_SYNC_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, NbtSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, NbtRuleDataPayload.STREAM_CODEC),
            NbtSyncPayload::rules,
            ByteBufCodecs.BOOL,
            NbtSyncPayload::isFullSync,
            NbtSyncPayload::new);

    @Override
    public Type<NbtSyncPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            RarityCore.LOGGER.debug("接收NBT匹配规则同步包,规则数量: {}, 全量同步: {}",
                rules.size(), isFullSync);

            if (isFullSync) {
                NbtRarityMatcher.clearAllRules();
            }

            int appliedCount = 0;
            for (NbtRuleDataPayload ruleData : rules) {
                try {
                    NbtMatchRule rule = ruleData.toRule();
                    if (rule != null) {
                        NbtRarityMatcher.registerRule(rule);
                        appliedCount++;
                    }
                } catch (Exception e) {
                    RarityCore.LOGGER.warn("应用NBT规则时出错: {}", e.getMessage());
                }
            }

            SimpleNbtCache.reinitializeCache();

            RarityCore.LOGGER.info("NBT匹配规则同步完成: 应用 {} 条规则", appliedCount);
        });
    }

    public record NbtRuleDataPayload(String itemId, int priority, int rarity, boolean enabled,
                                     String description, List<ConditionDataPayload> conditions) {
        public static final StreamCodec<FriendlyByteBuf, NbtRuleDataPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                NbtRuleDataPayload::itemId,
                ByteBufCodecs.VAR_INT,
                NbtRuleDataPayload::priority,
                ByteBufCodecs.VAR_INT,
                NbtRuleDataPayload::rarity,
                ByteBufCodecs.BOOL,
                NbtRuleDataPayload::enabled,
                ByteBufCodecs.STRING_UTF8,
                NbtRuleDataPayload::description,
                ByteBufCodecs.collection(ArrayList::new, ConditionDataPayload.STREAM_CODEC),
                NbtRuleDataPayload::conditions,
                NbtRuleDataPayload::new);

        public NbtMatchRule toRule() {
            try {
                ResourceLocation itemLoc = ResourceLocation.parse(itemId);
                List<NbtCondition> nbtConditions = new ArrayList<>();

                for (ConditionDataPayload conditionData : conditions) {
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

        public NbtCondition toCondition() {
            try {
                switch (NbtCondition.MatchType.valueOf(type)) {
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