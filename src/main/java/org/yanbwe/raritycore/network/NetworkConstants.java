package org.yanbwe.raritycore.network;

/**
 * 网络相关常量类
 * 定义所有网络通信相关的常量值
 */
public class NetworkConstants {
    
    // 协议版本号
    public static final String PROTOCOL_VERSION = "1.0";
    
    // 网络通道名称
    public static final String RARITY_SYNC_CHANNEL = "rarity_sync";
    public static final String INCREMENTAL_SYNC_CHANNEL = "incremental_sync";
    public static final String NBT_SYNC_CHANNEL = "nbt_sync";
    public static final String ITEM_DATA_SYNC_CHANNEL = "item_data_sync";
    public static final String EDIT_MODE_REQUEST_CHANNEL = "edit_mode_request";

    // 网络载荷大小限制常量 — 防止超大包导致的客户端缓冲区溢出
    /** RaritySyncPayload 单次传输最大条目数，超过时记录警告但仍会发送 */
    public static final int MAX_RARITY_SYNC_ENTRIES = 10000;
    /** IncrementalSyncPayload 单次传输最大操作数，超过时记录警告但仍会发送 */
    public static final int MAX_INCREMENTAL_OPERATIONS = 500;
    /** ItemDataSyncPayload 单次传输最大规则数，超过时记录警告但仍会发送 */
    public static final int MAX_ITEM_DATA_RULES = 10000;
    /** 载荷序列化后大小警告阈值 (5MB)，超过时记录警告 */
    public static final int MAX_PAYLOAD_SERIALIZED_SIZE_BYTES = 5 * 1024 * 1024;
}
