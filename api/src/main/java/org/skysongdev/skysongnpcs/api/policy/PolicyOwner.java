package org.skysongdev.skysongnpcs.api.policy;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.access.GrantSource;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Who an {@link AccessPolicy} belongs to, and so who may change it: a character (made with
 * {@code /snpc policy create}) or a plugin (made through the API, such as one policy per
 * SkysongEconomy business). Staff may change any policy.
 */
public sealed interface PolicyOwner permits PolicyOwner.CharacterOwner, PolicyOwner.PluginOwner {

    /**
     * A character's policy.
     *
     * @param player  the player's UUID
     * @param profile the profile's name, as SkysongStats shows it
     * @return the owner
     */
    static @NotNull PolicyOwner character(@NotNull UUID player, @NotNull String profile) {
        return new CharacterOwner(player, profile);
    }

    /**
     * A plugin's policy.
     *
     * @param plugin the plugin
     * @return the owner, named after the plugin
     */
    static @NotNull PolicyOwner plugin(@NotNull Plugin plugin) {
        return new PluginOwner(plugin.getName());
    }

    /**
     * The source the policy's grants show: {@link GrantSource#owner()} for a character's
     * policy, {@link GrantSource#plugin(String)} for a plugin's.
     *
     * @return the source
     */
    @NotNull GrantSource source();

    /**
     * A character's policy.
     *
     * @param player  the player's UUID
     * @param profile the profile's name
     */
    record CharacterOwner(@NotNull UUID player, @NotNull String profile) implements PolicyOwner {
        /**
         * @throws IllegalArgumentException when the profile is blank
         */
        public CharacterOwner {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(profile, "profile");
            if (profile.isBlank()) {
                throw new IllegalArgumentException("profile is blank");
            }
        }

        @Override
        public @NotNull GrantSource source() {
            return GrantSource.owner();
        }
    }

    /**
     * A plugin's policy.
     *
     * @param plugin the plugin's name, in lower case
     */
    record PluginOwner(@NotNull String plugin) implements PolicyOwner {
        /**
         * @throws IllegalArgumentException when the name is not a plugin name
         */
        public PluginOwner {
            Objects.requireNonNull(plugin, "plugin");
            plugin = plugin.toLowerCase(Locale.ROOT);
            // The same names GrantSource accepts, so source() never fails.
            GrantSource.plugin(plugin);
        }

        @Override
        public @NotNull GrantSource source() {
            return GrantSource.plugin(plugin);
        }
    }
}
