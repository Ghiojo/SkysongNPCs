package org.skysongdev.skysongnpcs.items;

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
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The {@code pickup-item:} of every template in {@code templates/}: the SkysongItems saved
 * item (an id {@code /si loaditem} takes) that NPCs made from the template are picked up
 * into, unless the NPC has its own. The template registry reads the rest of each file. A
 * value with a mistake keeps its previous version. Whether the saved item exists is checked
 * when an item is made, since saved items can change on their own.
 *
 * <pre>
 * pickup-item: npcs/guard_token
 * </pre>
 */
public final class TemplatePickupItems implements Reloadable {
    static final String KEY = "pickup-item";

    private final File folder;
    private final Logger logger;
    private Map<String, String> items = Map.of();

    public TemplatePickupItems(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    /** The saved item a template names; empty when it names none or does not exist. */
    public Optional<String> of(String template) {
        return Optional.ofNullable(items.get(template));
    }

    @Override
    public List<String> reload() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        Map<String, String> read = new LinkedHashMap<>();
        List<String> broken = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length());
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
                parse(yaml).ifPresent(item -> read.put(id, item));
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
        items = Map.copyOf(read);
        return List.copyOf(broken);
    }

    private void keepPrevious(Map<String, String> read, String id) {
        String previous = items.get(id);
        if (previous != null) {
            read.put(id, previous);
        }
    }

    /** One template's saved item; none or blank is none. */
    static Optional<String> parse(YamlConfiguration yaml) {
        if (!yaml.contains(KEY)) {
            return Optional.empty();
        }
        if (!yaml.isString(KEY)) {
            throw new IllegalArgumentException(KEY + " must be the id of a SkysongItems saved item, as /si loaditem takes it");
        }
        String id = yaml.getString(KEY).trim();
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }
}
