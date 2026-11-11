package org.skysongdev.skysongnpcs.api.choices;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stat points an NPC was given, to spend 1 point = +1 base on an eligible stat.
 *
 * @param id       the grant's own id
 * @param source   who gave them, free text: {@code template}, {@code blueprint}, {@code staff},
 *                 {@code ability:<id>}, or a plugin's own, such as {@code economy:smithy}
 * @param amount   how many points it gives
 * @param spent    how many of them are spent
 * @param eligible the stats they may go on, by SkysongStats stat id; empty for the NPC's
 *                 default eligible stats (config.yml, or its template's)
 */
public record PointGrant(@NotNull UUID id, @NotNull String source, int amount, int spent,
                         @NotNull Set<String> eligible) {

    public PointGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        eligible = Set.copyOf(eligible);
    }

    /** The points still to spend. */
    public int unspent() {
        return amount - spent;
    }
}
