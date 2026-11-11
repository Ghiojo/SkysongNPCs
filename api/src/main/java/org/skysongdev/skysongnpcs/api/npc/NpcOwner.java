package org.skysongdev.skysongnpcs.api.npc;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Who owns a managed NPC: one character (a player and one of their SkysongStats profiles),
 * or the server. The owner may do everything with their NPC. A server-owned NPC is for staff
 * only, unless access grants let others in.
 *
 * <p>Ownership belongs to the character, not the player: a player who switches to another
 * profile is not the owner while they play it.
 */
public sealed interface NpcOwner permits NpcOwner.CharacterOwner, NpcOwner.ServerOwner {

    /**
     * A character as owner.
     *
     * @param player  the player's UUID
     * @param profile the profile's name, matched exactly, with its case, as SkysongStats shows it
     * @return the owner
     */
    static @NotNull NpcOwner character(@NotNull UUID player, @NotNull String profile) {
        return new CharacterOwner(player, profile);
    }

    /**
     * The server as owner.
     *
     * @return the owner
     */
    static @NotNull NpcOwner server() {
        return ServerOwner.INSTANCE;
    }

    /**
     * Owned by one character.
     *
     * @param player  the player's UUID
     * @param profile the profile's name, as SkysongStats shows it
     */
    record CharacterOwner(@NotNull UUID player, @NotNull String profile) implements NpcOwner {
        /**
         * @throws NullPointerException     when the player or the profile is null
         * @throws IllegalArgumentException when the profile is blank
         */
        public CharacterOwner {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(profile, "profile");
            if (profile.isBlank()) {
                throw new IllegalArgumentException("profile is blank");
            }
        }
    }

    /** Owned by the server. Get it with {@link NpcOwner#server()}. */
    final class ServerOwner implements NpcOwner {
        private static final ServerOwner INSTANCE = new ServerOwner();

        private ServerOwner() {
        }

        @Override
        public String toString() {
            return "ServerOwner";
        }
    }
}
