package org.skysongdev.skysongnpcs.picks;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * One spent pick: the proficiency chosen with a grant, and the tier it had before, so a
 * respec can put it back.
 *
 * @param grant        the {@link PickGrant#id()} it was spent from
 * @param proficiency  the SkysongStats proficiency id
 * @param previousTier the tier id the proficiency had before the pick
 */
public record PickSpend(@NotNull UUID grant, @NotNull String proficiency, @NotNull String previousTier) {

    public PickSpend {
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(proficiency, "proficiency");
        Objects.requireNonNull(previousTier, "previousTier");
    }
}
