package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.items.NpcItems;
import org.skysongdev.skysongnpcs.items.Blueprints;
import org.skysongdev.skysongnpcs.items.NpcFactory;
import org.skysongdev.skysongnpcs.items.NpcPickup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link NpcItems} over the plugin's {@link Blueprints}. */
final class NpcItemsImpl implements NpcItems {
    private final Blueprints blueprints;
    private final NpcFactory factory;
    // M18
    private final NpcPickup pickup;

    NpcItemsImpl(Blueprints blueprints, NpcFactory factory, NpcPickup pickup) {
        this.blueprints = blueprints;
        this.factory = factory;
        this.pickup = pickup;
    }

    @Override
    public boolean isBlueprint(@Nullable ItemStack item) {
        mainThread();
        return blueprints.template(item).isPresent();
    }

    @Override
    public @NotNull Optional<String> blueprintTemplate(@Nullable ItemStack item) {
        mainThread();
        return blueprints.template(item);
    }

    @Override
    public @NotNull ItemStack makeBlueprint(@NotNull ItemStack base, @NotNull String template, @Nullable String name) {
        mainThread();
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(template, "template");
        if (factory.template(template).isEmpty()) {
            throw new IllegalArgumentException("There is no NPC template " + template);
        }
        return blueprints.make(base, template, name);
    }

    // M18: NPC items

    @Override
    public @NotNull Optional<UUID> storedNpc(@Nullable ItemStack item) {
        mainThread();
        return blueprints.npc(item);
    }

    @Override
    public boolean isCurrent(@Nullable ItemStack item) {
        mainThread();
        return pickup.isCurrent(item);
    }

    @Override
    public @NotNull ItemStack pickUp(@NotNull UUID npc) {
        mainThread();
        return pickup.pickUp(Objects.requireNonNull(npc, "npc"));
    }

    @Override
    public @NotNull ItemStack reissue(@NotNull UUID npc) {
        mainThread();
        return pickup.reissue(Objects.requireNonNull(npc, "npc"));
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
