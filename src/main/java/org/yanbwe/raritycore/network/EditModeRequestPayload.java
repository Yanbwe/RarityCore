package org.yanbwe.raritycore.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.cache.DualCacheManager;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.config.ConfigManager;
import org.yanbwe.raritycore.itemdatamatching.ItemDataConfigLoader;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 编辑模式请求载荷 — 客户端发送到服务端
 * v13 扩展: 支持 FullMatch 模式 (editMode="fullmatch", fullMatchConfigJson 包含预生成的配置文件 JSON)
 */
public record EditModeRequestPayload(
    ResourceLocation itemId,
    int rarity,
    boolean deleteMode,
    String editMode,
    String fullMatchConfigJson,
    boolean autoReload
) implements CustomPacketPayload {

    private static final Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    /** 紧凑构造函数 — 对载荷字段进行边界验证, v13: rarity≥0 无上限 */
    public EditModeRequestPayload {
        if (itemId == null) {
            RarityCore.LOGGER.warn("EditModeRequestPayload: itemId is null");
        }
        // v13: rarity ≥ 0 即可，不再限制上限；仅记录负值异常
        if (rarity < 0) {
            RarityCore.LOGGER.warn("EditModeRequestPayload: rarity {} is negative, which is unusual", rarity);
        }
    }

    public static final CustomPacketPayload.Type<EditModeRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.EDIT_MODE_REQUEST_CHANNEL));

    public static final StreamCodec<FriendlyByteBuf, EditModeRequestPayload> STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC,       EditModeRequestPayload::itemId,
        ByteBufCodecs.VAR_INT,               EditModeRequestPayload::rarity,
        ByteBufCodecs.BOOL,                  EditModeRequestPayload::deleteMode,
        ByteBufCodecs.STRING_UTF8,           EditModeRequestPayload::editMode,
        ByteBufCodecs.STRING_UTF8,           EditModeRequestPayload::fullMatchConfigJson,
        ByteBufCodecs.BOOL,                  EditModeRequestPayload::autoReload,
        EditModeRequestPayload::new
    );

    @Override
    public Type<EditModeRequestPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                RarityCore.LOGGER.warn("EditModeRequestPayload received on client side, ignoring");
                return;
            }

            if (!serverPlayer.hasPermissions(2)) {
                RarityCore.LOGGER.warn("Player {} without sufficient permissions tried to use edit mode",
                    serverPlayer.getName().getString());
                return;
            }

            // v13: 根据编辑模式分派到对应的服务端处理逻辑
            if ("fullmatch".equalsIgnoreCase(editMode) && !fullMatchConfigJson.isEmpty()) {
                handleFullMatchRequest(serverPlayer);
            } else {
                handleNormalRequest(serverPlayer);
            }
        });
    }

    /**
     * Normal 模式服务端处理: 写入 FinalRarity.json
     */
    private void handleNormalRequest(ServerPlayer player) {
        RarityCore.LOGGER.info("Processing edit mode request (Normal) from {}: Item={}, Rarity={}, Delete={}",
            player.getName().getString(), itemId, rarity, deleteMode);

        try {
            var item = BuiltInRegistries.ITEM.get(itemId);
            if (item != null && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                if (deleteMode) {
                    RarityRegistry.unregister(item, false);
                    RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), 0);
                    RarityCore.LOGGER.info("Deleted rarity for item {} by player {}",
                        itemId, player.getName().getString());
                } else {
                    RarityRegistry.register(item, rarity, false);
                    RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), rarity);
                    RarityCore.LOGGER.info("Set rarity {} for item {} by player {}",
                        rarity, itemId, player.getName().getString());
                }
            } else {
                RarityCore.LOGGER.warn("Invalid item ID in edit mode request: {}", itemId);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error processing edit mode request: {}", e.getMessage());
        }
    }

    /**
     * FullMatch 模式服务端处理: 保存客户端生成的 Item Data 匹配配置文件
     */
    private void handleFullMatchRequest(ServerPlayer player) {
        RarityCore.LOGGER.info("Processing edit mode request (FullMatch) from {}: Item={}, Rarity={}",
            player.getName().getString(), itemId, rarity);

        try {
            // 解析客户端发来的配置 JSON
            JsonObject config = JsonParser.parseString(fullMatchConfigJson).getAsJsonObject();

            // 验证基本字段
            if (!config.has("item_id") || !config.has("conditions") || !config.has("rarity")) {
                RarityCore.LOGGER.warn("FullMatch: Received invalid config from client {}", player.getName().getString());
                return;
            }

            // 保存到服务端配置目录
            Path configDir = ConfigManager.getConfigDirPath().resolve("item_data_matches");
            Files.createDirectories(configDir);

            String safeName = itemId.toString().replace(':', '_');
            Path configFile = findNextAvailableFile(configDir, "edit_" + safeName);

            String jsonString = GSON.toJson(config);
            Files.writeString(configFile, jsonString);

            RarityCore.LOGGER.info("FullMatch: Saved config {} by player {}",
                configFile.getFileName(), player.getName().getString());

            // autoReload: 重新加载配置使规则立即生效
            if (autoReload) {
                ItemDataConfigLoader.loadAllConfigs();
                DualCacheManager.handleConfigReload();
                RarityCore.LOGGER.info("FullMatch: Auto-reloaded configs after save");
            }

            // 同步到所有客户端
            RarityRegistry.syncRarityToClientsWithRetry();
        } catch (Exception e) {
            RarityCore.LOGGER.error("Error processing FullMatch request: {}", e.getMessage(), e);
        }
    }

    /**
     * 查找下一个可用的文件名
     */
    private static Path findNextAvailableFile(Path configDir, String baseName) {
        Path candidate = configDir.resolve(baseName + ".json");
        if (!Files.exists(candidate)) {
            return candidate;
        }
        for (int i = 1; i <= 999; i++) {
            candidate = configDir.resolve(baseName + "_" + i + ".json");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return configDir.resolve(baseName + "_" + System.currentTimeMillis() + ".json");
    }
}
