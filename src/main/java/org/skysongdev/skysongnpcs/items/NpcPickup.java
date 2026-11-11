package org.skysongdev.skysongnpcs.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.event.NpcPickupEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Picks NPCs up into items and places them again (M18). Picking up despawns the NPC, keeping
 * it in Citizens with everything it has, puts it in state STORED, and makes its NPC item: the
 * blueprint it was placed from with the NPC, a fresh ticket and lore lines added. Only the item
 * with the current ticket places it again, so a duplicated item or one kept from an earlier
 * pickup is refused. {@code /snpc reissue} makes a new item and voids the old one.
 */
public final class NpcPickup {
    /** How a pickup by a player ended. */
    public enum Result { NOT_MANAGED, NOT_ALLOWED, ALREADY_STORED, CANCELLED, FAILED, PICKED_UP }

    /**
     * @param result       how it ended
     * @param placeholders what the result's message fills in
     */
    public record Outcome(Result result, Map<String, String> placeholders) {
    }

    private final NpcRegistry registry;
    private final NpcTickets tickets;
    private final Blueprints blueprints;
    private final NpcSpawner spawner;
    private final AccessService access;
    private final NpcLookup citizens;
    private final Function<NpcOwner, String> ownerNames;
    private final PluginConfiguration configuration;
    private final PluginManager events;

    public NpcPickup(NpcRegistry registry, NpcTickets tickets, Blueprints blueprints, NpcSpawner spawner,
                     AccessService access, NpcLookup citizens, Function<NpcOwner, String> ownerNames,
                     PluginConfiguration configuration, PluginManager events) {
        this.registry = registry;
        this.tickets = tickets;
        this.blueprints = blueprints;
        this.spawner = spawner;
        this.access = access;
        this.citizens = citizens;
        this.ownerNames = ownerNames;
        this.configuration = configuration;
        this.events = events;
    }

    /** Picks it up for the player and tells them how it went. Used by the command and the menu button. */
    public Outcome pickUpAndTell(Player player, UUID npc) {
        Outcome outcome = pickUp(player, npc);
        switch (outcome.result()) {
            case CANCELLED -> { }
            case NOT_MANAGED -> configuration.messages().send(player, "npc.not-managed", outcome.placeholders());
            default -> configuration.messages().send(player,
                    "pickup." + outcome.result().name().toLowerCase(Locale.ROOT).replace('_', '-'), outcome.placeholders());
        }
        return outcome;
    }

    /**
     * Picks the NPC up for the player, when they may (owner, staff or the pickup action), and
     * gives them its item; what does not fit in their inventory drops at their feet.
     */
    public Outcome pickUp(Player player, UUID npc) {
        Map<String, String> placeholders = Map.of("npc", citizens.name(npc));
        Optional<NpcRecord> record = registry.get(npc);
        if (record.isEmpty()) {
            return new Outcome(Result.NOT_MANAGED, placeholders);
        }
        if (!access.may(player, record.get(), NpcAction.PICKUP)) {
            return new Outcome(Result.NOT_ALLOWED, placeholders);
        }
        if (record.get().state() == NpcState.STORED) {
            return new Outcome(Result.ALREADY_STORED, placeholders);
        }
        NpcPickupEvent event = new NpcPickupEvent(player, record.get());
        events.callEvent(event);
        if (event.isCancelled()) {
            return new Outcome(Result.CANCELLED, placeholders);
        }
        Optional<ItemStack> item = store(record.get());
        if (item.isEmpty()) {
            return new Outcome(Result.FAILED, placeholders);
        }
        give(player, item.get());
        return new Outcome(Result.PICKED_UP, placeholders);
    }

    /** @see org.skysongdev.skysongnpcs.api.items.NpcItems#pickUp */
    public ItemStack pickUp(UUID npc) {
        NpcRecord record = registry.get(npc)
                .orElseThrow(() -> new IllegalArgumentException("NPC " + npc + " is not managed"));
        if (record.state() == NpcState.STORED) {
            throw new IllegalStateException("NPC " + npc + " is picked up already");
        }
        return store(record).orElseThrow(() -> new IllegalStateException("Citizens has no NPC " + npc));
    }

