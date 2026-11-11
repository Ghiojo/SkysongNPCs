package org.skysongdev.skysongnpcs.api.policy;

import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named, reusable set of grants. Every NPC that follows the policy lets in whoever its grants
 * let in, on top of the NPC's own grants, so changing a policy changes all its NPCs at once.
 * An NPC follows at most one policy.
 *
 * @param id     the policy's name, unique on the server: lower-case letters, digits,
 *               {@code -}, {@code _} and {@code .}, at most 64. Plugins should start theirs
 *               with their own name, such as {@code economy.smithy}.
 * @param owner  who it belongs to
 * @param grants what it lets in, one grant per group, each from {@link PolicyOwner#source()};
 *               unmodifiable
 */
public record AccessPolicy(@NotNull String id, @NotNull PolicyOwner owner, @NotNull List<AccessGrant> grants) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");

    /**
     * @throws IllegalArgumentException when the id is not a valid policy id
     */
    public AccessPolicy {
        Objects.requireNonNull(owner, "owner");
        id = normalizeId(id);
        grants = List.copyOf(grants);
    }

    /**
     * A policy id as it is stored: lower case and checked.
     *
     * @param id an id, in any case
     * @return the id in lower case
     * @throws IllegalArgumentException when it holds anything but letters, digits, {@code -},
     *                                  {@code _} and {@code .}, or is empty or longer than 64
     */
    public static @NotNull String normalizeId(@NotNull String id) {
        String normalized = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT);
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Not a policy id: " + id);
        }
        return normalized;
    }
}
