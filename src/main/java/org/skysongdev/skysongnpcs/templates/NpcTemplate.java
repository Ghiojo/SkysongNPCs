package org.skysongdev.skysongnpcs.templates;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * One kind of NPC, read from {@code templates/<id>.yml}: what Citizens spawns and the stat
 * sheet it starts with. Every NPC made from a template gets a stat sheet. Later milestones add their own parts (ancestries, starting points,
 * proficiency picks, abilities, access).
 *
 * @param id         the file name without {@code .yml}, such as {@code guard_dog}
 * @param entityType the Citizens entity type
 * @param name       the name a new NPC gets
 * @param skin       for player NPCs, the name of the player whose skin it wears, or null
 *                   for Citizens' default
 * @param stats      the sheet's starting base values, by SkysongStats stat id; stats left out
 *                   start at their stats.yml start value
 */
public record NpcTemplate(@NotNull String id, @NotNull EntityType entityType, @NotNull String name,
                          @Nullable String skin, @NotNull Map<String, Integer> stats) {

    public NpcTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(name, "name");
        stats = Map.copyOf(stats);
    }
}
