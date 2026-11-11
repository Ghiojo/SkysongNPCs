package org.skysongdev.skysongnpcs.characters;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Players' characters (SkysongStats profiles). {@link StatsCharacterLookup} is the real one;
 * tests use their own, since SkysongStats is not on the test classpath.
 */
public interface CharacterLookup {

    /** The profile a player is playing now, empty when they have none. */
    Optional<String> activeProfile(UUID player);

    /** Every profile a player has. */
    List<String> profiles(UUID player);
}
