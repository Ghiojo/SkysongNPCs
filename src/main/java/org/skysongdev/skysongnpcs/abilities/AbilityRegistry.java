package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.skysongdev.skysongnpcs.sheets.StatSheets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The abilities abilities.yml defines, one section per ability, keyed by its id. On first
 * start the bundled file is written.
 *
 * <p>A file with any mistake (bad YAML, an unknown stat, proficiency or tier, a name of more
 * than one word) is not applied: the abilities read before stay, and the mistake is logged.
 */
public final class AbilityRegistry {
    static final String FILE = "abilities.yml";
    /** Ids are typed in commands and are part of choice sources, which are at most 64 characters. */
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern ONE_WORD = Pattern.compile("\\S+");

    private final File file;
    private final Runnable writeDefault;
    private final StatSheets sheets;
    private final Function<String, Optional<String>> skills;
    private final Logger logger;
    private Map<String, AbilityDefinition> abilities = Map.of();

    /**
     * @param file         abilities.yml
     * @param writeDefault writes the bundled abilities.yml to {@code file}
     * @param sheets       SkysongStats, for the stats, proficiencies and tiers the file names
     * @param skills       a skill's id from its id or alias, empty when there is no such skill
     */
    public AbilityRegistry(File file, Runnable writeDefault, StatSheets sheets,
                           Function<String, Optional<String>> skills, Logger logger) {
        this.file = file;
        this.writeDefault = writeDefault;
        this.sheets = sheets;
        this.skills = skills;
        this.logger = logger;
    }

    public Optional<AbilityDefinition> get(String id) {
        return Optional.ofNullable(abilities.get(id));
    }

    /** Every ability, in the file's order. */
    public Collection<AbilityDefinition> all() {
        return abilities.values();
    }

    /** The ids in the file's order, for sorting an NPC's abilities. */
    public List<String> ids() {
        return List.copyOf(abilities.keySet());
    }

    /**
     * Rereads abilities.yml.
     *
     * @return whether it was applied; false keeps the abilities read before
     */
    public boolean reload() {
        if (!file.exists()) {
            writeDefault.run();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            abilities = parse(yaml, sheets, skills);
        } catch (IOException | InvalidConfigurationException | AbilityException failed) {
            logger.severe(FILE + " was not applied: " + failed.getMessage());
            return false;
        }
        logger.info("Loaded " + abilities.size() + " NPC abilit" + (abilities.size() == 1 ? "y." : "ies."));
        return true;
    }

