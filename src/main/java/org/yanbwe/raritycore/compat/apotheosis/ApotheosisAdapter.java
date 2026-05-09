package org.yanbwe.raritycore.compat.apotheosis;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApotheosisAdapter {

    private static boolean isApotheosisLoaded = false;
    private static boolean isInitialized = false;
    private static ResourceLocation rarityComponentLoc;
    private static ResourceLocation purityComponentLoc;

    /** 缓存的 DataComponentType 引用，避免热路径上的注册表查找 */
    private static DataComponentType<?> cachedRarityType;
    private static DataComponentType<?> cachedPurityType;

    private static final Pattern DYNAMIC_HOLDER_PATTERN = Pattern.compile("DynamicHolder\\{[^/]*/ ([^}]+)\\}");

    public static synchronized void init() {
        if (isInitialized) {
            return;
        }

        isApotheosisLoaded = ModList.get().isLoaded("apotheosis");

        if (!isApotheosisLoaded) {
            RarityCore.LOGGER.debug("Apotheosis mod not detected, skipping compatibility adapter");
            isInitialized = true;
            return;
        }

        try {
            rarityComponentLoc = ResourceLocation.fromNamespaceAndPath("apotheosis", "rarity");
            purityComponentLoc = ResourceLocation.fromNamespaceAndPath("apotheosis", "purity");

            // 缓存 DataComponentType 引用，避免热路径上的注册表反向查找 (O(1) 替代 O(n))
            cachedRarityType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(rarityComponentLoc);
            cachedPurityType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(purityComponentLoc);

            isInitialized = true;
            RarityCore.LOGGER.info("Apotheosis compatibility adapter initialized, rarity={}, purity={}",
                rarityComponentLoc, purityComponentLoc);

        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Apotheosis compatibility adapter", e);
        }
    }

    public static boolean hasApotheosisRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        if (!isInitialized) {
            init();
        }

        if (!isApotheosisLoaded) {
            return false;
        }

        try {
            // 使用缓存的 DataComponentType 引用进行 O(1) 查找，替代原 O(n) 遍历
            DataComponentMap components = itemStack.getComponents();
            
            // 类型标识比较（比 registry 反向查找快得多）
            for (var tc : components) {
                DataComponentType<?> type = tc.type();
                if (type == cachedRarityType || type == cachedPurityType) {
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to check Apotheosis rarity for item: {}", itemStack.getItem(), e);
            return false;
        }
    }

    public static Integer getMappedRarity(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        if (!isInitialized) {
            init();
        }

        if (!isApotheosisLoaded) {
            return null;
        }

        try {
            // 使用缓存的 DataComponentType 引用遍历组件 (标识比较替代注册表反向查找)
            DataComponentMap components = itemStack.getComponents();

            for (var tc : components) {
                DataComponentType<?> type = tc.type();

                if (type == cachedRarityType) {
                    Object value = tc.value();
                    String rarityName = getRarityNameFromHolder(value);
                    if (rarityName != null) {
                        Integer mapped = mapApotheosisRarityString(rarityName);
                        if (mapped != null) {
                            return mapped;
                        }
                    }
                } else if (type == cachedPurityType) {
                    Object value = tc.value();
                    String purityName = getPurityName(value);
                    if (purityName != null) {
                        Integer mapped = mapApotheosisPurityString(purityName);
                        if (mapped != null) {
                            return mapped;
                        }
                    }
                }
            }

            return null;

        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get mapped Apotheosis rarity for item: {}", itemStack.getItem(), e);
            return null;
        }
    }

    private static String getRarityNameFromHolder(Object holder) {
        if (holder == null) {
            return null;
        }

        String str = holder.toString();
        Matcher matcher = DYNAMIC_HOLDER_PATTERN.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }

        if (holder instanceof ResourceLocation rl) {
            return rl.getPath();
        }

        return null;
    }

    private static String getPurityName(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Enum<?> enumValue) {
            return enumValue.name().toLowerCase();
        }

        if (value instanceof String str) {
            return str;
        }

        String str = value.toString().toLowerCase();
        for (String purity : new String[]{"cracked", "chipped", "flawed", "normal", "flawless", "perfect"}) {
            if (str.contains(purity)) {
                return purity;
            }
        }

        return null;
    }

    private static Integer mapApotheosisRarityString(String rarityString) {
        if (rarityString == null || rarityString.isEmpty()) {
            return null;
        }

        String rarityName = rarityString.toLowerCase();
        if (rarityName.contains(":")) {
            rarityName = rarityName.substring(rarityName.indexOf(":") + 1);
        }

        switch (rarityName) {
            case "common":
                return 1;
            case "uncommon":
                return 2;
            case "rare":
                return 3;
            case "epic":
                return 4;
            case "mythic":
                return 5;
            case "ancient":
                return 6;
            case "artifact":
                return 7;
            case "heirloom":
                return 8;
            case "esoteric":
                return 9;
            default:
                RarityCore.LOGGER.debug("Unknown apotheosis rarity: {}", rarityString);
                return null;
        }
    }

    private static Integer mapApotheosisPurityString(String purityString) {
        if (purityString == null || purityString.isEmpty()) {
            return null;
        }

        switch (purityString.toLowerCase()) {
            case "cracked":
                return 1;
            case "chipped":
                return 2;
            case "flawed":
                return 3;
            case "normal":
                return 4;
            case "flawless":
                return 5;
            case "perfect":
                return 6;
            default:
                RarityCore.LOGGER.debug("Unknown apotheosis purity: {}", purityString);
                return null;
        }
    }

    public static void reset() {
        isInitialized = false;
        isApotheosisLoaded = false;
        rarityComponentLoc = null;
        purityComponentLoc = null;
        cachedRarityType = null;
        cachedPurityType = null;
    }

    /**
     * 检查神化模组是否已加载
     * @return 神化模组是否已加载
     */
    public static boolean isLoaded() {
        if (!isInitialized) {
            init();
        }
        return isApotheosisLoaded;
    }
}