package org.skysongdev.skysongnpcs.api.groups;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A named list of characters a player made with {@code /snpc group}, so one grant
 * ({@code group:<id>}) can let all of them use an NPC. Its owner (a character, or the server
 * for groups staff or plugins make) and staff change it.
 *
 * @param id      the group's name, unique on the server: 1 to 32 lower-case letters, digits, {@code -} and {@code _}
 * @param owner   who may change it
 * @param members who is in it, in the order they were added
 */
public record PlayerGroup(@NotNull String id, @NotNull NpcOwner owner, @NotNull Set<GroupMember> members) {
    /** The type grants write for these groups: {@code group:<id>}. */
    public static final String TYPE = "group";

    /**
     * @throws IllegalArgumentException when the id is not a valid group name
     */
    public PlayerGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        if (!isValidId(id)) {
            throw new IllegalArgumentException("Not a group name: " + id);
        }
        members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
    }

    /**
     * Whether a text can name a group: 1 to 32 lower-case letters, digits, {@code -} and {@code _}.
     *
     * @param id the text
     * @return true when it can
     */
    public static boolean isValidId(@Nullable String id) {
        return id != null && id.matches("[a-z0-9_-]{1,32}");
    }

    /**
     * The group as grants name it.
     *
     * @param id the group's name
     * @return {@code group:<id>}
     */
    public static @NotNull AccessGroup reference(@NotNull String id) {
        return new AccessGroup(TYPE, id);
    }

    /**
     * This group as grants name it.
     *
     * @return {@code group:<id>}
     */
    public @NotNull AccessGroup reference() {
        return reference(id);
    }

    /**
     * Whether a player, playing a profile, is in this group.
     *
     * @param player        the player's UUID
     * @param activeProfile the profile they play now, null when they play none
     * @return true when one of the members is them
     */
    public boolean contains(@NotNull UUID player, @Nullable String activeProfile) {
        for (GroupMember member : members) {
            if (member.includes(player, activeProfile)) {
                return true;
            }
        }
        return false;
    }
}
