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

                if (keyLoc.equals(rarityComponentLoc)) {
                    Object value = component.value();
                    String rarityName = getRarityNameFromHolder(value);
                    if (rarityName != null) {
                        Integer mapped = mapApotheosisRarityString(rarityName);
                        if (mapped != null) {
                            return mapped;
                        }
                    }
                } else if (keyLoc.equals(purityComponentLoc)) {
                    Object value = component.value();
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
    }
}