package org.skysongdev.skysongnpcs.api.access;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A group of players a grant names, written {@code <type>:<id>}. Membership is checked live
 * whenever a player tries an action, against the character they are playing at that moment.
 *
 * <ul>
 *   <li>{@code player:<uuid>} every character of one player, {@code player:<uuid>/<profile>}
 *       one character. Commands let players type a name; it is stored as the UUID.</li>
 *   <li>{@code perm:<node>} anyone with a permission node.</li>
 *   <li>{@code <type>:<id>} a group another plugin provides through a {@link GroupProvider}
 *       registered under {@code <type>}, such as {@code economy:smithy/workers}.</li>
 * </ul>
 *
 * @param type the kind of group, lower case: {@code player}, {@code perm} or a provider's type
 * @param id   which group of that kind; may hold any character but a line break
 */
public record AccessGroup(@NotNull String type, @NotNull String id) {
    /** The type of {@link #player(UUID)} groups. */
    public static final String PLAYER = "player";
    /** The type of {@link #permission(String)} groups. */
    public static final String PERMISSION = "perm";

    /**
     * @throws IllegalArgumentException when the type is not a lower-case word, or the id is blank
     */
    public AccessGroup {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        if (!type.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("A group type is a lower-case word: " + type);
        }
        if (id.isBlank() || id.contains("\n")) {
            throw new IllegalArgumentException("A group id is not blank and has no line break: " + id);
        }
    }

    /**
     * Every character of one player.
     *
     * @param player the player's UUID
     * @return the group
     */
    public static @NotNull AccessGroup player(@NotNull UUID player) {
        return new AccessGroup(PLAYER, player.toString());
    }

    /**
     * One character.
     *
     * @param player  the player's UUID
     * @param profile the profile's name, as SkysongStats shows it
     * @return the group
     */
    public static @NotNull AccessGroup player(@NotNull UUID player, @NotNull String profile) {
        return new AccessGroup(PLAYER, player + "/" + profile);
    }

    /**
     * Anyone with a permission node.
     *
     * @param node the node, such as {@code myserver.guards}
     * @return the group
     */
    public static @NotNull AccessGroup permission(@NotNull String node) {
        return new AccessGroup(PERMISSION, node);
    }

    /**
     * Reads the {@code <type>:<id>} form {@link #toString()} writes. The type is matched
     * ignoring case; the id is kept as it is.
     *
     * @param text such as {@code economy:smithy/workers}
     * @return the group
     * @throws IllegalArgumentException when the text has no type or no id
     */
    public static @NotNull AccessGroup parse(@NotNull String text) {
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            throw new IllegalArgumentException("Not a <type>:<id> group: " + text);
        }
        return new AccessGroup(text.substring(0, colon).toLowerCase(Locale.ROOT), text.substring(colon + 1));
    }

    /**
     * @return {@code <type>:<id>}
     */
    @Override
    public @NotNull String toString() {
        return type + ":" + id;
    }
}
