package org.yanbwe.raritycore.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.function.Supplier;

public class RaritySyncPacket {
    public static SimpleChannel INSTANCE;

    /**
     * 记录客户端上次接收到的配置版本号。
     * 当版本号匹配时，跳过数据负载以避免重复处理。
     */
    private static int clientConfigVersion = 0;

    public static void initialize() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.RARITY_SYNC_CHANNEL),
                () -> NetworkConstants.PROTOCOL_VERSION,
                NetworkConstants.PROTOCOL_VERSION::equals,
                NetworkConstants.PROTOCOL_VERSION::equals
        );

        INSTANCE.messageBuilder(RaritySyncPacket.class, 0)
                .encoder(RaritySyncPacket::encode)
                .decoder(RaritySyncPacket::new)
                .consumerMainThread(RaritySyncPacket::handle)
                .add();
    }

    private final int configVersion;
    private final Map<ResourceLocation, Integer> rarityData;
    private final Map<ResourceLocation, Integer> autoRarityData;
    private final List<TagRuleEntry> tagRarityRules;

    /**
     * 从服务端构建同步包。
     *
     * @param configVersion  当前配置版本号
     * @param rarityData     手动稀有度映射 (FinalRarity.json)
     * @param autoRarityData 自动计算的稀有度映射 (auto_rarity.json)
     * @param tagRarityRules TagRarity 规则 (TagRarity.json)
     */
    public RaritySyncPacket(int configVersion,
                            Map<ResourceLocation, Integer> rarityData,
                            Map<ResourceLocation, Integer> autoRarityData,
                            List<TagRuleEntry> tagRarityRules) {
        this.configVersion = configVersion;
        this.rarityData = new ConcurrentHashMap<>(rarityData);
        this.autoRarityData = new ConcurrentHashMap<>(autoRarityData != null ? autoRarityData : Collections.emptyMap());
        this.tagRarityRules = new ArrayList<>(tagRarityRules != null ? tagRarityRules : Collections.emptyList());
    }

    /**
     * 在客户端解码数据包。
     * 格式：[varInt version] [NBT itemRarity] [NBT autoRarity] [varInt tagRuleCount] [tagRule...]
     */
    public RaritySyncPacket(FriendlyByteBuf buf) {
        this.configVersion = buf.readVarInt();

        boolean skip = clientConfigVersion > 0 && this.configVersion == clientConfigVersion;

        this.rarityData = skip ? null : decodeRarityMap(buf);
        this.autoRarityData = skip ? null : decodeRarityMap(buf);
        this.tagRarityRules = skip ? null : decodeTagRules(buf);

        if (skip) {
            RarityCore.LOGGER.debug("Rarity sync skipped: client already at version {}", clientConfigVersion);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(configVersion);
        encodeRarityMap(buf, rarityData);
        encodeRarityMap(buf, autoRarityData);
        encodeTagRules(buf, tagRarityRules);
    }

    // -- 序列化辅助方法 -------------------------------------------------

    private static Map<ResourceLocation, Integer> decodeRarityMap(FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        if (nbt == null || nbt.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<ResourceLocation, Integer> map = new ConcurrentHashMap<>();
        for (String key : nbt.getAllKeys()) {
            Tag tag = nbt.get(key);
            if (tag instanceof IntTag intTag) {
                map.put(ResourceLocation.parse(key), intTag.getAsInt());
            }
        }
        return map;
    }

    private static void encodeRarityMap(FriendlyByteBuf buf, Map<ResourceLocation, Integer> map) {
        CompoundTag nbt = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : map.entrySet()) {
            nbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        buf.writeNbt(nbt);
    }

    private static List<TagRuleEntry> decodeTagRules(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count <= 0) return Collections.emptyList();
        List<TagRuleEntry> rules = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rules.add(new TagRuleEntry(buf.readUtf(), buf.readVarInt()));
        }
        return rules;
    }

    private static void encodeTagRules(FriendlyByteBuf buf, List<TagRuleEntry> rules) {
        buf.writeVarInt(rules.size());
        for (TagRuleEntry rule : rules) {
            buf.writeUtf(rule.tagLocation());
            buf.writeVarInt(rule.rarity());
        }
    }

    // -- TagRarity 规则传输条目 -----------------------------------------

    /**
     * TagRarity 规则数据传输条目。
     */
    public static class TagRuleEntry {
        private final String tagLocation;
        private final int rarity;

        /**
         * @param tagLocation Tag 的资源路径字符串
         * @param rarity      稀有度等级 (1-7)
         */
        public TagRuleEntry(String tagLocation, int rarity) {
            if (tagLocation == null) throw new IllegalArgumentException("tagLocation must not be null");
            if (rarity < 1) throw new IllegalArgumentException("rarity must be >= 1, got " + rarity);
            this.tagLocation = tagLocation;
            this.rarity = rarity;
        }

        public String tagLocation() { return tagLocation; }
        public int rarity() { return rarity; }

        /** 在客户端反序列化为 TagKey&lt;Item&gt;。 */
        @SuppressWarnings("deprecation")
        public net.minecraft.tags.TagKey<net.minecraft.world.item.Item> toTagKey() {
            return net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.parse(tagLocation));
        }
    }

    // -- 客户端处理器 ---------------------------------------------------

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
                RarityCore.LOGGER.warn("RaritySyncPacket received on wrong side, ignoring");
                ctx.get().setPacketHandled(true);
                return;
            }

            if (rarityData == null) {
                RarityCore.LOGGER.debug("Rarity sync skipped: client version {} matches server", clientConfigVersion);
                ctx.get().setPacketHandled(true);
                return;
            }

            RarityCore.LOGGER.debug("Rarity sync received: {} items, {} auto, {} tags, version {} -> {}",
                rarityData.size(), autoRarityData.size(), tagRarityRules.size(),
                clientConfigVersion, this.configVersion);

            clientConfigVersion = this.configVersion;

            // 1) Manual registry (FinalRarity.json)
            RarityRegistry.ITEM_RARITY_MAP.clear();
            RarityRegistry.ITEM_RARITY_MAP.putAll(rarityData);

            // 2) Auto-calculated table (auto_rarity.json)
            RarityRegistry.applySyncedAutoRarity(autoRarityData);

            // 3) TagRarity config
            org.yanbwe.raritycore.config.TagRarityConfigManager.applySyncedRules(tagRarityRules);

            org.yanbwe.raritycore.client.CacheInvalidationListener.onNetworkSync();
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    /**
     * 重置客户端版本追踪器，强制下次登录时进行完整重新同步。
     */
    public static void resetClientVersion() {
        clientConfigVersion = 0;
        RarityCore.LOGGER.debug("Client rarity sync version reset");
    }

}
