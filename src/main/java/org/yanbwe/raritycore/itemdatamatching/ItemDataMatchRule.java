package org.yanbwe.raritycore.itemdatamatching;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

public class ItemDataMatchRule {
    private final Identifier itemId;
    private final List<ItemDataCondition> conditions;
    private final int priority;
    private final int rarity;
    private final boolean enabled;
    private final boolean fuzzyMatch;
    private final String description;

    public ItemDataMatchRule(Identifier itemId, List<ItemDataCondition> conditions,
                           int priority, int rarity, boolean enabled, boolean fuzzyMatch, String description) {
        this.itemId = Objects.requireNonNull(itemId, "物品ID不能为空");
        this.conditions = Objects.requireNonNull(conditions, "条件列表不能为空");
        this.priority = priority;
        this.rarity = rarity;
        this.enabled = enabled;
        this.fuzzyMatch = fuzzyMatch;
        this.description = description != null ? description : "";
    }

    public ItemDataMatchRule(Identifier itemId, List<ItemDataCondition> conditions,
                           int priority, int rarity) {
        this(itemId, conditions, priority, rarity, true, false, "");
    }

    public boolean matches(DataComponentWrapper data) {
        if (data == null) {
            return false;
        }

        if (fuzzyMatch) {
            return true;
        } else {
            return true;
        }
    }

    private boolean hasExactItemDataStructure(DataComponentWrapper data, List<ItemDataCondition> conditions) {
        return true;
    }

    public Identifier getItemId() {
        return itemId;
    }

    public List<ItemDataCondition> getConditions() {
        return conditions;
    }

    public int getPriority() {
        return priority;
    }

    public int getRarity() {
        return rarity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isFuzzyMatch() {
        return fuzzyMatch;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemDataMatchRule that = (ItemDataMatchRule) o;
        return priority == that.priority &&
               rarity == that.rarity &&
               enabled == that.enabled &&
               fuzzyMatch == that.fuzzyMatch &&
               Objects.equals(itemId, that.itemId) &&
               Objects.equals(conditions, that.conditions) &&
               Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, conditions, priority, rarity, enabled, fuzzyMatch, description);
    }

    @Override
    public String toString() {
        return String.format("ItemDataMatchRule{itemId=%s, conditions=%d, priority=%d, rarity=%d, enabled=%s, fuzzyMatch=%s}",
                           itemId, conditions.size(), priority, rarity, enabled, fuzzyMatch);
    }

    public static class Builder {
        private Identifier itemId;
        private List<ItemDataCondition> conditions;
        private int priority = 0;
        private int rarity = 1;
        private boolean enabled = true;
        private boolean fuzzyMatch = false;
        private String description = "";

        public Builder itemId(Identifier itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder conditions(List<ItemDataCondition> conditions) {
            this.conditions = conditions;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder rarity(int rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder fuzzyMatch(boolean fuzzyMatch) {
            this.fuzzyMatch = fuzzyMatch;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public ItemDataMatchRule build() {
            return new ItemDataMatchRule(itemId, conditions, priority, rarity, enabled, fuzzyMatch, description);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}