package org.skysongdev.skysongnpcs.picks;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * One promotion: a proficiency a grant had picked, raised one tier further with the grant's
 * picks. A respec puts it back to {@code fromTier}.
 *
 * @param grant       the {@link PickGrant#id()} it was paid from
 * @param step        its place among the grant's promotions, from 0, oldest first
 * @param proficiency the SkysongStats proficiency id
 * @param fromTier    the tier it had before
 * @param toTier      the tier it was raised to
 * @param cost        how many of the grant's picks it used
 */
public record PromotionSpend(@NotNull UUID grant, int step, @NotNull String proficiency, @NotNull String fromTier,
                             @NotNull String toTier, int cost) {

    public PromotionSpend {
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(proficiency, "proficiency");
        Objects.requireNonNull(fromTier, "fromTier");
        Objects.requireNonNull(toTier, "toTier");
    }
}
