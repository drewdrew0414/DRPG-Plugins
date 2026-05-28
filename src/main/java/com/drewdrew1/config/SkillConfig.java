package com.drewdrew1.config;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;

public final class SkillConfig {
    private String name;
    private String displayName;
    private int startLevel;
    private int maxLevel;
    private double multipleExp;
    private List<Map<String, List<ExpRule>>> getExp;
    private List<RewardConfig> getItem;
    private List<PassiveConfig> passive;
    private List<TitleConfig> title;

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public int startLevel() {
        return startLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public double multipleExp() {
        return multipleExp;
    }

    public List<Map<String, List<ExpRule>>> getExp() {
        return getExp == null ? List.of() : getExp;
    }

    public List<RewardConfig> getItem() {
        return getItem == null ? List.of() : getItem;
    }

    public List<PassiveConfig> passive() {
        return passive == null ? List.of() : passive;
    }

    public List<TitleConfig> title() {
        return title == null ? List.of() : title;
    }

    public static final class ExpRule {
        private List<String> block;
        private List<String> useTool;
        private List<ValueRange> getExp;
        private List<ValueRange> bonusExp;
        private int requiredLevel;
        private List<String> requiredEnchant;
        private List<String> world;
        private List<String> biome;
        private List<ComboConfig> combo;
        private boolean placedByPlayer;
        private List<String> entity;
        private String type;
        private List<String> item;
        private String potionType;
        private List<String> potion;
        private String targetItem;

        public List<String> block() {
            return block == null ? List.of() : block;
        }

        public List<String> useTool() {
            return useTool == null ? List.of() : useTool;
        }

        public List<ValueRange> getExp() {
            return getExp == null ? List.of() : getExp;
        }

        public List<ValueRange> bonusExp() {
            return bonusExp == null ? List.of() : bonusExp;
        }

        public int requiredLevel() {
            return requiredLevel;
        }

        public List<String> requiredEnchant() {
            return requiredEnchant == null ? List.of() : requiredEnchant;
        }

        public List<String> world() {
            return world == null ? List.of() : world;
        }

        public List<String> biome() {
            return biome == null ? List.of() : biome;
        }

        public List<ComboConfig> combo() {
            return combo == null ? List.of() : combo;
        }

        public boolean placedByPlayer() {
            return placedByPlayer;
        }

        public List<String> entity() {
            return entity == null ? List.of() : entity;
        }

        public String type() {
            return type;
        }

        public List<String> item() {
            return item == null ? List.of() : item;
        }

        public String potionType() {
            return potionType;
        }

        public List<String> potion() {
            return potion == null ? List.of() : potion;
        }

        public String targetItem() {
            return targetItem;
        }
    }

    public static final class ValueRange {
        private double min;
        private double max;
        private double chance;

        public double min() {
            return min;
        }

        public double max() {
            return max;
        }

        public double chance() {
            return chance;
        }
    }

    public static final class ComboConfig {
        private int count;
        private double multiply;

        public int count() {
            return count;
        }

        public double multiply() {
            return multiply;
        }
    }

    public static final class RewardConfig {
        private int level;
        private boolean isRandom;
        private List<RandomItemConfig> randomItem;
        private List<ItemStackConfig> item;
        private int givePlayerExp;

        public int level() {
            return level;
        }

        public boolean isRandom() {
            return isRandom;
        }

        public List<RandomItemConfig> randomItem() {
            return randomItem == null ? List.of() : randomItem;
        }

        public List<ItemStackConfig> item() {
            return item == null ? List.of() : item;
        }

        public int givePlayerExp() {
            return givePlayerExp;
        }
    }

    public static final class RandomItemConfig {
        private int id;
        private JsonElement item;

        public int id() {
            return id;
        }

        public JsonElement item() {
            return item;
        }
    }

    public static final class ItemStackConfig {
        private String item;
        private int amount;
        private String name;
        private List<String> nbt;
        private List<String> lore;
        private List<EnchantConfig> enchants;

        public String item() {
            return item;
        }

        public int amount() {
            return amount;
        }

        public String name() {
            return name;
        }

        public List<String> nbt() {
            return nbt == null ? List.of() : nbt;
        }

        public List<String> lore() {
            return lore == null ? List.of() : lore;
        }

        public List<EnchantConfig> enchants() {
            return enchants == null ? List.of() : enchants;
        }
    }

    public static final class EnchantConfig {
        private String enchant;
        private int level;

        public String enchant() {
            return enchant;
        }

        public int level() {
            return level;
        }
    }

    public static final class PassiveConfig {
        private int level;
        private List<EffectConfig> effect;
        private int usageTime;
        private int coolTime;

        public int level() {
            return level;
        }

        public List<EffectConfig> effect() {
            return effect == null ? List.of() : effect;
        }

        public int usageTime() {
            return usageTime;
        }

        public int coolTime() {
            return coolTime;
        }
    }

    public static final class EffectConfig {
        private String id;
        private int level;

        public String id() {
            return id;
        }

        public int level() {
            return level;
        }
    }

    public static final class TitleConfig {
        private int level;
        private String title;

        public int level() {
            return level;
        }

        public String title() {
            return title;
        }
    }
}
