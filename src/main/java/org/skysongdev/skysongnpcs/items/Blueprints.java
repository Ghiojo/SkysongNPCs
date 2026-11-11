package org.skysongdev.skysongnpcs.items;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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
}
