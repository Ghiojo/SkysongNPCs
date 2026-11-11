package org.skysongdev.skysongnpcs.menu;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Optional;

/**
 * The {@code npc-menu:} section of menus.yml: the menu's size, whether right-click opens it,
 * and per button id an optional slot, material and {@code hidden: true}.
 */
final class MenuSettings {
    private static final String SECTION = "npc-menu";

    private final ConfigurationSection menu;

    MenuSettings(ConfigurationSection menusFile) {
        ConfigurationSection section = menusFile.getConfigurationSection(SECTION);
        this.menu = section == null ? new MemoryConfiguration() : section;
    }

    /** Rows of the menu, kept between 1 and 6. */
    int rows() {
        return Math.max(1, Math.min(6, menu.getInt("rows", 3)));
    }

    boolean openOnRightClick() {
        return menu.getBoolean("open-on-right-click", true);
    }

    int slot(String button, int fallback) {
        return menu.getInt(path(button, "slot"), fallback);
    }

    boolean hidden(String button) {
        return menu.getBoolean(path(button, "hidden"), false);
    }

    /** The material menus.yml draws this button with instead of its own; empty when unset or not a known item. */
    Optional<Material> material(String button) {
        String name = menu.getString(path(button, "material"));
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null && material.isItem() ? Optional.of(material) : Optional.empty();
    }

    // Ids hold no dots (see NpcMenuService), so they are safe as one path element.
    private static String path(String button, String key) {
        return "buttons." + button + "." + key;
    }
}
