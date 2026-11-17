package org.skysongdev.skysongnpcs.api.sheet;

import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongstats.api.CharacterId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Managed NPCs' stat sheets. A sheet is a SkysongStats plugin character owned by SkysongNPCs,
 * {@code CharacterId(npcUuid, "npc")}, so everything else about it (stats, modifiers,
 * proficiencies, injuries, conditions) goes through the SkysongStats API with the id from
 * {@link #character(UUID)}. SkysongNPCs keeps the items an NPC carries counted toward its
 * item-tag stats (Arcrot...) by itself.
 *
 * <p>Every managed NPC has a sheet: SkysongNPCs makes it when the NPC is adopted, made from a
 * template or copied, and gives one back to an NPC found without one on start and after a
 * SkysongStats reload. {@link #hasSheet} is false for a managed NPC only while its character
 * was deleted by hand and not yet repaired.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface NpcSheets {
    /** The profile part of every NPC's SkysongStats character id. */
    String PROFILE = "npc";

    /**
     * @param npc the NPC's Citizens UUID
     * @return whether the NPC has a stat sheet
     */
    boolean hasSheet(@NotNull UUID npc);

    /**
     * The NPC's SkysongStats character, for use with the SkysongStats API.
     *
     * @param npc the NPC's Citizens UUID
     * @return its character id, empty when the NPC has no sheet
     */
    @NotNull Optional<CharacterId> character(@NotNull UUID npc);

    /**
     * Gives a managed NPC a stat sheet. Managed NPCs have one from the start, so this only
     * replaces one deleted in SkysongStats before SkysongNPCs repairs it.
     *
     * @param npc   the NPC's Citizens UUID
     * @param stats starting base values by SkysongStats stat id or alias; the stats left out
     *              start at their stats.yml start value
     * @return the new sheet's character id
     * @throws IllegalArgumentException when the NPC is not managed or a stat does not exist
     * @throws IllegalStateException    when the NPC has a sheet already
     */
    @NotNull CharacterId create(@NotNull UUID npc, @NotNull Map<String, Integer> stats);

    /**
     * Gives a managed NPC the stat sheet a template describes.
     *
     * @param npc      the NPC's Citizens UUID
     * @param template a template's id, the name of its file in {@code templates/}
     * @return the new sheet's character id
     * @throws IllegalArgumentException when there is no such template, or
     *                                  as {@link #create(UUID, Map)}
     * @throws IllegalStateException    when the NPC has a sheet already
     */
    @NotNull CharacterId createFromTemplate(@NotNull UUID npc, @NotNull String template);

    /**
     * Counts the items the NPC carries toward its item-tag stats now. SkysongNPCs also does
     * it on its own every few seconds; call this after changing an NPC's inventory or
     * equipment when the stats must be right straight away.
     *
     * @param npc the NPC's Citizens UUID; nothing happens when it has no sheet
     */
    void recountItems(@NotNull UUID npc);

    /**
     * Adds a line under every NPC's sheet, after its abilities, wherever the sheet is shown:
     * {@code /snpc sheet} and the NPC menu's Sheet button. Lines show in the order they were
     * added. It is removed again when the plugin disables. A footer that throws is skipped
     * for that sheet and logged.
     *
     * @param plugin the plugin that owns the line; its name, in lower case, starts the id
     * @param name   the line's name inside the plugin: lower-case letters, digits, {@code _} and {@code -}
     * @param line   the line for an NPC, by its Citizens UUID, called each time a sheet is shown;
     *               empty to show nothing under that NPC's sheet
     * @return the line's id, {@code <plugin>:<name>}
     * @throws IllegalArgumentException when the name is not valid or the plugin already has a line with it
     * @since 0.24.0
     */
    @NotNull String registerFooter(@NotNull Plugin plugin, @NotNull String name,
                                   @NotNull Function<UUID, Optional<Component>> line);

    /**
     * Removes a line added with {@link #registerFooter}.
     *
     * @param plugin the plugin that registered it
     * @param name   its name inside that plugin
     * @return true when it was there
     * @since 0.24.0
     */
    boolean unregisterFooter(@NotNull Plugin plugin, @NotNull String name);
}
