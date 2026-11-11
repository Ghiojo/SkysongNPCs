package org.skysongdev.skysongnpcs.api.choices;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Proficiency picks an NPC was given: pick {@code count} proficiencies of one skill, and each
 * picked one goes up to {@code tier}.
 *
 * @param id     the grant's own id
 * @param source who gave them, free text: {@code template}, {@code staff}, {@code ability:<id>},
 *               or a plugin's own, such as {@code economy:smithy}
 * @param skill  the SkysongStats skill id
 * @param tier   the SkysongStats tier id the picked proficiencies go up to
 * @param count  how many proficiencies it lets the NPC pick
 * @param picked the proficiency ids picked with it so far, oldest first
 * @since 0.15.0
 */
public record ProficiencyPickGrant(@NotNull UUID id, @NotNull String source, @NotNull String skill,
                                   @NotNull String tier, int count, @NotNull List<String> picked) {

    public ProficiencyPickGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(tier, "tier");
        picked = List.copyOf(picked);
    }

    /** The picks still to make. */
    public int unspent() {
        return Math.max(0, count - picked.size());
    }
}
