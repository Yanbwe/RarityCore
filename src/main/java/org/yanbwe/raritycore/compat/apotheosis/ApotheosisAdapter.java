package org.yanbwe.raritycore.compat.apotheosis;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.yanbwe.raritycore.RarityCore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class ApotheosisAdapter {

    private static boolean isInitialized = false;
    private static DataComponentType<ResourceLocation> rarityComponent;
    private static DataComponentType<String> purityComponent;

    public static void init() {
        if (isInitialized) {
            return;
        }

        if (!ModList.get().isLoaded("apotheosis")) {
            RarityCore.LOGGER.debug("Apotheosis mod not detected, skipping initialization");
            return;
        }

        try {
            rarityComponent = getApotheosisRarityComponent();
            purityComponent = getApotheosisPurityComponent();

            isInitialized = true;
            RarityCore.LOGGER.info("Apotheosis compatibility adapter initialized with Component API");

        } catch (Exception e) {
            RarityCore.LOGGER.error("Failed to initialize Apotheosis compatibility adapter", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<ResourceLocation> getApotheosisRarityComponent() {
        try {
            ResourceLocation rarityLoc = ResourceLocation.fromNamespaceAndPath("apotheosis", "rarity");
            Field field = BuiltInRegistries.class.getDeclaredField("DATA_COMPONENT_TYPE");
            field.setAccessible(true);
            Object registry = field.get(null);

            Method byLocationMethod = registry.getClass().getMethod("get", ResourceLocation.class);
            Object holder = byLocationMethod.invoke(registry, rarityLoc);

            if (holder != null) {
                Method getValueMethod = holder.getClass().getMethod("value");
                Object value = getValueMethod.invoke(holder);
                if (value instanceof DataComponentType) {
                    return (DataComponentType<ResourceLocation>) value;
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Could not get apotheosis rarity component: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<String> getApotheosisPurityComponent() {
        try {
            ResourceLocation purityLoc = ResourceLocation.fromNamespaceAndPath("apotheosis", "purity");
            Field field = BuiltInRegistries.class.getDeclaredField("DATA_COMPONENT_TYPE");
            field.setAccessible(true);
            Object registry = field.get(null);

            Method byLocationMethod = registry.getClass().getMethod("get", ResourceLocation.class);
            Object holder = byLocationMethod.invoke(registry, purityLoc);

            if (holder != null) {
                Method getValueMethod = holder.getClass().getMethod("value");
                Object value = getValueMethod.invoke(holder);
                if (value instanceof DataComponentType) {
                    return (DataComponentType<String>) value;
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Could not get apotheosis purity component: {}", e.getMessage());
        }
        return null;
    }

    public static boolean hasApotheosisRarity(ItemStack itemStack) {
        if (!isInitialized || itemStack.isEmpty()) {
            return false;
        }

        try {
            if (rarityComponent != null && hasComponent(itemStack, rarityComponent)) {
                return true;
            }

            if (purityComponent != null && hasComponent(itemStack, purityComponent)) {
                return true;
            }

            return false;

        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to check Apotheosis rarity for item: {}", itemStack.getItem(), e);
            return false;
        }
    }

    private static <T> boolean hasComponent(ItemStack itemStack, DataComponentType<T> componentType) {
        try {
            Map<?, ?> components = getComponentsMap(itemStack);
            if (components != null) {
                return components.containsKey(componentType);
            }
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to check component presence: {}", e.getMessage());
        }
        return false;
    }

    private static <T> T getComponent(ItemStack itemStack, DataComponentType<T> componentType, T defaultValue) {
        try {
            Map<?, ?> components = getComponentsMap(itemStack);
            if (components != null && components.containsKey(componentType)) {
                @SuppressWarnings("unchecked")
                T value = (T) components.get(componentType);
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get component: {}", e.getMessage());
        }
        return defaultValue;
    }

    private static Map<?, ?> getComponentsMap(ItemStack itemStack) {
        try {
            Method getComponentsMethod = itemStack.getClass().getMethod("getComponents");
            return (Map<?, ?>) getComponentsMethod.invoke(itemStack);
        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get components map: {}", e.getMessage());
            return null;
        }
    }

    public static Integer getMappedRarity(ItemStack itemStack) {
        if (!isInitialized || itemStack.isEmpty()) {
            return null;
        }

        try {
            if (rarityComponent != null) {
                ResourceLocation rarityLoc = getComponent(itemStack, rarityComponent, null);
                if (rarityLoc != null) {
                    Integer mappedRarity = mapApotheosisRarityString(rarityLoc.toString());
                    if (mappedRarity != null) {
                        return mappedRarity;
                    }
                }
            }

            if (purityComponent != null) {
                String purity = getComponent(itemStack, purityComponent, null);
                if (purity != null) {
                    Integer mappedRarity = mapApotheosisPurityString(purity);
                    if (mappedRarity != null) {
                        return mappedRarity;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            RarityCore.LOGGER.debug("Failed to get mapped Apotheosis rarity for item: {}", itemStack.getItem(), e);
            return null;
        }
    }

    private static Integer mapApotheosisRarityString(String rarityString) {
        if (rarityString == null || rarityString.isEmpty()) {
            return null;
        }

        String rarityName = rarityString;
        if (rarityName.contains(":")) {
            rarityName = rarityName.substring(rarityName.indexOf(":") + 1);
        }

        switch (rarityName.toLowerCase()) {
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
                return null;
        }
    }

    public static void reset() {
        isInitialized = false;
        rarityComponent = null;
        purityComponent = null;
    }
}