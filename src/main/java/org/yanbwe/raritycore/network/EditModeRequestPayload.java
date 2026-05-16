package org.yanbwe.raritycore.network;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.yanbwe.raritycore.RarityCore;
import org.yanbwe.raritycore.command.RarityCoreCommands;
import org.yanbwe.raritycore.edit.FullMatchConfigGenerator;
import org.yanbwe.raritycore.registry.RarityRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * 编辑模式请求载荷，用于客户端请求服务端修改物品稀有度。
 *
 * <p>支持两种编辑模式：
 * <ul>
 *   <li><b>NORMAL</b> — 直接注册/取消注册稀有度并保存到 FinalRarity.json</li>
 *   <li><b>FULLMATCH</b> — 生成基于 Item Data 的完全匹配配置文件（委托给 {@link FullMatchConfigGenerator}）</li>
 * </ul>
 *
 * <p><b>向后兼容性设计决策：</b>
 * 通过自定义 {@link StreamCodec} 检查缓冲区剩余可读字节数来判断新旧格式。
 * 旧版客户端仅发送 3 个字段（itemId, rarity, deleteMode），
 * 服务端在解码时检测到无额外数据后，默认 mode 为 {@code "NORMAL"}、parameters 为空 Map。
 * 该方案避免了增加新的 payload 类型或网络通道，在不破坏现有客户端的前提下进行平滑扩展。</p>
 *
 * @param itemId     目标物品的注册表 ID
 * @param rarity     稀有度等级（0 表示删除）
 * @param deleteMode 是否为删除模式
 * @param mode       编辑模式："NORMAL" 或 "FULLMATCH"（null/空字符串视为 NORMAL）
 * @param parameters 编辑参数映射（如 rarity, autoReload, ignore, stringContains）
 * @param fullMatchConfigJson FullMatch 模式下客户端预生成的完整配置 JSON 字符串
 */
