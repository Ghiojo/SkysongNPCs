package org.skysongdev.skysongnpcs.items;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.skysongdev.skysongnpcs.api.event.NpcPlaceEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.characters.CharacterLookup;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Places an NPC from a blueprint onto the top of a block, like SkysongWardrobe places a
 * mannequin: checks permission, template, character, space, regions and limits, fires
 * {@link NpcPlaceEvent}, makes the NPC facing the player, and uses up one item unless the
 * player is in creative. The NPC belongs to the character the player is playing.
 */
public final class NpcPlacer {
    /** How a placing attempt ended; every result but {@link #NOT_A_BLUEPRINT} uses up the click. */
    public enum Result {
        NOT_A_BLUEPRINT, NO_PERMISSION, UNKNOWN_TEMPLATE, NO_CHARACTER, NO_SPACE, NO_ACCESS_HERE,
        CHARACTER_LIMIT, CHUNK_LIMIT, CANCELLED, FAILED, PLACED
    }

    /**
     * @param result       how it ended
     * @param placeholders what the result's message fills in
     */
    public record Outcome(Result result, Map<String, String> placeholders) {
        static Outcome of(Result result) {
            return new Outcome(result, Map.of());
        }
    }

    /** Facing is rounded to the nearest of eight directions. */
    private static final float FACING_STEP = 45f;

    private final Blueprints blueprints;
    private final NpcFactory factory;
    private final NpcRegistry registry;
    private final CharacterLookup characters;
    private final NpcSpawner spawner;
    private final PlaceRegions regions;
    private final Supplier<PlacingSettings> settings;
    private final PluginManager events;
    private final Logger logger;

    public NpcPlacer(Blueprints blueprints, NpcFactory factory, NpcRegistry registry, CharacterLookup characters,
                     NpcSpawner spawner, PlaceRegions regions, Supplier<PlacingSettings> settings,
                     PluginManager events, Logger logger) {
        this.blueprints = blueprints;
        this.factory = factory;
        this.registry = registry;
        this.characters = characters;
        this.spawner = spawner;
        this.regions = regions;
        this.settings = settings;
        this.events = events;
        this.logger = logger;
    }

    /** Tries to place the item in the player's hand on top of {@code ground}. Main thread only. */
    public Outcome place(Player player, ItemStack item, Block ground) {
        Optional<String> templateId = blueprints.template(item);
        if (templateId.isEmpty()) {
            return Outcome.of(Result.NOT_A_BLUEPRINT);
        }
        if (!player.hasPermission(Permissions.PLACE)) {
            return Outcome.of(Result.NO_PERMISSION);
        }
        Optional<NpcTemplate> template = factory.template(templateId.get());
        if (template.isEmpty()) {
            logger.warning(player.getName() + " used a blueprint for the template " + templateId.get()
                    + ", which templates/ does not have.");
            return new Outcome(Result.UNKNOWN_TEMPLATE, Map.of("template", templateId.get()));
        }
        Optional<String> profile = characters.activeProfile(player.getUniqueId());
        if (profile.isEmpty()) {
            return Outcome.of(Result.NO_CHARACTER);
        }
        NpcOwner owner = NpcOwner.character(player.getUniqueId(), profile.get());

        Block feet = ground.getRelative(BlockFace.UP);
        if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
            return Outcome.of(Result.NO_SPACE);
        }
        Location location = feet.getLocation().add(0.5, 0, 0.5);
        location.setYaw(facing(player.getLocation().getYaw()));
        if (!regions.allows(player, location)) {
            return Outcome.of(Result.NO_ACCESS_HERE);
        }
        PlacingSettings limits = settings.get();
        boolean staff = player.hasPermission(Permissions.ADMIN);
        if (!staff && !PlacingSettings.roomFor(limits.perCharacter(), outInTheWorld(owner))) {
            return new Outcome(Result.CHARACTER_LIMIT, Map.of("limit", Integer.toString(limits.perCharacter())));
        }
        if (!staff && !PlacingSettings.roomFor(limits.perChunk(), managedIn(feet))) {
            return new Outcome(Result.CHUNK_LIMIT, Map.of("limit", Integer.toString(limits.perChunk())));
        }

        NpcPlaceEvent event = new NpcPlaceEvent(player, item, template.get().id(), owner, location);
        events.callEvent(event);
        if (event.isCancelled()) {
            return Outcome.of(Result.CANCELLED);
        }
        String name = blueprints.name(item).orElse(template.get().name());
        Optional<NpcRecord> placed;
        try {
            placed = factory.create(template.get(), name, owner, location);
        } catch (RuntimeException failed) {
            logger.log(Level.SEVERE, "Could not make an NPC from the template " + template.get().id()
                    + " for " + player.getName() + ", so it was not placed and the item was kept.", failed);
            return Outcome.of(Result.FAILED);
        }
        if (placed.isEmpty()) {
            // Another plugin cancelled the Citizens spawn.
            return Outcome.of(Result.NO_ACCESS_HERE);
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract();
        }
        return new Outcome(Result.PLACED, Map.of("npc", name));
    }

    /** NPCs the character has in the world: picked-up ones live in items and do not count. */
    private int outInTheWorld(NpcOwner owner) {
        return (int) registry.ownedBy(owner).stream().filter(npc -> npc.state() != NpcState.STORED).count();
    }

    private int managedIn(Block feet) {
        return (int) spawner.spawnedIn(feet.getChunk()).stream().filter(registry::isManaged).count();
    }

    /** The yaw that faces back at a player looking along {@code playerYaw}, in steps of 45 degrees. */
    static float facing(float playerYaw) {
        float towardPlayer = playerYaw + 180f;
        float snapped = Math.round(towardPlayer / FACING_STEP) * FACING_STEP;
        return ((snapped % 360f) + 360f) % 360f;
    }
}
