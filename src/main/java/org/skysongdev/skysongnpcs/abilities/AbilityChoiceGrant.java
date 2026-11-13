package org.skysongdev.skysongnpcs.abilities;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An ability choice an NPC was given: choose {@code count} abilities from {@code options}.
 * A grant stays after its choices are made, so a respec can give them back.
 *
 * @param id      the grant's own id
 * @param npc     the Citizens UUID of the NPC
 * @param source  who gave it, such as {@code economy:module:trade}; up to 64 characters, no spaces
 * @param options the abilities.yml ids it offers, each once, in the order given
 * @param count   how many it lets the NPC choose, 1 or more
 */
public record AbilityChoiceGrant(@NotNull UUID id, @NotNull UUID npc, @NotNull String source,
                                 @NotNull List<String> options, int count) {
    private static final Pattern SOURCE = Pattern.compile("[^\\s]{1,64}");

    public AbilityChoiceGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(source, "source");
        options = List.copyOf(new LinkedHashSet<>(options));
        if (!SOURCE.matcher(source).matches()) {
            throw new IllegalArgumentException("A source is 1 to 64 characters without spaces, not '" + source + "'");
        }
        if (options.isEmpty()) {
            throw new IllegalArgumentException("An ability choice offers 1 ability or more");
        }
        if (count < 1) {
            throw new IllegalArgumentException("An ability choice gives 1 choice or more, not " + count);
        }
    }

    /** The same grant with another count. */
    public AbilityChoiceGrant withCount(int newCount) {
        return new AbilityChoiceGrant(id, npc, source, options, newCount);
    }

    /** The same grant on another NPC, under a new id: what a copied NPC gets. */
    public AbilityChoiceGrant copiedTo(UUID copy) {
        return new AbilityChoiceGrant(UUID.randomUUID(), copy, source, options, count);
    }
}
