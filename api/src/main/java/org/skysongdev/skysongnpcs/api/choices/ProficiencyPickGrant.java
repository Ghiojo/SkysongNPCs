package org.skysongdev.skysongnpcs.api.choices;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Proficiency picks an NPC was given: pick {@code count} proficiencies of one skill, and each
 * picked one goes up to {@code tier}. Since 0.22.0 a grant may also promote: spend
 * {@code promotionCost} of its picks to raise a proficiency it picked one tier further, up
 * to {@code promoteTo}.
 *
 * @param id            the grant's own id
 * @param source        who gave them, free text: {@code template}, {@code staff},
 *                      {@code ability:<id>}, or a plugin's own, such as {@code economy:smithy}
 * @param skill         the SkysongStats skill id
 * @param tier          the SkysongStats tier id the picked proficiencies go up to
 * @param count         how many picks it gives
 * @param picked        the proficiency ids picked with it so far, oldest first
 * @param promoteTo     the highest tier its promotions reach; null when it cannot promote
 * @param promotionCost how many picks one promotion uses; 0 when it cannot promote
 * @param promoted      the proficiency ids promoted with it so far, one entry per promotion,
 *                      oldest first
 * @since 0.15.0
 */
public record ProficiencyPickGrant(@NotNull UUID id, @NotNull String source, @NotNull String skill,
                                   @NotNull String tier, int count, @NotNull List<String> picked,
                                   @Nullable String promoteTo, int promotionCost, @NotNull List<String> promoted) {

    public ProficiencyPickGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(tier, "tier");
        picked = List.copyOf(picked);
        promoted = List.copyOf(promoted);
    }

    /** A grant that cannot promote, as before 0.22.0. */
    public ProficiencyPickGrant(@NotNull UUID id, @NotNull String source, @NotNull String skill,
                                @NotNull String tier, int count, @NotNull List<String> picked) {
        this(id, source, skill, tier, count, picked, null, 0, List.of());
    }

    /** The picks still to spend, on picks or promotions. */
    public int unspent() {
        return Math.max(0, count - picked.size() - promoted.size() * promotionCost);
    }
}
