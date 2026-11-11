package org.skysongdev.skysongnpcs.api.ability;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * One ability from abilities.yml, as players see it. What it does to an NPC (modifiers, stat
 * points, proficiency picks and tiers) stays inside SkysongNPCs, which applies it when the
 * ability is granted and again when abilities.yml changes.
 *
 * @param id          its key in abilities.yml, such as {@code brawler}; what the API and
 *                    commands take
 * @param name        its one-word name, in MiniMessage
 * @param description what hovering it on the stat sheet shows, in MiniMessage
 * @param icon        the item the abilities menu draws it with
 * @since 0.16.0
 */
public record NpcAbility(@NotNull String id, @NotNull String name, @NotNull String description,
                         @NotNull Material icon) {

    /**
     * @throws NullPointerException when any part is null
     */
    public NpcAbility {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
    }
}
