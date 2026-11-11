package org.skysongdev.skysongnpcs.api.access;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/**
 * Who made a grant, and so who may change it. The owner side (the owner, and anyone the NPC
 * lets {@link NpcAction#MANAGE}) only changes owner-side grants, and each plugin only its own,
 * so a business owner and SkysongEconomy never overwrite each other. Staff change any.
 */
public sealed interface GrantSource permits GrantSource.OwnerSide, GrantSource.PluginSide {

    /**
     * The owner side: grants made with {@code /snpc access}.
     *
     * @return the source
     */
    static @NotNull GrantSource owner() {
        return OwnerSide.INSTANCE;
    }

    /**
     * A plugin's own grants.
     *
     * @param plugin the plugin
     * @return the source, named after the plugin
     */
    static @NotNull GrantSource plugin(@NotNull Plugin plugin) {
        return plugin(plugin.getName());
    }

    /**
     * A plugin's own grants, by the plugin's name.
     *
     * @param name the plugin's name, matched ignoring case
     * @return the source
     */
    static @NotNull GrantSource plugin(@NotNull String name) {
        return new PluginSide(name);
    }

    /**
     * The source's id as storage and commands write it: {@code owner}, or the plugin's name in
     * lower case.
     *
     * @return the id
     */
    @NotNull String key();

    /** Grants made by the owner side. Get it with {@link GrantSource#owner()}. */
    final class OwnerSide implements GrantSource {
        private static final OwnerSide INSTANCE = new OwnerSide();

        private OwnerSide() {
        }

        @Override
        public @NotNull String key() {
            return "owner";
        }

        @Override
        public String toString() {
            return "OwnerSide";
        }
    }

    /**
     * Grants made by one plugin.
     *
     * @param plugin the plugin's name, in lower case
     */
    record PluginSide(@NotNull String plugin) implements GrantSource {
        /**
         * @throws IllegalArgumentException when the name is blank or is {@code owner}
         */
        public PluginSide {
            Objects.requireNonNull(plugin, "plugin");
            plugin = plugin.toLowerCase(Locale.ROOT);
            if (!plugin.matches("[a-z0-9_.-]+") || plugin.equals("owner")) {
                throw new IllegalArgumentException("Not a plugin name: " + plugin);
            }
        }

        @Override
        public @NotNull String key() {
            return plugin;
        }
    }
}
