package org.skysongdev.skysongnpcs.choices;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.skysongdev.skysongnpcs.config.Reloadable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * config.yml's {@code choices} section: the default eligible stats and caps of stat points.
 * Names that are no SkysongStats stat are left out and logged on every reload, so a
 * stats.yml without them still works.
 */
public final class ChoiceSettings implements Reloadable {
    private final Supplier<FileConfiguration> config;
    private final Function<String, Optional<String>> statIds;
    private final Logger logger;
    private PointRules defaults = new PointRules(List.of(), Map.of());

    public ChoiceSettings(Supplier<FileConfiguration> config, Function<String, Optional<String>> statIds, Logger logger) {
        this.config = config;
        this.statIds = statIds;
        this.logger = logger;
    }

    public PointRules defaults() {
        return defaults;
    }

    /** config.yml itself is read by the plugin's configuration first; this reads its section again. */
    @Override
    public List<String> reload() {
        ConfigurationSection section = config.get().getConfigurationSection("choices.stat-points");
        if (section == null) {
            section = new YamlConfiguration();
        }
        Function<String, String> where = name -> "config.yml choices.stat-points names " + name
                + ", which is no SkysongStats stat; it is left out.";
        List<String> eligible = PointRules.readEligible(section, statIds, name -> logger.warning(where.apply(name)));
        Map<String, Integer> max = PointRules.readMax(section, statIds, name -> logger.warning(where.apply(name)));
        defaults = new PointRules(Objects.requireNonNullElse(eligible, List.of()), Objects.requireNonNullElse(max, Map.of()));
        return List.of();
    }
}
