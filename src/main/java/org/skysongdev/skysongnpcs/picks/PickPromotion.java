package org.skysongdev.skysongnpcs.picks;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * What a pick grant's picks may also do: raise a proficiency the grant already picked one
 * tier further, up to {@code tier}, for {@code cost} of its picks. Two picks on the same
 * proficiency (cost 1) take it from the grant's tier to the next one.
 *
 * @param tier the highest tier a promotion reaches, above the grant's own
 * @param cost how many of the grant's picks one promotion uses, 1 or more
 */
public record PickPromotion(@NotNull String tier, int cost) {

    public PickPromotion {
        Objects.requireNonNull(tier, "tier");
        if (cost < 1) {
            throw new IllegalArgumentException("A promotion costs 1 pick or more, not " + cost);
        }
    }
}