public record EditModeRequestPayload(
    Identifier itemId,
    int rarity,
    boolean deleteMode,
    String mode,
    Map<String, String> parameters,
    String fullMatchConfigJson
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditModeRequestPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(RarityCore.MODID, NetworkConstants.EDIT_MODE_REQUEST_CHANNEL));

    /**
     * 参数映射的 StreamCodec：{@code Map<String, String>} ↔ 写入 String→String 键值对。
     */
    private static final StreamCodec<FriendlyByteBuf, Map<String, String>> PARAMETERS_CODEC =
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    /**
     * 协议版本标记字节。写在 3 个必填字段之后、可选字段之前。
     * <p>旧版客户端不写此字节（仅 3 字段），解码时通过 mark/reset 检测：
     * 若下一个字节 == PROTOCOL_VERSION(1) 则是新版格式，否则回退为旧版。
     * 该值 1 不会与 mode 字符串的 VarInt 首字节冲突
     * （"NORMAL" = 6, "FULLMATCH" = 9, 均 ≠ 1）。</p>
     */
    private static final byte PROTOCOL_VERSION = 1;

    /**
     * 自定义 StreamCodec，实现与旧版（3 字段）客户端的向后兼容。
     *
     * <p><b>新版编码顺序：</b>
     * {@code itemId | rarity | deleteMode | VERSION(1) | mode | parameters | [hasJson, json]}</p>
     * <p><b>旧版编码顺序（仅解码支持）：</b>
     * {@code itemId | rarity | deleteMode | mode}</p>
     * <p>解码时通过 mark/reset 检测版本字节：
     * 若第一个额外字节 == PROTOCOL_VERSION → 新版完整解码；
     * 否则回退到旧版，mode 从该字节开始读取，parameters 默认空 Map。</p>
     */
    public static final StreamCodec<FriendlyByteBuf, EditModeRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, EditModeRequestPayload payload) {
            Identifier.STREAM_CODEC.encode(buf, payload.itemId);
            buf.writeVarInt(payload.rarity);
            buf.writeBoolean(payload.deleteMode);
            buf.writeByte(PROTOCOL_VERSION); // ← 版本标记
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.mode != null ? payload.mode : "NORMAL");
            PARAMETERS_CODEC.encode(buf, payload.parameters != null ? payload.parameters : Map.of());
            // FullMatch config JSON — nullable
            String json = payload.fullMatchConfigJson;
            buf.writeBoolean(json != null);
            if (json != null) {
                ByteBufCodecs.STRING_UTF8.encode(buf, json);
            }
        }

        @Override
        public EditModeRequestPayload decode(FriendlyByteBuf buf) {
            Identifier itemId = Identifier.STREAM_CODEC.decode(buf);
            int rarity = buf.readVarInt();
            boolean deleteMode = buf.readBoolean();

            String mode;
            Map<String, String> parameters;
            String fullMatchConfigJson = null;

            if (buf.readableBytes() > 0) {
                buf.markReaderIndex();
                byte versionCandidate = buf.readByte();

                if (versionCandidate == PROTOCOL_VERSION) {
                    // 新版格式：版本匹配，继续读取剩余字段
                    mode = ByteBufCodecs.STRING_UTF8.decode(buf);
                    if (buf.readableBytes() > 0) {
                        parameters = PARAMETERS_CODEC.decode(buf);
                    } else {
                        parameters = Map.of();
                    }
                    if (buf.readableBytes() > 0 && buf.readBoolean()) {
                        fullMatchConfigJson = ByteBufCodecs.STRING_UTF8.decode(buf);
                    }
                } else {
                    // 旧版格式：versionCandidate 实际上是 mode 字符串 VarInt 的首字节
                    buf.resetReaderIndex();
                    mode = ByteBufCodecs.STRING_UTF8.decode(buf);
                    parameters = Map.of();
                }
            } else {
                mode = "NORMAL";
                parameters = Map.of();
            }

            return new EditModeRequestPayload(itemId, rarity, deleteMode, mode, parameters, fullMatchConfigJson);
        }
    };

    /**
     * 向后兼容的构造函数：默认 mode="NORMAL"、parameters 为空 Map、fullMatchConfigJson 为 null。
     * <p>旧版代码（如 {@link org.yanbwe.raritycore.edit.EditModeManager} NORMAL 模式）
     * 使用 3 参数构造此载荷时无需任何修改。</p>
     */
    public EditModeRequestPayload(Identifier itemId, int rarity, boolean deleteMode) {
        this(itemId, rarity, deleteMode, "NORMAL", Map.of(), null);
    }

    /**
     * FullMatch 模式构造函数：传入客户端预生成的配置 JSON。
     */
    public EditModeRequestPayload(Identifier itemId, int rarity, boolean deleteMode, String mode, String fullMatchConfigJson) {
        this(itemId, rarity, deleteMode, mode != null ? mode : "FULLMATCH", Map.of(), fullMatchConfigJson);
    }

    @Override
    public Type<EditModeRequestPayload> type() {
        return TYPE;
    }

    /**
     * 在服务端处理编辑模式请求。
     *
     * <p>执行权限检查后按 mode 分派：</p>
     * <ul>
     *   <li><b>NORMAL</b> — 现有行为：注册/取消注册稀有度并保存到 FinalRarity.json</li>
     *   <li><b>FULLMATCH</b> — 调用 {@link FullMatchConfigGenerator#generateOnServer}（当前为 stub 实现）</li>
     * </ul>
     * <p>mode 为 null、空字符串或非 {@code "FULLMATCH"} 时均回退到 NORMAL 分支。</p>
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                RarityCore.LOGGER.warn("EditModeRequestPayload received on client side or from null player, ignoring");
                return;
            }

            if (!Commands.LEVEL_MODERATORS.check(serverPlayer.permissions())) {
                RarityCore.LOGGER.warn("Player {} without sufficient permissions tried to use edit mode",
                    serverPlayer.getName().getString());
                return;
            }

            String effectiveMode = (mode != null && !mode.isEmpty()) ? mode : "NORMAL";

            RarityCore.LOGGER.info("Processing edit mode request from player {}: Item={}, Rarity={}, Delete={}, Mode={}",
                serverPlayer.getName().getString(), itemId, rarity, deleteMode, effectiveMode);

            try {
                    if ("FULLMATCH".equals(effectiveMode)) {
                        handleFullMatch(serverPlayer);
                    } else {
                    handleNormal(serverPlayer);
                }
            } catch (Exception e) {
                RarityCore.LOGGER.error("Error processing edit mode request: {}", e.getMessage());
            }
        });
    }

    /**
     * NORMAL 模式处理：注册/取消注册物品稀有度并保存配置文件。
     * <p>与旧版行为完全一致，仅从 handle() 中提取为独立方法。</p>
     */
    private void handleNormal(ServerPlayer serverPlayer) {
        var itemHolder = BuiltInRegistries.ITEM.get(itemId);
        if (itemHolder.isPresent() && !itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            var item = itemHolder.get().value();
            if (deleteMode) {
                RarityRegistry.unregister(item, false);
                RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), 0);
                RarityCore.LOGGER.info("Deleted rarity for item {} by player {}",
                    itemId, serverPlayer.getName().getString());
            } else {
                RarityRegistry.register(item, rarity, false);
                RarityCoreCommands.saveRarityToConfigPublic(itemId.toString(), rarity);
                RarityCore.LOGGER.info("Set rarity {} for item {} by player {}",
                    rarity, itemId, serverPlayer.getName().getString());
            }
        } else {
            RarityCore.LOGGER.warn("Invalid item ID in edit mode request: {}", itemId);
        }
    }

    /**
     * FULLMATCH 模式处理：使用客户端预生成的配置 JSON 写入文件。
     */
    private void handleFullMatch(ServerPlayer serverPlayer) {
        if (fullMatchConfigJson == null || fullMatchConfigJson.isEmpty()) {
            RarityCore.LOGGER.warn("FULLMATCH mode: no config JSON provided, skipping");
            return;
        }
        RarityCore.LOGGER.info("FULLMATCH mode: writing config for item={}, rarity={}, player={}",
            itemId, rarity, serverPlayer.getName().getString());
        FullMatchConfigGenerator.generateOnServer(itemId, rarity, fullMatchConfigJson);
    }
}
