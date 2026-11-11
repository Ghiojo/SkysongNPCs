package org.skysongdev.skysongnpcs;

import org.skysongdev.skysongnpcs.characters.CharacterLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** SkysongStats' profiles for tests. */
public final class FakeCharacterLookup implements CharacterLookup {
    private final Map<UUID, List<String>> profiles = new HashMap<>();
    private final Map<UUID, String> active = new HashMap<>();

    /** Gives the player a profile and makes it the one they play. */
    public void play(UUID player, String profile) {
        profiles.computeIfAbsent(player, ignored -> new ArrayList<>());
        if (!profiles.get(player).contains(profile)) {
            profiles.get(player).add(profile);
        }
        active.put(player, profile);
    }

    @Override
    public Optional<String> activeProfile(UUID player) {
        return Optional.ofNullable(active.get(player));
    }

    @Override
    public List<String> profiles(UUID player) {
        return List.copyOf(profiles.getOrDefault(player, List.of()));
    }
}
