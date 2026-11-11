package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.skysongdev.skysongnpcs.config.Reloadable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The {@code abilities:} list of every template in {@code templates/}: the abilities an NPC
 * made from the template starts with, as abilities.yml ids. The template registry reads the
 * rest of each file. A list with a mistake keeps its previous version. Ids abilities.yml does
 * not have are checked when an NPC is made, since abilities.yml can change on its own.
 *
 * <pre>
 * abilities: [brawler, keen]
 * </pre>
 */
public final class TemplateAbilities implements Reloadable {
    static final String KEY = "abilities";

    private final File folder;
    private final Logger logger;
    private Map<String, List<String>> abilities = Map.of();

    public TemplateAbilities(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    /** The abilities a template gives; empty when it gives none or does not exist. */
    public List<String> of(String template) {
        return abilities.getOrDefault(template, List.of());
    }

    @Override
    public List<String> reload() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        Map<String, List<String>> read = new LinkedHashMap<>();
        List<String> broken = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length());
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
                read.put(id, parse(yaml));
            } catch (IOException | InvalidConfigurationException unreadable) {
                // The template registry reports a file it cannot read.
                keepPrevious(read, id);
            } catch (IllegalArgumentException mistake) {
                String path = folder.getName() + "/" + file.getName();
                logger.severe(path + ": " + KEY + " was not applied: " + mistake.getMessage());
                broken.add(path);
                keepPrevious(read, id);
            }
        }
        abilities = Map.copyOf(read);
        return List.copyOf(broken);
    }

    private void keepPrevious(Map<String, List<String>> read, String id) {
        List<String> previous = abilities.get(id);
        if (previous != null) {
            read.put(id, previous);
        }
    }

    /** One template's list; no list is no abilities. */
    static List<String> parse(YamlConfiguration yaml) {
        if (!yaml.contains(KEY)) {
            return List.of();
        }
        if (!yaml.isList(KEY)) {
            throw new IllegalArgumentException(KEY + " must be a list of ability ids");
        }
        List<String> ids = new ArrayList<>();
        for (Object entry : yaml.getList(KEY)) {
            if (!(entry instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException(KEY + " must be a list of ability ids");
            }
            String lower = id.trim().toLowerCase(Locale.ROOT);
            if (!ids.contains(lower)) {
                ids.add(lower);
            }
        }
        return List.copyOf(ids);
    }
}
