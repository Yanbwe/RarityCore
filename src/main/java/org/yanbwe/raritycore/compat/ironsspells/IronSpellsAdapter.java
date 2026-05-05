package org.yanbwe.raritycore.compat.ironsspells;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

import java.util.List;

/**
 * Iron's Spells 'n Spellbooks 兼容适配器
 * <p>
 * 解析法术卷轴 Data Component 中的法术等级，映射为本模组稀有度。
 * 法术等级映射规则：level 1→稀有度1, level 2→2, ..., level 7→7, level 8+→7。
 * </p>
 * <p>
 * 优先级：与神化模组映射同级——Item Data 匹配配置之后、ITEM_RARITY_MAP 之前。
 * </p>
 * <p>
 * 数据结构：{@code irons_spellbooks:spell_container} 组件包含 {@code data()} 列表，
 * 列表中每个元素有 {@code level()} 方法。组件键名包含命名空间冒号。
 * </p>
 */
public class IronSpellsAdapter {

    private static boolean isIronSpellsLoaded = false;
    private static boolean isInitialized = false;
    private static DataComponentType<?> spellContainerType;

    /**
     * 初始化适配器
     * <p>
     * 先通过 Class.forName 检测 irons_spellbooks 模组类是否存在，
     * 再通过 {@link ModList#get()#isLoaded(String)} 确认模组加载状态，
     * 最后从 {@link BuiltInRegistries#DATA_COMPONENT_TYPE} 获取
     * {@code irons_spellbooks:spell_container} 组件类型。
     * </p>
     */
    public static void init() {
        if (isInitialized) {
            return;
        }

        // Class.forName 检测模组是否存在（类加载层面）
        try {
            Class.forName("io.redspace.ironsspellbooks.IronsSpellbooks");
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("Iron's Spells 'n Spellbooks mod not detected via Class.forName, skipping compatibility adapter");
            isInitialized = true;
            return;
        }

        // ModList 确认模组加载状态（FML 层面）
        isIronSpellsLoaded = ModList.get().isLoaded("irons_spellbooks");

        if (!isIronSpellsLoaded) {
            RarityCore.LOGGER.debug("Iron's Spells 'n Spellbooks mod not loaded in ModList, skipping compatibility adapter");
            isInitialized = true;
            return;
        }

        try {
            // 获取 irons_spellbooks:spell_container 组件类型（键名包含命名空间冒号）
            ResourceLocation componentLoc = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_container");
            spellContainerType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentLoc);

            if (spellContainerType == null) {
                RarityCore.LOGGER.warn("Iron's Spells spell_container component type not found in registry at {}",
                        componentLoc);
                isIronSpellsLoaded = false;
            } else {
                RarityCore.LOGGER.info("Iron's Spells compatibility adapter initialized, component type: {}",
                        componentLoc);
            }

            isInitialized = true;
        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Iron's Spells compatibility adapter", e);
            isInitialized = true;
        }
    }

    /**
     * 检查 Iron's Spells 模组是否已加载
     *
     * @return 模组是否已加载
     */
    public static boolean isLoaded() {
        if (!isInitialized) {
            init();
        }
        return isIronSpellsLoaded;
    }

    /**
     * 获取 Iron's Spells 法术等级映射的稀有度
     * <p>
     * 通过 {@code irons_spellbooks:spell_container} 组件读取物品的法术数据，
     * 反射调用 {@code data()} 获取法术列表，取第一个法术的 {@code level()}，
     * 按映射规则转换为稀有度等级。
     * </p>
     * <p>
     * 映射规则：level 1→1, 2→2, ..., 7→7, 8+→7
     * </p>
     *
     * @param itemStack 物品栈
     * @return 映射的稀有度等级（1-7），如果物品没有法术容器组件则返回 null
     */
    public static Integer getMappedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        if (!isInitialized) {
            init();
        }

        if (!isIronSpellsLoaded || spellContainerType == null) {
            return null;
        }

        try {
            // 通过注册的 ComponentType 获取 irons_spellbooks:spell_container 组件
            Object spellContainer = itemStack.get(spellContainerType);
            if (spellContainer == null) {
                return null;
            }

            // 反射调用 data() 方法获取法术列表
            List<?> spells = (List<?>) spellContainer.getClass().getMethod("data").invoke(spellContainer);
            if (spells == null || spells.isEmpty()) {
                return null;
            }

            // 取第一个法术元素，反射调用 level() 获取法术等级
            Object firstSpell = spells.get(0);
            int level = (int) firstSpell.getClass().getMethod("level").invoke(firstSpell);

            // 映射：level 1→1, 2→2, ..., 7→7, 8+→7
            return Math.min(level, 7);

        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get mapped Iron's Spells rarity for item: {}", itemStack.getItem(), e);
            return null;
        }
    }

    /**
     * 重置适配器状态（用于热重载场景）
     */
    public static void reset() {
        isInitialized = false;
        isIronSpellsLoaded = false;
        spellContainerType = null;
    }
}
