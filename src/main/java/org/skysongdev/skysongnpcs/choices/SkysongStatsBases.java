package org.skysongdev.skysongnpcs.choices;

import org.skysongdev.skysongnpcs.sheets.SkysongStatsSheets;
import org.skysongdev.skysongstats.api.SkysongStatsApi;

import java.util.OptionalInt;
import java.util.UUID;

/** {@link StatBases} through the SkysongStats API. */
public final class SkysongStatsBases implements StatBases {
    @Override
    public OptionalInt base(UUID npc, String stat) {
        return SkysongStatsApi.get().stats().get(SkysongStatsSheets.character(npc), stat)
                .map(values -> OptionalInt.of(values.base()))
                .orElse(OptionalInt.empty());
    }
}
