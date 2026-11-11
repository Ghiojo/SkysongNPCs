package org.skysongdev.skysongnpcs.api.groups;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Someone in a {@link PlayerGroup}: one character, or every character of a player.
 *
 * @param player  the player's UUID
 * @param profile the profile's name, as SkysongStats shows it; null for every character of the player
 */
public record GroupMember(@NotNull UUID player, @Nullable String profile) {
    /**
     * @throws IllegalArgumentException when the profile is blank
     */
    public GroupMember {
        Objects.requireNonNull(player, "player");
        if (profile != null && profile.isBlank()) {
            throw new IllegalArgumentException("profile is blank");
        }
    }

    /**
     * Every character of a player.
     *
     * @param player the player's UUID
     * @return the member
     */
    public static @NotNull GroupMember everyCharacter(@NotNull UUID player) {
        return new GroupMember(player, null);
    }

    /**
     * One character.
     *
     * @param player  the player's UUID
     * @param profile the profile's name, as SkysongStats shows it
     * @return the member
     */
    public static @NotNull GroupMember character(@NotNull UUID player, @NotNull String profile) {
        return new GroupMember(player, Objects.requireNonNull(profile, "profile"));
    }

    /**
     * Whether a player, playing a profile, is this member.
     *
     * @param player        the player's UUID
     * @param activeProfile the profile they play now, null when they play none
     * @return true when it is them
     */
    public boolean includes(@NotNull UUID player, @Nullable String activeProfile) {
        return this.player.equals(player) && (profile == null || profile.equals(activeProfile));
    }
}
