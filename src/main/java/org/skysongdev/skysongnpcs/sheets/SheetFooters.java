package org.skysongdev.skysongnpcs.sheets;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The lines other plugins show under NPCs' sheets, after the abilities line
 * ({@link org.skysongdev.skysongnpcs.api.sheet.NpcSheets#registerFooter}). Each has an id
 * {@code <plugin>:<name>}, like menu buttons, and a plugin's lines go away when it disables.
 */
public final class SheetFooters implements Listener {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

    private final Logger logger;
    private final Map<String, Footer> footers = new LinkedHashMap<>();

    public SheetFooters(Logger logger) {
        this.logger = logger;
    }

    /**
     * @return the line's id
     * @throws IllegalArgumentException when the name is not valid or already taken by the plugin
     */
    public String register(Plugin owner, String name, Function<UUID, Optional<Component>> line) {
        Objects.requireNonNull(owner, "plugin");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(line, "line");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Sheet footer name '" + name
                    + "' may only hold lower-case letters, digits, _ and -");
        }
        String id = id(owner, name);
        if (footers.containsKey(id)) {
            throw new IllegalArgumentException("A sheet footer " + id + " is registered already");
        }
        footers.put(id, new Footer(owner, line));
        return id;
    }

    /** @return true when the line was there */
    public boolean unregister(Plugin owner, String name) {
        return footers.remove(id(Objects.requireNonNull(owner, "plugin"), Objects.requireNonNull(name, "name"))) != null;
    }

    /** Drops every line of a plugin. */
    public void unregisterAll(Plugin owner) {
        footers.values().removeIf(footer -> footer.plugin().equals(owner));
    }

    /** @return the ids of every registered line, in the order they were added */
    public List<String> ids() {
        return List.copyOf(footers.keySet());
    }

    /**
     * The lines to show under one NPC's sheet, in the order they were added. A line that
     * throws is left out and logged.
     */
    public List<Component> lines(UUID npc) {
        List<Component> lines = new ArrayList<>();
        // A copy, so a footer that registers or removes lines while running cannot break the loop.
        for (Map.Entry<String, Footer> entry : List.copyOf(footers.entrySet())) {
            try {
                entry.getValue().line().apply(npc).ifPresent(lines::add);
            } catch (RuntimeException failed) {
                logger.log(Level.WARNING, "The sheet footer " + entry.getKey() + " failed for NPC " + npc, failed);
            }
        }
        return lines;
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterAll(event.getPlugin());
    }

    private static String id(Plugin owner, String name) {
        return owner.getName().toLowerCase(Locale.ROOT) + ":" + name;
    }

    private record Footer(Plugin plugin, Function<UUID, Optional<Component>> line) {
    }
}
