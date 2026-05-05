package org.yanbwe.raritycore.compat.tacz;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * TacZ (Timeless and Classics Zero) 兼容适配器。
 *
 * <p>由于 TacZ 模组的枪械、配件、子弹共用基础物品 ID（如 {@code tacz:modern_kinetic_gun}），
 * 实际子类型由 Data Component 区分。本适配器通过检测物品栈上的 TacZ Data Component 键名
 * 来识别物品子类型，并提取对应的 ID 值。</p>
 *
 * <h3>TacZ Data Component 键名</h3>
 * <table>
 *   <tr><th>类型</th><th>组件键</th><th>ID 字段</th></tr>
 *   <tr><td>枪械</td><td>{@code tacz:gun}</td><td>{@code GunId}</td></tr>
 *   <tr><td>配件</td><td>{@code tacz:attachment}</td><td>{@code AttachmentId}</td></tr>
 *   <tr><td>子弹</td><td>{@code tacz:ammo}</td><td>{@code AmmoId}</td></tr>
 * </table>
 *
 * <p>所有与 TacZ API 的交互均通过 {@link #init()} 中的 {@code Class.forName}
 * 和反射完成，不产生编译期依赖。当 TacZ 模组未加载时，所有公共方法安全返回
 * null 或 {@link TacZItemType#NONE}。</p>
 *
 * @see org.yanbwe.raritycore.compat.apotheosis.ApotheosisAdapter
 * @see org.yanbwe.raritycore.compat.ironsspells.IronSpellsAdapter
 */
public class TacZAdapter {

    /** TacZ 物品子类型枚举 */
    public enum TacZItemType {
        /** 枪械 — 组件键 {@code tacz:gun}，含 {@code GunId} 字段 */
        GUN,
        /** 配件 — 组件键 {@code tacz:attachment}，含 {@code AttachmentId} 字段 */
        ATTACHMENT,
        /** 子弹 — 组件键 {@code tacz:ammo}，含 {@code AmmoId} 字段 */
        AMMO,
        /** 非 TacZ 物品 */
        NONE
    }

    private static boolean isTacZLoaded = false;
    private static boolean isInitialized = false;

    private static DataComponentType<?> gunComponentType;
    private static DataComponentType<?> attachmentComponentType;
    private static DataComponentType<?> ammoComponentType;

    // ──────────── 初始化 ────────────

    /**
     * 初始化适配器。
     * <p>先通过 {@code Class.forName} 检测 TacZ 入口类是否存在，
     * 再通过 {@link ModList#get()#isLoaded(String)} 确认 FML 加载状态，
     * 最后从 {@link BuiltInRegistries#DATA_COMPONENT_TYPE} 获取三个组件类型。</p>
     * <p>幂等操作 — 重复调用不会重新初始化。</p>
     */
    public static void init() {
        if (isInitialized) {
            return;
        }

        // 通过 Class.forName 检测模组类是否存在（类加载层面）
        try {
            Class.forName("com.tacz.guns.api.TimelessAPI");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("TacZ mod not detected via Class.forName, skipping compatibility adapter");
            isInitialized = true;
            return;
        }

        // ModList 确认模组加载状态（FML 层面）
        isTacZLoaded = ModList.get().isLoaded("tacz");

        if (!isTacZLoaded) {
            RarityCore.LOGGER.debug("TacZ mod not loaded in ModList, skipping compatibility adapter");
            isInitialized = true;
            return;
        }

        try {
            // 获取三个 TacZ Data Component 类型
            ResourceLocation gunLoc = ResourceLocation.fromNamespaceAndPath("tacz", "gun");
            ResourceLocation attachmentLoc = ResourceLocation.fromNamespaceAndPath("tacz", "attachment");
            ResourceLocation ammoLoc = ResourceLocation.fromNamespaceAndPath("tacz", "ammo");

            gunComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(gunLoc);
            attachmentComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(attachmentLoc);
            ammoComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ammoLoc);

            if (gunComponentType == null && attachmentComponentType == null && ammoComponentType == null) {
                RarityCore.LOGGER.warn(
                    "TacZ component types not found in registry: gun={}, attachment={}, ammo={}",
                    gunLoc, attachmentLoc, ammoLoc);
                isTacZLoaded = false;
            } else {
                RarityCore.LOGGER.info(
                    "TacZ compatibility adapter initialized. gun={}, attachment={}, ammo={}",
                    gunComponentType != null ? gunLoc : "NOT_FOUND",
                    attachmentComponentType != null ? attachmentLoc : "NOT_FOUND",
                    ammoComponentType != null ? ammoLoc : "NOT_FOUND");
            }

            isInitialized = true;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize TacZ compatibility adapter", e);
            isInitialized = true;
        }
    }

    // ──────────── 公共查询方法 ────────────

    /**
     * 检查 TacZ 模组是否已加载。
     *
     * @return 模组是否已加载
     */
    public static boolean isLoaded() {
        if (!isInitialized) {
            init();
        }
        return isTacZLoaded;
    }

    /**
     * 判断物品栈是否为 TacZ 物品（枪械/配件/子弹）。
     *
     * @param itemStack 物品栈
     * @return true 表示是 TacZ 物品
     */
    public static boolean isTacZItem(@Nullable ItemStack itemStack) {
        return getTacZItemType(itemStack) != TacZItemType.NONE;
    }

    /**
     * 获取物品栈的 TacZ 子类型。
     *
     * @param itemStack 物品栈
     * @return 子类型枚举，非 TacZ 物品返回 {@link TacZItemType#NONE}
     */
    public static TacZItemType getTacZItemType(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return TacZItemType.NONE;
        }
        if (!isInitialized) {
            init();
        }
        if (!isTacZLoaded) {
            return TacZItemType.NONE;
        }

        try {
            if (gunComponentType != null && itemStack.has(gunComponentType)) {
                return TacZItemType.GUN;
            }
            if (attachmentComponentType != null && itemStack.has(attachmentComponentType)) {
                return TacZItemType.ATTACHMENT;
            }
            if (ammoComponentType != null && itemStack.has(ammoComponentType)) {
                return TacZItemType.AMMO;
            }
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to check TacZ item type for item: {}", itemStack.getItem(), e);
        }

        return TacZItemType.NONE;
    }

    /**
     * 获取 TacZ 物品的 ID（GunId / AttachmentId / AmmoId）。
     * <p>通过反射调用 Data Component 值对象上的对应方法获取 ID。
     * 按 {@code GunId()} → {@code getGunId()} → 嵌套 record 字段 的顺序尝试。
     * 对于 {@code ResourceLocation} 类型的返回值，提取其字符串表示。</p>
     *
     * @param itemStack 物品栈
     * @return TacZ 物品的 ID 字符串，如果获取失败或不是 TacZ 物品则返回 null
     */
    @Nullable
    public static String getTacZItemId(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        if (!isInitialized) {
            init();
        }
        if (!isTacZLoaded) {
            return null;
        }

        try {
            TacZItemType type = getTacZItemType(itemStack);
            if (type == TacZItemType.NONE) {
                return null;
            }

            String idFieldName = switch (type) {
                case GUN -> "GunId";
                case ATTACHMENT -> "AttachmentId";
                case AMMO -> "AmmoId";
                default -> null;
            };

            if (idFieldName == null) return null;

            DataComponentType<?> componentType = switch (type) {
                case GUN -> gunComponentType;
                case ATTACHMENT -> attachmentComponentType;
                case AMMO -> ammoComponentType;
                default -> null;
            };

            if (componentType == null) return null;

            Object componentValue = itemStack.get(componentType);
            if (componentValue == null) return null;

            // 策略1: 反射调用 record 的访问器方法 GunId() / AttachmentId() / AmmoId()
            String id = extractIdViaMethod(componentValue, idFieldName);
            if (id != null) return id;

            // 策略2: 反射调用 getter 方法 getGunId() / getAttachmentId() / getAmmoId()
            id = extractIdViaMethod(componentValue, "get" + idFieldName);
            if (id != null) return id;

            // 策略3: 遍历组件值中 name() 方法返回的字符串（record 组件）
            id = extractIdViaRecordComponents(componentValue, idFieldName);
            if (id != null) return id;

            // 策略4: toString() 后备解析 — 尝试从字符串表示中匹配常见模式
            id = extractIdViaToString(componentValue, idFieldName);
            if (id != null) return id;

            RarityCore.LOGGER.debug("TacZ: Failed to extract {} from component value: {}",
                idFieldName, componentValue.getClass().getName());
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get TacZ item ID for item: {}", itemStack.getItem(), e);
        }

        return null;
    }

    /**
     * 获取 TacZ Data Component 的注册键名。
     * 用于生成 ItemData 配置的条件路径。
     *
     * @param type 物品类型
     * @return 组件键名（如 "tacz:gun"），如果不可用则返回 null
     */
    @Nullable
    public static String getComponentKey(TacZItemType type) {
        if (!isInitialized) init();
        if (!isTacZLoaded) return null;

        ResourceLocation loc = switch (type) {
            case GUN -> gunComponentType != null
                ? BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(gunComponentType) : null;
            case ATTACHMENT -> attachmentComponentType != null
                ? BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(attachmentComponentType) : null;
            case AMMO -> ammoComponentType != null
                ? BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(ammoComponentType) : null;
            default -> null;
        };

        return loc != null ? loc.toString() : null;
    }

    /**
     * 获取 TacZ Data Component 中 ID 字段的名称。
     *
     * @param type 物品类型
     * @return ID 字段名（"GunId" / "AttachmentId" / "AmmoId"），不可用时返回 null
     */
    @Nullable
    public static String getIdFieldName(TacZItemType type) {
        return switch (type) {
            case GUN -> "GunId";
            case ATTACHMENT -> "AttachmentId";
            case AMMO -> "AmmoId";
            default -> null;
        };
    }

    // ──────────── ID 提取策略 ────────────

    /**
     * 通过反射调用指定方法名提取 ID。
     */
    @Nullable
    private static String extractIdViaMethod(Object componentValue, String methodName) {
        try {
            Object result = componentValue.getClass().getMethod(methodName).invoke(componentValue);
            return convertIdResult(result);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 尝试通过 record 组件遍历提取 ID。
     * Java record 类有 {@code getRecordComponents()} 方法返回组件数组，
     * 每个组件有 {@code getName()} 和对应的 {@code getAccessor()} 方法。
     */
    @Nullable
    private static String extractIdViaRecordComponents(Object componentValue, String targetFieldName) {
        try {
            // 尝试获取record components
            java.lang.reflect.Method getRecordComponents =
                componentValue.getClass().getMethod("getRecordComponents");
            Object[] components = (Object[]) getRecordComponents.invoke(componentValue);

            if (components == null) return null;

            for (Object component : components) {
                String name = (String) component.getClass().getMethod("getName").invoke(component);
                if (targetFieldName.equals(name)) {
                    java.lang.reflect.Method accessor =
                        (java.lang.reflect.Method) component.getClass().getMethod("getAccessor").invoke(component);
                    Object result = accessor.invoke(componentValue);
                    return convertIdResult(result);
                }
            }
        } catch (Exception e) {
            // Not a record or reflection failed — fall through
        }
        return null;
    }

    /**
     * 从 toString() 字符串表示中解析 ID 值。
     * 支持的格式：
     * <ul>
     *   <li>{@code ClassName[GunId=tacz:m4a1, ...]} — record toString()</li>
     *   <li>{@code ClassName{GunId='tacz:m4a1', ...}} — 常见 toString()</li>
     * </ul>
     */
    @Nullable
    private static String extractIdViaToString(Object componentValue, String fieldName) {
        try {
            String str = componentValue.toString();
            if (str == null || str.isEmpty()) return null;

            // 模式: fieldName=value 或 fieldName='value'
            String pattern1 = fieldName + "=";
            int idx = str.indexOf(pattern1);
            if (idx >= 0) {
                int start = idx + pattern1.length();
                if (start < str.length()) {
                    // 跳过可能的引号
                    if (str.charAt(start) == '\'') {
                        start++;
                        int end = str.indexOf('\'', start);
                        return end > start ? str.substring(start, end) : str.substring(start);
                    }
                    // 提取到下一个逗号或括号
                    int end = str.indexOf(',', start);
                    if (end < 0) end = str.indexOf(']', start);
                    if (end < 0) end = str.indexOf('}', start);
                    return end > start ? str.substring(start, end).trim() : str.substring(start).trim();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 将反射调用结果转换为 ID 字符串。
     * 如果结果是 ResourceLocation，提取其字符串表示。
     */
    @Nullable
    private static String convertIdResult(Object result) {
        if (result == null) return null;
        if (result instanceof ResourceLocation rl) {
            return rl.toString();
        }
        if (result instanceof Optional<?> opt) {
            return opt.map(Object::toString).orElse(null);
        }
        return result.toString();
    }

    // ──────────── 重置 ────────────

    /**
     * 重置适配器状态（用于热重载场景）。
     */
    public static void reset() {
        isInitialized = false;
        isTacZLoaded = false;
        gunComponentType = null;
        attachmentComponentType = null;
        ammoComponentType = null;
    }
}
