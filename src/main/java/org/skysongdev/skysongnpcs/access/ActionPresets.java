package org.skysongdev.skysongnpcs.access;

import org.bukkit.configuration.ConfigurationSection;
import org.skysongdev.skysongnpcs.api.access.NpcAction;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Turns the action lists players type into actions: action ids, the presets in config.yml's
 * {@code access.presets}, and {@code all}. Reads config.yml each time, so a reload applies at once.
 */
public final class ActionPresets {
    /** Stands for every action. config.yml cannot redefine it. */
    public static final String ALL = "all";
    static final String SECTION = "access.presets";

    private final Supplier<ConfigurationSection> config;

    /** @param config config.yml as last read */
    public ActionPresets(Supplier<ConfigurationSection> config) {
        this.config = config;
    }

    /**
     * A preset's actions. Ids in it that are not actions are skipped.
     *
     * @return the actions, empty when there is no such preset
     */
    public Optional<Set<NpcAction>> preset(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.equals(ALL)) {
            return Optional.of(Collections.unmodifiableSet(EnumSet.allOf(NpcAction.class)));
        }
        ConfigurationSection presets = config.get().getConfigurationSection(SECTION);
        if (presets == null || !presets.isList(key)) {
            return Optional.empty();
        }
        Set<NpcAction> actions = EnumSet.noneOf(NpcAction.class);
        for (String id : presets.getStringList(key)) {
            NpcAction.fromKey(id).ifPresent(actions::add);
        }
        return Optional.of(Collections.unmodifiableSet(actions));
    }

    /** {@code all} and every preset in config.yml. */
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        names.add(ALL);
        ConfigurationSection presets = config.get().getConfigurationSection(SECTION);
        if (presets != null) {
            presets.getKeys(false).stream().filter(presets::isList).forEach(names::add);
        }
        return names;
    }

    /**
     * Reads a list of action ids and preset names, split by spaces or commas.
     *
     * @return the actions, or the first word that is neither
     */
    public Parsed parse(String text) {
        Set<NpcAction> actions = EnumSet.noneOf(NpcAction.class);
        for (String word : text.split("[\\s,]+")) {
            if (word.isEmpty()) {
                continue;
            }
            Optional<NpcAction> action = NpcAction.fromKey(word);
            if (action.isPresent()) {
                actions.add(action.get());
                continue;
            }
            Optional<Set<NpcAction>> preset = preset(word);
            if (preset.isEmpty()) {
                return new Parsed(Set.of(), word);
            }
            actions.addAll(preset.get());
        }
        return new Parsed(Collections.unmodifiableSet(actions), null);
    }

    /**
     * What {@link #parse} read.
     *
     * @param actions the actions, empty when there was an unknown word or no word at all
     * @param unknown the first word that was not an action or preset, or null
     */
    public record Parsed(Set<NpcAction> actions, String unknown) {
    }
}
