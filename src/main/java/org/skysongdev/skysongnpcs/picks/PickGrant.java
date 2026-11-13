package org.skysongdev.skysongnpcs.picks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Proficiency picks an NPC was given: choose {@code count} proficiencies of one skill, and
 * each chosen one goes up to {@code tier}. A grant stays after its picks are spent, so a
 * respec can give them back and a source can tell spent picks from unspent ones.
 *
 * @param id     the grant's own id
 * @param npc    the Citizens UUID of the NPC
 * @param source who gave it, such as {@code template}, {@code staff}, {@code ability:<id>}
 *               or a plugin's name; up to 64 characters
 * @param skill  the SkysongStats skill id
 * @param tier   the SkysongStats tier id the picked proficiencies go up to
 * @param count  how many proficiencies it lets the NPC pick, 1 or more
 * @param promotion what its picks may also do to proficiencies it picked; null for nothing
 */
public record PickGrant(@NotNull UUID id, @NotNull UUID npc, @NotNull String source, @NotNull String skill,
                        @NotNull String tier, int count, @Nullable PickPromotion promotion) {
    private static final Pattern SOURCE = Pattern.compile("[^\\s]{1,64}");

    public PickGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(tier, "tier");
        if (!SOURCE.matcher(source).matches()) {
            throw new IllegalArgumentException("A source is 1 to 64 characters without spaces, not '" + source + "'");
        }
        if (count < 1) {
            throw new IllegalArgumentException("A grant gives 1 pick or more, not " + count);
        }
    }

    /** A grant whose picks only pick. */
    public PickGrant(@NotNull UUID id, @NotNull UUID npc, @NotNull String source, @NotNull String skill,
                     @NotNull String tier, int count) {
        this(id, npc, source, skill, tier, count, null);
    }

    /** The same grant with another count. */
    public PickGrant withCount(int newCount) {
        return new PickGrant(id, npc, source, skill, tier, newCount, promotion);
    }

    /** The same grant on another NPC, under a new id: what a copied NPC gets. */
    public PickGrant copiedTo(UUID copy) {
        return new PickGrant(UUID.randomUUID(), copy, source, skill, tier, count, promotion);
    }
}
