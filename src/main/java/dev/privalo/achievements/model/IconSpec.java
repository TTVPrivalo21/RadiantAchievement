package dev.privalo.achievements.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

/**
 * An item icon with optional custom texture (CustomModelData, item_model component or Oraxen item id).
 */
public record IconSpec(String material, int customModelData, String itemModel, String oraxenItem) {

    public static final IconSpec DEFAULT = new IconSpec("GOLD_INGOT", 0, "", "");

    public IconSpec {
        material = material == null || material.isBlank() ? "GOLD_INGOT" : material.trim();
        itemModel = itemModel == null ? "" : itemModel.trim();
        oraxenItem = oraxenItem == null ? "" : oraxenItem.trim();
    }

    public static IconSpec fromSection(ConfigurationSection s, IconSpec def) {
        if (s == null) {
            return def;
        }
        return new IconSpec(s.getString("material", def.material()), s.getInt("custom-model-data", 0),
                s.getString("item-model", ""), s.getString("oraxen", ""));
    }

    public static IconSpec fromMap(Map<?, ?> map) {
        Object mat = map.get("material");
        Object model = map.get("item-model");
        Object oraxen = map.get("oraxen");
        return new IconSpec(mat == null ? "STONE" : mat.toString(), (int) SoundSpec.num(map.get("custom-model-data"), 0),
                model == null ? "" : model.toString(), oraxen == null ? "" : oraxen.toString());
    }

    public boolean hasItemModel() {
        return !itemModel.isEmpty();
    }
}
