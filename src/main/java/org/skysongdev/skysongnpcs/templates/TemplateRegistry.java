package org.skysongdev.skysongnpcs.templates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.skysongdev.skysongnpcs.config.Reloadable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Every template in the plugin's {@code templates/} folder, one file per template, named
 * after its id. On first start (no folder yet) the bundled examples are written there.
 *
 * <p>A file with a mistake (bad YAML, an unknown entity type or stat, a value of the wrong
 * kind) is not applied: on a reload the template keeps the version read before, and the file
 * is logged and named in {@link #reload()}'s answer. A file that is deleted takes its
 * template away on the next reload.
 */
public final class TemplateRegistry implements Reloadable {
    static final String FOLDER = "templates";
    /** Ids are stored in snpc_npcs.template, a VARCHAR(64), and typed in commands. */
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");

    private final File folder;
    private final Runnable writeExamples;
    private final Function<String, Optional<String>> statIds;
    private final Logger logger;
    private Map<String, NpcTemplate> templates = Map.of();

    /**
     * @param folder        the templates folder
     * @param writeExamples writes the bundled example templates into {@code folder}
     * @param statIds       a SkysongStats stat's id from its id or alias, empty when there is no such stat
     */
    public TemplateRegistry(File folder, Runnable writeExamples, Function<String, Optional<String>> statIds,
                            Logger logger) {
        this.folder = folder;
        this.writeExamples = writeExamples;
        this.statIds = statIds;
        this.logger = logger;
    }

    public Optional<NpcTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    /** Every template, by id. */
    public Collection<NpcTemplate> all() {
        return templates.values();
    }

    @Override
    public List<String> reload() {
        if (!folder.exists()) {
            writeExamples.run();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        Map<String, NpcTemplate> read = new LinkedHashMap<>();
        List<String> broken = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length());
            try {
                read.put(id, read(id, file));
            } catch (TemplateException failed) {
                String path = FOLDER + "/" + file.getName();
                logger.severe(path + " was not applied: " + failed.getMessage());
                broken.add(path);
                NpcTemplate previous = templates.get(id);
                if (previous != null) {
                    read.put(id, previous);
                }
            }
        }
        templates = Map.copyOf(read);
        logger.info("Loaded " + templates.size() + " NPC template(s).");
        return List.copyOf(broken);
    }

    private NpcTemplate read(String id, File file) {
        if (!ID.matcher(id).matches()) {
            throw new TemplateException("a template's file name must be 1 to 64 lowercase letters, digits or _");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new TemplateException(e.getMessage());
        }
        return parse(id, yaml, statIds);
    }

    /** Reads one template's contents. */
    static NpcTemplate parse(String id, ConfigurationSection yaml, Function<String, Optional<String>> statIds) {
        String typeName = yaml.getString("entity-type", "PLAYER").trim().toUpperCase(Locale.ROOT);
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException unknown) {
            throw new TemplateException("entity-type " + typeName + " is not a Minecraft entity type");
        }
        if (type == EntityType.UNKNOWN) {
            throw new TemplateException("entity-type cannot be UNKNOWN");
        }

        String name = yaml.getString("name", id);
        if (name == null || name.isBlank()) {
            throw new TemplateException("name is empty");
        }
        String skin = yaml.getString("skin");
        if (skin != null && skin.isBlank()) {
            skin = null;
        }
        if (yaml.contains("sheet") && !yaml.isBoolean("sheet")) {
            throw new TemplateException("sheet must be true or false");
        }
        boolean sheet = yaml.getBoolean("sheet", true);

        Map<String, Integer> stats = new LinkedHashMap<>();
        if (yaml.contains("stats")) {
            ConfigurationSection section = yaml.getConfigurationSection("stats");
            if (section == null) {
                throw new TemplateException("stats must be a list of stat: value lines");
            }
            for (String key : section.getKeys(false)) {
                if (!section.isInt(key)) {
                    throw new TemplateException("stats." + key + " must be a whole number");
                }
                String stat = statIds.apply(key)
                        .orElseThrow(() -> new TemplateException("stats." + key + " is not a SkysongStats stat"));
                if (stats.containsKey(stat)) {
                    throw new TemplateException("stats." + key + " names " + stat + " a second time");
                }
                stats.put(stat, section.getInt(key));
            }
        }
        return new NpcTemplate(id, type, name, skin, sheet, stats);
    }

    /** A mistake in a template file. */
    static final class TemplateException extends RuntimeException {
        TemplateException(String message) {
            super(message);
        }
    }
}
