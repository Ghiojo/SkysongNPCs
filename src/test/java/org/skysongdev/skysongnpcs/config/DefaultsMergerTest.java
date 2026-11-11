package org.skysongdev.skysongnpcs.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultsMergerTest {

    @Test
    void addsMissingKeysWithTheirComments() throws InvalidConfigurationException {
        YamlConfiguration server = yaml("prefix: '[Mine]'\n");
        YamlConfiguration defaults = yaml("""
                prefix: '[NPCs]'
                reload:
                  # Sent after a reload.
                  done: Reloaded.
                """);

        List<String> added = DefaultsMerger.mergeMissing(server, defaults);

        assertEquals(List.of("reload.done"), added);
        assertEquals("Reloaded.", server.getString("reload.done"));
        assertEquals(List.of("Sent after a reload."), server.getComments("reload.done"));
    }

    @Test
    void keepsTheServersOwnValuesAndExtraKeys() throws InvalidConfigurationException {
        YamlConfiguration server = yaml("prefix: '[Mine]'\nold-key: kept\n");
        YamlConfiguration defaults = yaml("prefix: '[NPCs]'\n");

        List<String> added = DefaultsMerger.mergeMissing(server, defaults);

        assertTrue(added.isEmpty());
        assertEquals("[Mine]", server.getString("prefix"));
        assertEquals("kept", server.getString("old-key"));
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }
}
