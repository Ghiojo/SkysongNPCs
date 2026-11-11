package org.skysongdev.skysongnpcs.picks;

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
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The {@code proficiency-picks:} section of every template in {@code templates/}: the picks
 * an NPC made from the template starts with. The template registry reads the rest of each
 * file; a file it does not know is skipped here too. A section with a mistake keeps its
 * previous version, like the registry does.
 *
 * <pre>
 * proficiency-picks:
 *   - skill: metalworking
 *     tier: proficient
 *     count: 2
 * </pre>
 */
public final class TemplatePicks implements Reloadable {
    static final String KEY = "proficiency-picks";
    /** The source of the picks a template gives. */
    public static final String SOURCE = "template";

    private final File folder;
    private final ProficiencyCatalog catalog;
    private final Logger logger;
    private Map<String, List<Spec>> picks = Map.of();

    public TemplatePicks(File folder, ProficiencyCatalog catalog, Logger logger) {
        this.folder = folder;
        this.catalog = catalog;
        this.logger = logger;
    }

    /** The picks a template gives, empty when it gives none or does not exist. */
    public List<Spec> of(String template) {
        return picks.getOrDefault(template, List.of());
    }

    /** Gives an NPC the picks of its template, with source {@value #SOURCE}. */
    public void grant(ProficiencyPicks to, UUID npc, String template) {
        for (Spec spec : of(template)) {
            to.grant(npc, SOURCE, spec.skill(), spec.tier(), spec.count());
        }
    }

    @Override
    public List<String> reload() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        Map<String, List<Spec>> read = new LinkedHashMap<>();
        List<String> broken = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length());
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
                read.put(id, parse(yaml));
            } catch (IOException | InvalidConfigurationException unreadable) {
                // The template registry reports a file it cannot read; nothing to add here.
                keepPrevious(read, id);
            } catch (IllegalArgumentException mistake) {
                String path = folder.getName() + "/" + file.getName();
                logger.severe(path + ": " + KEY + " was not applied: " + mistake.getMessage());
                broken.add(path);
                keepPrevious(read, id);
            }
        }
        picks = Map.copyOf(read);
        return List.copyOf(broken);
    }

    private void keepPrevious(Map<String, List<Spec>> read, String id) {
        List<Spec> previous = picks.get(id);
        if (previous != null) {
            read.put(id, previous);
        }
    }

    /** Reads one template's section; no section is no picks. */
    List<Spec> parse(YamlConfiguration yaml) {
        if (!yaml.contains(KEY)) {
            return List.of();
        }
        if (!yaml.isList(KEY)) {
            throw new IllegalArgumentException(KEY + " must be a list of skill, tier and count entries");
        }
        List<Spec> specs = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> entry : yaml.getMapList(KEY)) {
            index++;
            String where = KEY + " entry " + index;
            Object skill = entry.get("skill");
            Object tier = entry.get("tier");
            Object count = entry.containsKey("count") ? entry.get("count") : 1;
            if (!(skill instanceof String skillName)) {
                throw new IllegalArgumentException(where + " needs a skill");
            }
            if (!(tier instanceof String tierName)) {
                throw new IllegalArgumentException(where + " needs a tier");
            }
            if (!(count instanceof Integer number) || number < 1) {
                throw new IllegalArgumentException(where + ": count must be a whole number of 1 or more");
            }
            String skillId = catalog.skillId(skillName)
                    .orElseThrow(() -> new IllegalArgumentException(where + ": " + skillName + " is not a SkysongStats skill"));
            String tierId = catalog.tierId(tierName)
                    .orElseThrow(() -> new IllegalArgumentException(where + ": " + tierName + " is not a SkysongStats tier"));
            specs.add(new Spec(skillId, tierId, number));
        }
        if (specs.size() != yaml.getList(KEY).size()) {
            throw new IllegalArgumentException(KEY + " must be a list of skill, tier and count entries");
        }
        return List.copyOf(specs);
    }

    /** Picks a template gives: {@code count} proficiencies of {@code skill}, raised to {@code tier}. */
    public record Spec(String skill, String tier, int count) {
    }
}
