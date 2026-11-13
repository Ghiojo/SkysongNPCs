package org.skysongdev.skysongnpcs.api.ability;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An ability choice an NPC was given: choose {@code count} abilities from {@code options}. A
 * chosen ability is granted under the source {@code "choice:" + source}, so a plugin can tell
 * it from its direct grants.
 *
 * @param id      the choice's id, for {@link NpcAbilities#choose}
 * @param source  who gave it
 * @param options the abilities it offers, as given; one abilities.yml no longer defines is not
 *                offered any more
 * @param count   how many it lets the NPC choose
 * @param chosen  the abilities chosen with it that the NPC still has, oldest first
 * @since 0.23.0
 */
public record NpcAbilityChoice(@NotNull UUID id, @NotNull String source, @NotNull List<String> options, int count,
                               @NotNull List<String> chosen) {

    public NpcAbilityChoice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        options = List.copyOf(options);
        chosen = List.copyOf(chosen);
    }

    /** @return how many abilities can still be chosen with it */
    public int remaining() {
        return Math.max(0, count - chosen.size());
    }
}