    /** Reads every ability in the file; the first mistake stops it. */
    static Map<String, AbilityDefinition> parse(ConfigurationSection yaml, StatSheets sheets,
                                                 Function<String, Optional<String>> skills) {
        Map<String, AbilityDefinition> read = new LinkedHashMap<>();
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) {
                throw new AbilityException(id + " must be a section with name, description, icon and effects");
            }
            if (!ID.matcher(id).matches()) {
                throw new AbilityException(id + ": an ability's id must be 1 to 32 lowercase letters, digits or _");
            }
            read.put(id, parseOne(id, section, sheets, skills));
        }
        return Collections.unmodifiableMap(read);
    }

    private static AbilityDefinition parseOne(String id, ConfigurationSection yaml, StatSheets sheets,
                                               Function<String, Optional<String>> skills) {
        String name = yaml.getString("name", capitalised(id));
        String plainName = name.replaceAll("<[^>]*>", "");
        if (!ONE_WORD.matcher(plainName).matches()) {
            throw new AbilityException(id + ".name must be one word");
        }

        String description;
        if (yaml.isList("description")) {
            description = String.join("\n", yaml.getStringList("description"));
        } else {
            description = yaml.getString("description", "");
        }

        String iconName = yaml.getString("icon", "BOOK").trim().toUpperCase(Locale.ROOT);
        Material icon = Material.matchMaterial(iconName);
        if (icon == null || !icon.isItem() || icon.isAir()) {
            throw new AbilityException(id + ".icon " + iconName + " is not an item");
        }

        List<AbilityDefinition.Modifier> modifiers = new ArrayList<>();
        ConfigurationSection modifierSection = section(yaml, id, "modifiers");
        if (modifierSection != null) {
            for (String key : modifierSection.getKeys(false)) {
                if (!modifierSection.isInt(key)) {
                    throw new AbilityException(id + ".modifiers." + key + " must be a whole number");
                }
                modifiers.add(new AbilityDefinition.Modifier(target(id, key, sheets), modifierSection.getInt(key)));
            }
        }

        AbilityDefinition.Points points = AbilityDefinition.Points.NONE;
        ConfigurationSection pointSection = section(yaml, id, "points");
        if (pointSection != null) {
            int amount = positive(pointSection, id + ".points", "amount");
            List<String> eligible = new ArrayList<>();
            for (String stat : pointSection.getStringList("eligible")) {
                eligible.add(sheets.statId(stat)
                        .orElseThrow(() -> new AbilityException(id + ".points.eligible: " + stat + " is not a SkysongStats stat")));
            }
            points = new AbilityDefinition.Points(amount, eligible);
        }

        List<String> tierIds = sheets.tierIds();
        List<AbilityDefinition.Picks> picks = new ArrayList<>();
        if (yaml.contains("picks")) {
            if (!yaml.isList("picks")) {
                throw new AbilityException(id + ".picks must be a list of skill, tier and count");
            }
            int index = 0;
            for (Map<?, ?> entry : yaml.getMapList("picks")) {
                String where = id + ".picks[" + index++ + "]";
                String skill = text(entry.get("skill"));
                String tier = text(entry.get("tier"));
                if (skill == null || skill.isBlank()) {
                    throw new AbilityException(where + " has no skill");
                }
                String skillId = skills.apply(skill)
                        .orElseThrow(() -> new AbilityException(where + ".skill " + skill + " is not a SkysongStats skill"));
                if (tier == null || !tierIds.contains(tier.toLowerCase(Locale.ROOT))) {
                    throw new AbilityException(where + ".tier " + tier + " is not a SkysongStats tier");
                }
                Object count = entry.containsKey("count") ? entry.get("count") : 1;
                if (!(count instanceof Integer number) || number < 1) {
                    throw new AbilityException(where + ".count must be a whole number of 1 or more");
                }
                AbilityDefinition.Picks pick = new AbilityDefinition.Picks(skillId,
                        tier.toLowerCase(Locale.ROOT), number);
                if (picks.stream().anyMatch(other -> other.key().equals(pick.key()))) {
                    throw new AbilityException(where + " repeats " + pick.key() + "; raise its count instead");
                }
                picks.add(pick);
            }
        }

        Map<String, String> tiers = new LinkedHashMap<>();
        ConfigurationSection tierSection = section(yaml, id, "proficiencies");
        if (tierSection != null) {
            List<String> proficiencies = sheets.proficiencyIds();
            for (String key : tierSection.getKeys(false)) {
                String proficiency = key.toLowerCase(Locale.ROOT);
                if (!proficiencies.contains(proficiency)) {
                    throw new AbilityException(id + ".proficiencies." + key + " is not a SkysongStats proficiency");
                }
                String tier = String.valueOf(tierSection.getString(key)).toLowerCase(Locale.ROOT);
                if (!tierIds.contains(tier)) {
                    throw new AbilityException(id + ".proficiencies." + key + ": " + tier + " is not a SkysongStats tier");
                }
                tiers.put(proficiency, tier);
            }
        }
        return new AbilityDefinition(id, name, description, icon, modifiers, points, picks, tiers);
    }

    private static ConfigurationSection section(ConfigurationSection yaml, String id, String key) {
        if (!yaml.contains(key)) {
            return null;
        }
        ConfigurationSection section = yaml.getConfigurationSection(key);
        if (section == null) {
            throw new AbilityException(id + "." + key + " must be a section");
        }
        return section;
    }

    /** A stat id, or {@code max_} and a stat id. */
    private static String target(String id, String key, StatSheets sheets) {
        String lower = key.toLowerCase(Locale.ROOT);
        boolean max = lower.startsWith("max_");
        String stat = sheets.statId(max ? lower.substring(4) : lower)
                .orElseThrow(() -> new AbilityException(id + ".modifiers." + key + " is not a SkysongStats stat or bar max"));
        return max ? "max_" + stat : stat;
    }

    private static int positive(ConfigurationSection section, String where, String key) {
        if (!section.isInt(key) || section.getInt(key) < 1) {
            throw new AbilityException(where + "." + key + " must be a whole number of 1 or more");
        }
        return section.getInt(key);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String capitalised(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    /** A mistake in abilities.yml. */
    static final class AbilityException extends RuntimeException {
        AbilityException(String message) {
            super(message);
        }
    }
}
