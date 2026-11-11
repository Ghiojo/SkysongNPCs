package org.skysongdev.skysongnpcs.api.groups;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.List;
import java.util.Optional;

/**
 * The groups grants can name besides players, permissions and other plugins' groups:
 * <ul>
 *   <li>{@code group:<id>} a {@link PlayerGroup}, a named list of characters made with
 *       {@code /snpc group} or here.</li>
 *   <li>{@code lp:<group>} a LuckPerms group, inherited groups included, in the player's
 *       current contexts. Needs LuckPerms; without it such grants let nobody in.</li>
 * </ul>
 * Membership is checked live, against the character a player plays at that moment.
 *
 * <p>Deleting a player group also revokes every grant that names it, so a new group with the
 * same name never inherits its access. For the same reason a name some grant still names
 * cannot be used for a new group.
 *
 * <p>Must be called on the server's main thread, and throws {@link IllegalStateException}
 * otherwise. The limits in config.yml apply to {@code /snpc group} only, not here.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 *
 * @since 0.10.0
 */
@ApiStatus.NonExtendable
public interface PlayerGroups {
    /** The type grants write for LuckPerms groups: {@code lp:<group>}. */
    String LUCKPERMS_TYPE = "lp";

    /**
     * A LuckPerms group as grants name it.
     *
     * @param group the LuckPerms group's name
     * @return {@code lp:<group>}
     */
    static @NotNull AccessGroup luckPerms(@NotNull String group) {
        return new AccessGroup(LUCKPERMS_TYPE, group.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Whether LuckPerms is installed, so {@code lp:} groups let anyone in.
     *
     * @return true when it is
     */
    boolean luckPermsEnabled();

    /**
     * A player group by name.
     *
     * @param id the name, matched ignoring case
     * @return the group, empty when there is none
     */
    @NotNull Optional<PlayerGroup> group(@NotNull String id);

    /**
     * Every player group, by name.
     *
     * @return the groups, unmodifiable
     */
    @NotNull List<PlayerGroup> groups();

    /**
     * The player groups one character (or the server) owns, by name.
     *
     * @param owner the owner
     * @return the groups, unmodifiable
     */
    @NotNull List<PlayerGroup> ownedBy(@NotNull NpcOwner owner);

    /**
     * Makes an empty player group.
     *
     * @param id    its name, lower-cased; 1 to 32 letters, digits, {@code -} and {@code _}
     * @param owner who may change it
     * @return the group
     * @throws IllegalArgumentException when the name is not valid
     * @throws IllegalStateException    when the name is taken, or a grant still names it
     */
    @NotNull PlayerGroup create(@NotNull String id, @NotNull NpcOwner owner);

    /**
     * Deletes a player group and revokes every grant, from every source, that names it.
     *
     * @param id the name, matched ignoring case
     * @return true when there was one
     */
    boolean delete(@NotNull String id);

    /**
     * Adds a member to a player group. Nothing changes when it is in it already.
     *
     * @param id     the group's name, matched ignoring case
     * @param member who to add
     * @return the group as it is now
     * @throws IllegalArgumentException when there is no such group
     */
    @NotNull PlayerGroup addMember(@NotNull String id, @NotNull GroupMember member);

    /**
     * Removes a member from a player group. Only that exact member goes: removing a player's
     * {@linkplain GroupMember#everyCharacter every-character} entry leaves their one-character entries.
     *
     * @param id     the group's name, matched ignoring case
     * @param member who to remove
     * @return the group as it is now
     * @throws IllegalArgumentException when there is no such group
     */
    @NotNull PlayerGroup removeMember(@NotNull String id, @NotNull GroupMember member);

    /**
     * Gives a player group to another owner.
     *
     * @param id    the group's name, matched ignoring case
     * @param owner the new owner
     * @return the group as it is now
     * @throws IllegalArgumentException when there is no such group
     */
    @NotNull PlayerGroup setOwner(@NotNull String id, @NotNull NpcOwner owner);
}
