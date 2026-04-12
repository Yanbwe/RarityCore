package org.yanbwe.raritycore.compat.apotheosis;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
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

    private static final Pattern DYNAMIC_HOLDER_PATTERN = Pattern.compile("DynamicHolder\\{[^/]*/ ([^}]+)\\}");

    public static void init() {
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

            isInitialized = true;
            RarityCore.LOGGER.info("Apotheosis compatibility adapter initialized, rarity={}, purity={}",
                rarityComponentLoc, purityComponentLoc);

        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Apotheosis compatibility adapter", e);
        }
    }

    public static void debugCheckItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            RarityCore.LOGGER.debug("[DEBUG] ItemStack is null or empty");
            return;
        }
        
        RarityCore.LOGGER.debug("[DEBUG] Checking item: {} with {} components", 
            itemStack.getItem(), itemStack.getComponents().size());
        
        DataComponentMap components = itemStack.getComponents();
        for (TypedDataComponent<?> component : components) {
            ResourceLocation keyLoc = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
            RarityCore.LOGGER.debug("[DEBUG] Component found: {} = {}", keyLoc, component.value());
        }
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
            DataComponentMap components = itemStack.getComponents();

            for (TypedDataComponent<?> component : components) {
                ResourceLocation keyLoc = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
                if (keyLoc.equals(rarityComponentLoc) || keyLoc.equals(purityComponentLoc)) {
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
            DataComponentMap components = itemStack.getComponents();

            for (TypedDataComponent<?> component : components) {
                ResourceLocation keyLoc = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
                String keyStr = keyLoc.toString();
                Object value = component.value();

                if (keyStr.contains("rarity") && keyStr.contains("apotheosis")) {
                    String rarityName = getRarityNameFromHolder(value);
                    if (rarityName != null) {
                        Integer mapped = mapApotheosisRarityString(rarityName);
                        if (mapped != null) {
                            return mapped;
                        }
                    }
                } else if (keyStr.contains("purity") && keyStr.contains("apotheosis")) {
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

        ResourceLocation id = getDynamicHolderId(holder);
        if (id != null) {
            return id.getPath();
        }

        String rarityName = getRarityNameFromLootRarity(holder);
        if (rarityName != null) {
            return rarityName;
        }

        if (holder instanceof ResourceLocation rl) {
            return rl.getPath();
        }

        String str = holder.toString();
        Matcher matcher = DYNAMIC_HOLDER_PATTERN.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }

        if (str.startsWith("LootRarity{")) {
            int start = str.indexOf('{') + 1;
            int end = str.lastIndexOf('}');
            if (end > start) {
                return str.substring(start, end);
            }
        }

        RarityCore.LOGGER.debug("Unable to parse rarity from holder: {} (class: {})", str, holder.getClass().getName());
        return null;
    }

    private static ResourceLocation getDynamicHolderId(Object holder) {
        try {
            Class<?> dynamicHolderClass = Class.forName("dev.shadowsoffire.placebo.reload.DynamicHolder");
            if (dynamicHolderClass.isInstance(holder)) {
                java.lang.reflect.Method getIdMethod = dynamicHolderClass.getMethod("getId");
                Object idObj = getIdMethod.invoke(holder);
                if (idObj instanceof ResourceLocation rl) {
                    return rl;
                }
            }
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("DynamicHolder class not found, using fallback parsing");
        } catch (NoSuchMethodException e) {
            RarityCore.LOGGER.debug("DynamicHolder.getId() method not found");
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get DynamicHolder ID via reflection: {}", e.getMessage());
        }
        return null;
    }

    private static String getRarityNameFromLootRarity(Object holder) {
        try {
            Class<?> lootRarityClass = Class.forName("dev.shadowsoffire.apotheosis.loot.LootRarity");
            if (lootRarityClass.isInstance(holder)) {
                Object idObj = invokeMethod(holder, "getId", false);
                if (idObj instanceof ResourceLocation rl) {
                    return rl.getPath();
                }
                Object keyObj = invokeMethod(holder, "getKey", false);
                if (keyObj instanceof ResourceLocation keyRl) {
                    return keyRl.getPath();
                }
            }
        } catch (ClassNotFoundException e) {
            RarityCore.LOGGER.debug("LootRarity class not found");
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get LootRarity name via reflection: {}", e.getMessage());
        }
        return null;
    }

    private static Object invokeMethod(Object target, String methodName, boolean requireNull) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Method method = clazz.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    Object result = method.invoke(target);
                    if (!requireNull && result != null) {
                        return result;
                    } else if (result == null && requireNull) {
                        return result;
                    }
                } catch (NoSuchMethodException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to invoke method {}: {}", methodName, e.getMessage());
        }
        return null;
    }

    private static String getPurityName(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Enum<?> enumValue) {
            String name = enumValue.name().toLowerCase();
            RarityCore.LOGGER.debug("Purity enum value: {} -> {}", enumValue.name(), name);
            return name;
        }

        if (value instanceof String str) {
            RarityCore.LOGGER.debug("Purity string value: {}", str);
            return str;
        }

        String str = value.toString().toLowerCase();
        RarityCore.LOGGER.debug("Purity toString value: {}, class: {}", str, value.getClass().getName());
        for (String purity : new String[]{"cracked", "chipped", "flawed", "normal", "flawless", "perfect"}) {
            if (str.contains(purity)) {
                RarityCore.LOGGER.debug("Found purity keyword: {}", purity);
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
            RarityCore.LOGGER.debug("mapApotheosisPurityString called with null/empty string");
            return null;
        }

        String lower = purityString.toLowerCase();
        RarityCore.LOGGER.debug("mapApotheosisPurityString mapping: {} -> {}", purityString, lower);

        switch (lower) {
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
    }

    private static String getGemIdFromHolder(Object holder) {
        if (holder == null) {
            return null;
        }

        ResourceLocation id = getDynamicHolderId(holder);
        if (id != null) {
            return id.toString();
        }

        String str = holder.toString();
        Matcher matcher = DYNAMIC_HOLDER_PATTERN.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private static Integer mapGemRarity(String gemId) {
        if (gemId == null || gemId.isEmpty()) {
            return null;
        }

        RarityCore.LOGGER.debug("Mapping gem ID to rarity: {}", gemId);
        return 1;
    }
}