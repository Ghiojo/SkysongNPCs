package org.skysongdev.skysongnpcs.api.appearance;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * How an NPC holds its body. Sitting is separate ({@link NpcAppearance#setSitting(java.util.UUID, boolean)}),
 * but an NPC cannot sit and lie at once: lying down makes it stand up first, and sitting down
 * makes a lying NPC stand.
 */
public enum NpcPose {
    /** Standing up, as NPCs do by default. */
    STANDING("stand"),
    /** Crouching (sneaking). */
    CROUCHING("crouch"),
    /** Lying down, as in a bed. */
    LYING("lie");

    private final String key;

    NpcPose(String key) {
        this.key = key;
    }

    /**
     * @return the word players type for it: {@code stand}, {@code crouch} or {@code lie}
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * @param key a word such as {@code crouch}, ignoring case
     * @return the pose, empty when no pose has that word
     */
    public static @NotNull Optional<NpcPose> fromKey(@NotNull String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (NpcPose pose : values()) {
            if (pose.key.equals(normalized)) {
                return Optional.of(pose);
            }
        }
        return Optional.empty();
    }
}
