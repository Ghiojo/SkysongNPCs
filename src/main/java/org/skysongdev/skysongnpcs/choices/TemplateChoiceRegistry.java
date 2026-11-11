package org.skysongdev.skysongnpcs.choices;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.skysongdev.skysongnpcs.config.Reloadable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Reads the choice keys of every template file: {@code points}, {@code stat-points},
 * {@code ancestries} and {@code allow-other}. It reads the same files as the
 * {@link org.skysongdev.skysongnpcs.templates.TemplateRegistry}, after it, and like it keeps a
 * file's previous version when the file has a mistake.
 */
public final class TemplateChoiceRegistry implements Reloadable {
    private final File folder;
    private final Function<String, Optional<String>> statIds;
    private final Logger logger;
    private Map<String, TemplateChoices> choices = Map.of();

    public TemplateChoiceRegistry(File folder, Function<String, Optional<String>> statIds, Logger logger) {
        this.folder = folder;
        this.statIds = statIds;
        this.logger = logger;
    }

    /** The template's choice keys; defaults for a template without them. */
    public TemplateChoices get(String template) {
        return choices.getOrDefault(template, TemplateChoices.none(template));
    }

    @Override
    public List<String> reload() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        Map<String, TemplateChoices> read = new HashMap<>();
        List<String> broken = new ArrayList<>();
        for (File file : files == null ? new File[0] : files) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length());
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
                read.put(id, parse(id, yaml, statIds));
            } catch (IOException | InvalidConfigurationException e) {
                // The template registry already reports files that are not YAML.
                keep(id, read);
            } catch (IllegalArgumentException mistake) {
                String path = "templates/" + file.getName();
                logger.severe(path + " was not applied: " + mistake.getMessage());
                broken.add(path);
                keep(id, read);
            }
        }
        choices = Map.copyOf(read);
        return List.copyOf(broken);
    }

    private void keep(String id, Map<String, TemplateChoices> read) {
        TemplateChoices previous = choices.get(id);
        if (previous != null) {
            read.put(id, previous);
        }
    }

    /** Reads one template's choice keys. @throws IllegalArgumentException on a mistake */
    static TemplateChoices parse(String id, ConfigurationSection yaml, Function<String, Optional<String>> statIds) {
        if (yaml.contains("points") && (!yaml.isInt("points") || yaml.getInt("points") < 0)) {
            throw new IllegalArgumentException("points must be a whole number, 0 or more");
        }
        int points = yaml.getInt("points", 0);
        List<String> eligible = null;
        Map<String, Integer> max = null;
        if (yaml.contains("stat-points")) {
            ConfigurationSection section = yaml.getConfigurationSection("stat-points");
            if (section == null) {
                throw new IllegalArgumentException("stat-points must hold eligible: and/or max:");
            }
            eligible = PointRules.readEligible(section, statIds, name -> {
                throw new IllegalArgumentException("stat-points.eligible names " + name + ", which is no SkysongStats stat");
            });
            max = PointRules.readMax(section, statIds, name -> {
                throw new IllegalArgumentException("stat-points.max." + name + " is no SkysongStats stat or no whole number");
            });
        }
        if (yaml.contains("ancestries") && !yaml.isList("ancestries")) {
            throw new IllegalArgumentException("ancestries must be a list of SkysongStats ancestry ids");
        }
        List<String> ancestries = yaml.getStringList("ancestries").stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT)).filter(name -> !name.isEmpty()).distinct().toList();
        if (yaml.contains("allow-other") && !yaml.isBoolean("allow-other")) {
            throw new IllegalArgumentException("allow-other must be true or false");
        }
        return new TemplateChoices(id, points, eligible, max, ancestries, yaml.getBoolean("allow-other", true));
    }
}
