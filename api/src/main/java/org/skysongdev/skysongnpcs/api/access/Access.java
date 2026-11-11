package org.skysongdev.skysongnpcs.api.access;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what with managed NPCs. The owner (while playing the owning character) and staff
 * ({@code skysongnpcs.admin}) may do everything; a server-owned NPC is staff-only unless a
 * grant lets others in. Everyone else needs a grant: a group, the actions it allows, and its
 * {@link GrantSource}.
 *
 * <p>A plugin changes its own grants with {@link GrantSource#plugin(Plugin)}, and should
 * leave other sources' grants alone, as {@code /snpc access} does. Every change fires
 * {@link org.skysongdev.skysongnpcs.api.event.NpcAccessChangeEvent}. Grants are deleted with
 * their NPC, and copied with it by {@code /npc copy}.
 *
 * <p>Must be called on the server's main thread, and throws {@link IllegalStateException} otherwise.
 * Methods that take an NPC throw {@link IllegalArgumentException} when it is not managed.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface Access {

    /**
     * Whether a player may do an action with an NPC now, as the character they are playing.
     *
     * @param player the player
     * @param npc    the NPC's Citizens UUID
     * @param action the action
     * @return true when they may
     */
    boolean may(@NotNull Player player, @NotNull UUID npc, @NotNull NpcAction action);

    /**
     * Every action a player may do with an NPC now, {@code -view}s covered by an {@code -edit}
     * included.
     *
     * @param player the player
     * @param npc    the NPC's Citizens UUID
     * @return the actions, unmodifiable, empty when they may do nothing
     */
    @NotNull Set<NpcAction> allowed(@NotNull Player player, @NotNull UUID npc);

    /**
     * Every grant of an NPC, from every source.
     *
     * @param npc the NPC's Citizens UUID
     * @return the grants, an unmodifiable list in the order they were first made
     */
    @NotNull List<AccessGrant> grants(@NotNull UUID npc);

    /**
     * Lets a group do more with an NPC: the actions are added to the group's grant from this
     * source, which is made when it does not exist yet.
     *
     * @param npc     the NPC's Citizens UUID
     * @param source  who grants it; a plugin uses {@link GrantSource#plugin(Plugin)}
     * @param group   who it lets in
     * @param actions what to add; not empty
     * @return the grant as it is now
     */
    @NotNull AccessGrant grant(@NotNull UUID npc, @NotNull GrantSource source, @NotNull AccessGroup group,
                               @NotNull Set<NpcAction> actions);

    /**
     * Sets exactly what one group's grant from this source allows, replacing what it allowed
     * before. Handy for a plugin that keeps its own permission flags in step.
     *
     * @param npc     the NPC's Citizens UUID
     * @param source  whose grant
     * @param group   whose grant
     * @param actions what it allows from now on; empty deletes the grant
     * @return the grant as it is now, empty when deleted
     */
    @NotNull Optional<AccessGrant> setGrant(@NotNull UUID npc, @NotNull GrantSource source, @NotNull AccessGroup group,
                                            @NotNull Set<NpcAction> actions);

    /**
     * Deletes one group's grant from this source.
     *
     * @param npc    the NPC's Citizens UUID
     * @param source whose grant
     * @param group  whose grant
     * @return true when there was one
     */
    boolean revoke(@NotNull UUID npc, @NotNull GrantSource source, @NotNull AccessGroup group);

    /**
     * Deletes every grant one source made on an NPC, for example when a business lets go of it.
     *
     * @param npc    the NPC's Citizens UUID
     * @param source whose grants
     * @return how many were deleted
     */
    int revokeAll(@NotNull UUID npc, @NotNull GrantSource source);

    /**
     * Lets grants name a plugin's groups as {@code <type>:<id>}. Removed again when that
     * plugin disables, or with {@link #unregisterGroupProvider(String)}. Grants naming the
     * type stay while no provider is registered; they just let nobody in.
     *
     * @param plugin   the plugin that keeps the groups
     * @param type     the type grants write, a lower-case word such as {@code economy}
     * @param provider answers who is in which group
     * @throws IllegalArgumentException when the type is {@code player} or {@code perm}, is
     *                                  reserved for SkysongNPCs' own groups ({@code lp},
     *                                  {@code group}), or is not a lower-case word
     * @throws IllegalStateException    when another plugin registered the type already
     */
    void registerGroupProvider(@NotNull Plugin plugin, @NotNull String type, @NotNull GroupProvider provider);

    /**
     * Removes a group provider.
     *
     * @param type the type it was registered under
     * @return true when one was registered
     */
    boolean unregisterGroupProvider(@NotNull String type);

    /**
     * The actions a preset in config.yml stands for, as of the last reload. Presets are
     * shorthand: a grant made from one keeps its actions when the preset changes later.
     *
     * @param name the preset's name, such as {@code customer}
     * @return its actions, empty when config.yml has no such preset
     */
    @NotNull Optional<Set<NpcAction>> preset(@NotNull String name);
}