    /** @see org.skysongdev.skysongnpcs.api.items.NpcItems#reissue */
    public ItemStack reissue(UUID npc) {
        NpcRecord record = registry.get(npc)
                .orElseThrow(() -> new IllegalArgumentException("NPC " + npc + " is not managed"));
        if (record.state() != NpcState.STORED) {
            throw new IllegalStateException("NPC " + npc + " is placed, not picked up");
        }
        return item(record, tickets.issue(npc, tickets.resume(npc)));
    }

    /** Whether the player may place this NPC from its item: its owner, staff, or the pickup action. */
    public boolean mayPlace(Player player, NpcRecord npc) {
        return access.may(player, npc, NpcAction.PICKUP);
    }

    /** Whether the item is an NPC item with its NPC's current ticket. */
    public boolean isCurrent(@Nullable ItemStack item) {
        Optional<UUID> npc = blueprints.npc(item);
        Optional<UUID> ticket = blueprints.ticket(item);
        return npc.isPresent() && ticket.isPresent() && registry.isManaged(npc.get())
                && tickets.isCurrent(npc.get(), ticket.get());
    }

    /**
     * Spawns a picked-up NPC at {@code location} and brings back the state it had. Its ticket
     * stays, so a copy of the item finds it already placed.
     *
     * @return false when Citizens refused the spawn; it stays picked up
     */
    public boolean placeAgain(UUID npc, Location location) {
        if (!spawner.respawn(npc, location)) {
            return false;
        }
        registry.setState(npc, tickets.resume(npc));
        return true;
    }

    /** Where a placed NPC stands now; empty when it is not spawned. */
    public Optional<Location> location(UUID npc) {
        return spawner.location(npc);
    }

    /** Remembers the blueprint a new NPC was placed from, to give it back on pickup. */
    public void placedFrom(UUID npc, ItemStack blueprint) {
        tickets.rememberBlueprint(npc, blueprint);
    }

    /** The NPC was deleted: its items stop working. */
    public void deleted(UUID npc) {
        tickets.forget(npc);
    }

    public String name(UUID npc) {
        return citizens.name(npc);
    }

    /** Despawns it, issues a ticket, stores it, and makes its item; empty when Citizens has no such NPC. */
    private Optional<ItemStack> store(NpcRecord record) {
        if (!spawner.despawn(record.id())) {
            return Optional.empty();
        }
        UUID ticket = tickets.issue(record.id(), record.state());
        registry.setState(record.id(), NpcState.STORED);
        return Optional.of(item(record, ticket));
    }

    /** The NPC item: its blueprint, or config.yml's pickup.item, with the NPC, ticket and lore. */
    private ItemStack item(NpcRecord record, UUID ticket) {
        Messages messages = configuration.messages();
        Map<String, String> placeholders = Map.of(
                "npc", citizens.name(record.id()), "owner", ownerNames.apply(record.owner()));
        Optional<ItemStack> blueprint = tickets.blueprint(record.id());
        ItemStack item = blueprint.orElseGet(() -> new ItemStack(fallbackMaterial()));
        blueprints.makeNpcItem(item, record.id(), ticket);
        item.editMeta(meta -> {
            if (blueprint.isEmpty()) {
                meta.displayName(itemText(messages.get("pickup.item.name", placeholders)));
            }
            List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
            messages.lines("pickup.item.lore", placeholders).stream().map(NpcPickup::itemText).forEach(lore::add);
            meta.lore(lore);
        });
        return item;
    }

    private Material fallbackMaterial() {
        Material material = Material.matchMaterial(configuration.config().getString("pickup.item", "ARMOR_STAND"));
        return material == null || !material.isItem() || material.isAir() ? Material.ARMOR_STAND : material;
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    /** Item names and lore are italic unless a text says otherwise; messages.yml texts are not, unless they say so. */
    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
