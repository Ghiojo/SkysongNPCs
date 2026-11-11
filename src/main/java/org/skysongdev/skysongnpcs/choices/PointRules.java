package org.skysongdev.skysongnpcs.choices;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Which stats points may go on by default, and how high points may push each stat's base.
 * config.yml's {@code choices.stat-points} gives the defaults; a template may override either.
 *
 * @param eligible stat ids, in the order the points menu shows them
 * @param max      the highest base points may give a stat, by stat id; stats left out have no cap
 */
public record PointRules(List<String> eligible, Map<String, Integer> max) {

    public PointRules {
        eligible = List.copyOf(eligible);
        max = Map.copyOf(max);
    }

    /** The highest base points may give the stat; empty for no cap. */
    public OptionalInt cap(String stat) {
        Integer cap = max.get(stat);
        return cap == null ? OptionalInt.empty() : OptionalInt.of(cap);
    }

    /** These rules with a template's overrides; a null part keeps this one's. */
    public PointRules with(@Nullable List<String> eligibleOverride, @Nullable Map<String, Integer> maxOverride) {
        return new PointRules(eligibleOverride == null ? eligible : eligibleOverride,
                maxOverride == null ? max : maxOverride);
    }

    /**
     * Reads the eligible list of a {@code stat-points} section.
     *
     * @param unknown hears each name that is no SkysongStats stat; it is left out
     * @return null when the section has no {@code eligible} key
     */
    static @Nullable List<String> readEligible(ConfigurationSection section, Function<String, Optional<String>> statIds,
                                               Consumer<String> unknown) {
        if (!section.contains("eligible")) {
            return null;
        }
        List<String> eligible = new ArrayList<>();
        for (String name : section.getStringList("eligible")) {
            Optional<String> id = statIds.apply(name.trim());
            if (id.isEmpty()) {
                unknown.accept(name);
            } else if (!eligible.contains(id.get())) {
                eligible.add(id.get());
            }
        }
        return eligible;
    }

    /**
     * Reads the max map of a {@code stat-points} section; values that are not whole numbers
     * count as unknown.
     *
     * @return null when the section has no {@code max} key
     */
    static @Nullable Map<String, Integer> readMax(ConfigurationSection section, Function<String, Optional<String>> statIds,
                                                  Consumer<String> unknown) {
        ConfigurationSection max = section.getConfigurationSection("max");
        if (max == null) {
            return null;
        }
        Map<String, Integer> caps = new LinkedHashMap<>();
        for (String name : max.getKeys(false)) {
            Optional<String> id = statIds.apply(name);
            if (id.isEmpty() || !max.isInt(name)) {
                unknown.accept(name);
            } else {
                caps.put(id.get(), max.getInt(name));
            }
        }
        return caps;
    }
}
