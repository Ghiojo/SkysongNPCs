package org.skysongdev.skysongnpcs.choices;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The choice keys of template files. */
class TemplateChoiceRegistryTest {
    private static final Function<String, Optional<String>> STATS = name -> switch (name.toLowerCase()) {
        case "str", "strength" -> Optional.of("strength");
        case "hp" -> Optional.of("hp");
        default -> Optional.empty();
    };

    @TempDir
    Path folder;

    @Test
    void readsEveryKey() throws InvalidConfigurationException {
        TemplateChoices choices = parse("""
                points: 4
                stat-points:
                  eligible: [str]
                  max: {strength: 8}
                ancestries: [Elf, human]
                allow-other: false
                """);

        assertEquals(new TemplateChoices("t", 4, List.of("strength"), Map.of("strength", 8), List.of("elf", "human"), false),
                choices);
    }

    @Test
    void leftOutKeysFallBack() throws InvalidConfigurationException {
        TemplateChoices choices = parse("name: Rex\n");

        assertEquals(0, choices.points());
        assertNull(choices.eligible());
        assertNull(choices.max());
        assertEquals(List.of(), choices.ancestries());
        assertTrue(choices.allowOther());
        // Only max given: eligible stays config.yml's.
        assertNull(parse("stat-points:\n  max: {hp: 3}\n").eligible());
    }

    @Test
    void mistakesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> parse("points: -1\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("points: many\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("stat-points:\n  eligible: [luck]\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("stat-points:\n  max: {str: lots}\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("ancestries: elf\n"));
        assertThrows(IllegalArgumentException.class, () -> parse("allow-other: maybe\n"));
    }

    @Test
    void aFileWithAMistakeKeepsItsPreviousVersion() throws IOException {
        TemplateChoiceRegistry registry = new TemplateChoiceRegistry(folder.toFile(), STATS, Logger.getLogger("test"));
        Files.writeString(folder.resolve("guard.yml"), "points: 3\n");
        assertEquals(List.of(), registry.reload());

        Files.writeString(folder.resolve("guard.yml"), "points: -3\n");
        assertEquals(List.of("templates/guard.yml"), registry.reload());
        assertEquals(3, registry.get("guard").points());

        Files.delete(folder.resolve("guard.yml"));
        registry.reload();
        assertEquals(0, registry.get("guard").points());
        assertTrue(registry.get("none").allowOther());
    }

    private static TemplateChoices parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration file = new YamlConfiguration();
        file.loadFromString(yaml);
        return TemplateChoiceRegistry.parse("t", file, STATS);
    }
}
