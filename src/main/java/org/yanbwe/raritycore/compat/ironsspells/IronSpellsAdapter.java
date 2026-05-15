package org.yanbwe.raritycore.compat.ironsspells;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

import java.lang.reflect.Method;

/**
 * Iron's Spells 'n Spellbooks 兼容适配器。
 *
 * <p>采用与 {@code ApotheosisAdapter} 一致的组件遍历模式：
 * 通过 {@code itemStack.getComponents()} 遍历所有 DataComponent，
 * 按 ResourceLocation 键名匹配 {@code irons_spellbooks:spell_container}，
 * 获取组件值后反射读取法术等级。</p>
 *
 * <p>映射规则：level 1→1, 2→2, …, 7→7, 8+→7</p>
 *
 * <p><b>组件匹配策略</b>（v14 修复）：
 * 不再依赖 {@code BuiltInRegistries.DATA_COMPONENT_TYPE.get(key)} 获取组件引用，
 * 因为模组注册的 DataComponentType 可能不在原版内置注册表中。
 * 改为通过 {@code ResourceLocation} 键名字符串比较 + 注册表反向查找双重方式匹配，
 * 确保 IronMagic 的 {@code spell_container} 组件在任何初始化时机都能被正确识别。</p>
 */
public class IronSpellsAdapter {

    private static volatile boolean isIronSpellsLoaded = false;
    private static volatile boolean isInitialized = false;

    /** 组件键名匹配用的 ResourceLocation，初始化后缓存在此 */
    private static volatile ResourceLocation spellContainerKey;

    /** 缓存的反射方法引用，避免热路径上重复调用 getMethod() */
    private static volatile Method cachedGetSpellAtIndex;
    private static volatile Method cachedGetLevel;

    /**
     * 初始化适配器。
     * <p>通过 Class.forName + ModList.isLoaded 双重确认模组加载状态，
     * 成功后将组件键名 (irons_spellbooks:spell_container) 缓存在字段中，
     * 供 {@link #getMappedRarity(ItemStack)} 在遍历组件时做比对。</p>
     */
    public static synchronized void init() {
        if (isInitialized) {
            return;
        }

        try {
            Class.forName("io.redspace.ironsspellbooks.IronsSpellbooks");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("Iron's Spells mod not detected via Class.forName, skipping");
            isInitialized = true;
            return;
        }

        isIronSpellsLoaded = ModList.get().isLoaded("irons_spellbooks");

        if (!isIronSpellsLoaded) {
            RarityCore.LOGGER.debug("Iron's Spells mod not loaded in ModList, skipping");
            isInitialized = true;
            return;
        }

        spellContainerKey = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_container");

        // 缓存反射方法引用，避免热路径上每次调用都执行 getMethod() 查找
        try {
            Class<?> spellContainerClass = Class.forName(
                "io.redspace.ironsspellbooks.api.spells.SpellContainer");
            cachedGetSpellAtIndex = spellContainerClass.getMethod("getSpellAtIndex", int.class);

            Class<?> spellDataClass = Class.forName(
                "io.redspace.ironsspellbooks.api.spells.SpellData");
            cachedGetLevel = spellDataClass.getMethod("getLevel");
        } catch (Exception e) {
            RarityCore.LOGGER.warn("Failed to cache Iron's Spells method references: {}", e.getMessage());
            isIronSpellsLoaded = false;
            isInitialized = true;
            return;
        }

        RarityCore.LOGGER.info("Iron's Spells compatibility adapter initialized, target component: {}", spellContainerKey);
        isInitialized = true;
    }

    /**
     * 检查 Iron's Spells 模组是否已加载。
     */
    public static boolean isLoaded() {
        if (!isInitialized) {
            init();
        }
        return isIronSpellsLoaded;
    }

    /**
     * 从 Iron's Spells 法术卷轴获取映射后的稀有度。
     *
     * <p>遍历物品所有 DataComponent，按键名找到
     * {@code irons_spellbooks:spell_container}，
     * 反射获取法术等级并映射为本模组稀有度。</p>
     *
     * <p><b>匹配策略</b>（v14 修复）：不再依赖 {@code BuiltInRegistries.DATA_COMPONENT_TYPE}
     * 的引用比较（因为模组组件可能不在原版内置注册表中），改为按优先级尝试：</p>
     * <ol>
     *   <li>通过 {@code BuiltInRegistries.DATA_COMPONENT_TYPE.getKey()} 反向查找 ResourceLocation 并比较</li>
     *   <li>后备：通过 {@code toString()} 字符串比较（适用于任何注册机制的组件）</li>
     * </ol>
     *
     * @param itemStack 物品栈
     * @return 稀有度 1–7，若无有效法术数据返回 null
     */
    public static Integer getMappedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        if (!isInitialized) {
            init();
        }
        if (!isIronSpellsLoaded || spellContainerKey == null) {
            return null;
        }

        try {
            DataComponentMap components = itemStack.getComponents();

            for (var tc : components) {
                if (isMatchingComponent(tc.type())) {
                    Object spellContainer = tc.value();
                    if (spellContainer == null) {
                        return null;
                    }

                    int level = invokeSpellLevel(spellContainer);
                    if (level < 1) {
                        return null;
                    }

                    return level;
                }
            }

            return null;

        } catch (Exception e) {
            RarityCore.LOGGER.warn("Iron's Spells adapter failed for item {}: {}",
                    itemStack.getItem(), e.toString());
            return null;
        }
    }

    /**
     * 检查给定 DataComponentType 是否为目标 {@code irons_spellbooks:spell_container}。
     *
     * <p>使用两层策略确保匹配：</p>
     * <ol>
     *   <li>优先通过 {@code BuiltInRegistries.DATA_COMPONENT_TYPE.getKey()} 获取注册名并比较
     *       （标准注册表路径，最快）</li>
     *   <li>后备通过 {@code toString()} 字符串比较
     *       （适用于不在 BuiltInRegistries 中的模组组件）</li>
     * </ol>
     *
     * @param type 要检查的 DataComponentType
     * @return 如果匹配返回 true
     */
    private static boolean isMatchingComponent(DataComponentType<?> type) {
        if (type == null || spellContainerKey == null) {
            return false;
        }

        // 策略1：通过注册表反向查找 ResourceLocation 并比较（最快路径）
        ResourceLocation registryKey = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (registryKey != null && registryKey.equals(spellContainerKey)) {
            return true;
        }

        // 策略2：后备使用 toString() 字符串比较
        // 适用于模组组件不在 BuiltInRegistries 中的情况
        String typeName = type.toString();
        return typeName != null && typeName.equals(spellContainerKey.toString());
    }

    /**
     * 反射调用 spellContainer.getSpellAtIndex(0).getLevel() 获取法术等级。
     * <p>SpellContainer API:
     * <ul>
     *   <li>{@code getSpellAtIndex(int)} → SpellData</li>
     *   <li>{@code SpellData.getLevel()} → int</li>
     * </ul>
     */
    private static int invokeSpellLevel(Object spellContainer) throws Exception {
        Object spellData = cachedGetSpellAtIndex.invoke(spellContainer, 0);
        if (spellData == null) {
            throw new IllegalStateException("No spell at index 0");
        }
        return (int) cachedGetLevel.invoke(spellData);
    }

    /**
     * 重置适配器状态（用于热重载）。
     */
    public static void reset() {
        isInitialized = false;
        isIronSpellsLoaded = false;
        spellContainerKey = null;
        cachedGetSpellAtIndex = null;
        cachedGetLevel = null;
    }
}
