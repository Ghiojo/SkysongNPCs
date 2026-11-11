package org.skysongdev.skysongnpcs.appearance;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Keeps Citizens' own commands ({@code /npc}, {@code /trait}, {@code /waypoints}...) staff-only
 * on Skysong NPCs, so players change them only through {@code /snpc}, where access applies.
 * Which commands are guarded, and which of their subcommands stay open (such as
 * {@code /npc select}), is in config.yml. Console and staff are never stopped.
 */
public final class CitizensCommandGuard implements Listener {
    private static final List<String> DEFAULT_GUARDED = List.of("npc", "trait", "waypoints", "wp", "template", "tpl");
    private static final List<String> DEFAULT_OPEN = List.of("help", "list", "select", "sel", "create");

    private final NpcRegistry registry;
    private final AppearanceDriver citizens;
    private final NpcLookup names;
    private final Supplier<ConfigurationSection> config;
    private final Supplier<Messages> messages;

    public CitizensCommandGuard(NpcRegistry registry, AppearanceDriver citizens, NpcLookup names,
                                Supplier<ConfigurationSection> config, Supplier<Messages> messages) {
        this.registry = registry;
        this.citizens = citizens;
        this.names = names;
        this.config = config;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission(Permissions.ADMIN)) {
            return;
        }
        String[] words = event.getMessage().substring(1).trim().split("\\s+");
        if (!guarded(words[0])) {
            return;
        }
        String[] args = Arrays.copyOfRange(words, 1, words.length);
        if (args.length == 0 || lowerCase(config(), "citizens-commands.open", DEFAULT_OPEN)
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return;
        }
        Optional<UUID> npc = citizens.commandTarget(event.getPlayer(), args);
        if (npc.isPresent() && registry.isManaged(npc.get())) {
            event.setCancelled(true);
            messages.get().send(event.getPlayer(), "appearance.citizens-command", Map.of("npc", names.name(npc.get())));
        }
    }

    private boolean guarded(String label) {
        String command = label.toLowerCase(Locale.ROOT);
        int namespace = command.indexOf(':');
        if (namespace >= 0) {
            if (!command.substring(0, namespace).equals("citizens")) {
                return false;
            }
            command = command.substring(namespace + 1);
        }
        return lowerCase(config(), "citizens-commands.guarded", DEFAULT_GUARDED).contains(command);
    }

    private ConfigurationSection config() {
        return config.get();
    }

    private static List<String> lowerCase(ConfigurationSection config, String path, List<String> fallback) {
        List<String> values = config.isList(path) ? config.getStringList(path) : fallback;
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }
}
