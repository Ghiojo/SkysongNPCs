package org.skysongdev.skysongnpcs.items;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.OptionalInt;

/** Reads and makes blueprint items through their {@link BlueprintTags}. */
public final class Blueprints {
    private final ItemTagGateway tags;

    public Blueprints(ItemTagGateway tags) {
        this.tags = tags;
    }

    /** The template id a blueprint places, lowercased; empty when the item is not a blueprint. */
    public Optional<String> template(@Nullable ItemStack item) {
        return tags.string(item, BlueprintTags.BLUEPRINT).map(id -> id.toLowerCase(Locale.ROOT));
    }

    /** The name the blueprint gives its NPC; empty for the template's. */
    public Optional<String> name(@Nullable ItemStack item) {
        return tags.string(item, BlueprintTags.NAME);
    }

    /** A copy of {@code base} that places {@code template}, named {@code name} when it is not null. */
    public ItemStack make(ItemStack base, String template, @Nullable String name) {
        Objects.requireNonNull(base, "base");
        if (base.isEmpty()) {
            throw new IllegalArgumentException("A blueprint cannot be made from air");
        }
        ItemStack blueprint = base.clone();
        tags.setDetached(blueprint, BlueprintTags.BLUEPRINT, template.toLowerCase(Locale.ROOT));
        if (name != null && !name.isBlank()) {
            tags.setDetached(blueprint, BlueprintTags.NAME, name.trim());
        }
        return blueprint;
    }

    // M18: NPC items: a blueprint that also holds a picked-up NPC and its ticket.

    /** The NPC an NPC item holds; empty when the item is not one (or its tag is not a UUID). */
    public Optional<UUID> npc(@Nullable ItemStack item) {
        return uuid(item, BlueprintTags.NPC_ID);
    }

    /** The ticket an NPC item carries; empty when it carries none. */
    public Optional<UUID> ticket(@Nullable ItemStack item) {
        return uuid(item, BlueprintTags.TICKET);
    }

    /** Writes the NPC and its ticket on {@code item}, an item nobody holds yet. */
    public void makeNpcItem(ItemStack item, UUID npc, UUID ticket) {
        tags.setDetached(item, BlueprintTags.NPC_ID, npc.toString());
        tags.setDetached(item, BlueprintTags.TICKET, ticket.toString());
    }

    private Optional<UUID> uuid(@Nullable ItemStack item, String tag) {
        try {
            return tags.string(item, tag).map(UUID::fromString);
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    // M14

    /** The stat points the blueprint gives its NPC; empty for the template's, or when the tag is no whole number of 0 or more. */
    public OptionalInt points(@Nullable ItemStack item) {
        Optional<String> value = tags.string(item, BlueprintTags.POINTS);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            int points = Integer.parseInt(value.get());
            return points < 0 ? OptionalInt.empty() : OptionalInt.of(points);
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }
}
