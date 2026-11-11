package org.skysongdev.skysongnpcs.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.characters.CharacterLookup;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the commands that act on one NPC share: finding the NPC, reading an owner from the
 * command line, telling whether the sender owns an NPC, and writing names into messages.
 * Each lookup that fails tells the sender why and returns empty.
 */
public final class NpcTargeting {
    private final NpcRegistry registry;
    private final NpcLookup citizens;
    private final CharacterLookup characters;
    private final PluginConfiguration configuration;

    public NpcTargeting(NpcRegistry registry, NpcLookup citizens, CharacterLookup characters,
                        PluginConfiguration configuration) {
        this.registry = registry;
        this.citizens = citizens;
        this.characters = characters;
        this.configuration = configuration;
    }

    NpcRegistry registry() {
        return registry;
    }

    CharacterLookup characters() {
        return characters;
    }

    /** The Citizens NPC the sender looks at or selected, managed or not. */
    Optional<UUID> target(CommandSender sender) {
        Optional<UUID> npc = citizens.target(sender);
        if (npc.isEmpty()) {
            send(sender, "npc.no-target", Map.of());
        }
        return npc;
    }

    /** The managed NPC the sender looks at or selected. */
    Optional<NpcRecord> managedTarget(CommandSender sender) {
        Optional<UUID> npc = target(sender);
        if (npc.isEmpty()) {
            return Optional.empty();
        }
        Optional<NpcRecord> record = registry.get(npc.get());
        if (record.isEmpty()) {
            send(sender, "npc.not-managed", Map.of("npc", citizens.name(npc.get())));
        }
        return record;
    }

    /**
     * The character a player name and, optionally, a profile name stand for. Without a profile
     * it is the one the player is playing now. Offline players work when they joined before.
     */
    Optional<NpcOwner> character(CommandSender sender, String playerName, String profile) {
        OfflinePlayer player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            player = Bukkit.getOfflinePlayerIfCached(playerName);
        }
        if (player == null) {
            send(sender, "npc.unknown-player", Map.of("player", playerName));
            return Optional.empty();
        }
        String name = player.getName() == null ? playerName : player.getName();
        if (profile == null) {
            Optional<String> active = characters.activeProfile(player.getUniqueId());
            if (active.isEmpty()) {
                send(sender, "npc.no-active-profile", Map.of("player", name));
            }
            UUID id = player.getUniqueId();
            return active.map(found -> NpcOwner.character(id, found));
        }
        if (!characters.profiles(player.getUniqueId()).contains(profile)) {
            send(sender, "npc.unknown-profile", Map.of("player", name, "profile", profile));
            return Optional.empty();
        }
        return Optional.of(NpcOwner.character(player.getUniqueId(), profile));
    }

    /** Whether the sender is the NPC's owner: a player playing the owning character right now. */
    boolean isOwner(CommandSender sender, NpcRecord npc) {
        if (!(sender instanceof Player player) || !(npc.owner() instanceof NpcOwner.CharacterOwner owner)) {
            return false;
        }
        return owner.player().equals(player.getUniqueId())
                && characters.activeProfile(player.getUniqueId()).map(owner.profile()::equals).orElse(false);
    }

    boolean isStaff(CommandSender sender) {
        return sender.hasPermission(Permissions.ADMIN);
    }

    String npcName(UUID npc) {
        return citizens.name(npc);
    }

    /** The owner as messages.yml's {@code owner-name} texts write it. */
    String ownerName(NpcOwner owner) {
        if (owner instanceof NpcOwner.CharacterOwner character) {
            String player = Bukkit.getOfflinePlayer(character.player()).getName();
            return configuration.messages().plain("owner-name.character", Map.of(
                    "player", player == null ? character.player().toString() : player,
                    "profile", character.profile()));
        }
        return configuration.messages().plain("owner-name.server", Map.of());
    }

    void send(CommandSender sender, String key, Map<String, String> placeholders) {
        configuration.messages().send(sender, key, placeholders);
    }
}
