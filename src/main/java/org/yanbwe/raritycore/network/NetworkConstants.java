package org.yanbwe.raritycore.network;

/**
 * 网络相关常量类
 * 定义所有网络通信相关的常量值
 */
public class NetworkConstants {
    
    // 协议版本号
    // 1.2.0：NBT 规则同步包改为保留条件值原始类型（Number/Boolean），
    //        旧版本因统一字符串化导致客户端数值型 equals 条件静默失效，故提升版本阻止新旧端混连
    public static final String PROTOCOL_VERSION = "1.2.0";
    
    // 网络通道名称
    public static final String RARITY_SYNC_CHANNEL = "rarity_sync";
    public static final String INCREMENTAL_SYNC_CHANNEL = "incremental_sync";
    public static final String NBT_SYNC_CHANNEL = "nbt_sync";
    public static final String EDIT_MODE_REQUEST_CHANNEL = "edit_mode_request";
}
