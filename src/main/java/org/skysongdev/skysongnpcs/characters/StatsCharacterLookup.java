package org.skysongdev.skysongnpcs.characters;

import org.skysongdev.skysongstats.api.CharacterId;
import org.skysongdev.skysongstats.api.SkysongStatsApi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads profiles from SkysongStats, which keeps them all in memory, so offline players work too. */
public final class StatsCharacterLookup implements CharacterLookup {

    @Override
    public Optional<String> activeProfile(UUID player) {
        return SkysongStatsApi.get().profiles().active(player).map(CharacterId::profile);
    }

    @Override
    public List<String> profiles(UUID player) {
        return SkysongStatsApi.get().profiles().all(player).stream().map(CharacterId::profile).toList();
    }
}
