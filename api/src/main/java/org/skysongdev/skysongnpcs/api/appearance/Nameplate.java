package org.skysongdev.skysongnpcs.api.appearance;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/** Whether an NPC's name floats above its head. */
public enum Nameplate {
    /** Always shown. */
    SHOWN("show"),
    /** Shown only while a player looks at the NPC. */
    HOVER("hover"),
    /** Never shown. */
    HIDDEN("hide");

    private final String key;

    Nameplate(String key) {
        this.key = key;
    }

    /**
     * @return the word players type for it: {@code show}, {@code hide} or {@code hover}
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * @param key a word such as {@code hide}, ignoring case
     * @return the nameplate setting, empty when no setting has that word
     */
    public static @NotNull Optional<Nameplate> fromKey(@NotNull String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (Nameplate nameplate : values()) {
            if (nameplate.key.equals(normalized)) {
                return Optional.of(nameplate);
            }
        }
        return Optional.empty();
    }
}
