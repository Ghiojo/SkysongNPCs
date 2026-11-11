package org.skysongdev.skysongnpcs.config;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.SkysongNPCs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigurationTest {
    private SkysongNPCs plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(SkysongNPCs.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void writesBothFilesOnFirstStart() {
        assertTrue(Files.exists(file("config.yml")));
        assertTrue(Files.exists(file("messages.yml")));
    }

    @Test
    void addsMissingTextsToTheFileAndKeepsEditedOnes() throws IOException {
        Files.writeString(file("messages.yml"), "prefix: '[Mine] '\n");

        List<String> broken = plugin.configuration().reload();

        assertTrue(broken.isEmpty());
        String onDisk = Files.readString(file("messages.yml"));
        assertTrue(onDisk.contains("[Mine] "), onDisk);
        assertTrue(onDisk.contains("done:"), onDisk);
        assertEquals("[Mine] Reloaded config.yml and messages.yml.", plain("reload.done"));
    }

    @Test
    void aBrokenFileKeepsItsPreviousValuesAndIsNotOverwritten() throws IOException {
        Files.writeString(file("messages.yml"), "prefix: '[Mine] '\n");
        plugin.configuration().reload();
        String broken = "prefix: '[Oops]\n  bad: [\n";
        Files.writeString(file("messages.yml"), broken);

        List<String> result = plugin.configuration().reload();

        assertEquals(List.of("messages.yml"), result);
        assertTrue(plain("reload.done").startsWith("[Mine] "));
        assertEquals(broken, Files.readString(file("messages.yml")));
    }

    private Path file(String name) {
        return plugin.getDataFolder().toPath().resolve(name);
    }

    private String plain(String key) {
        return PlainTextComponentSerializer.plainText().serialize(plugin.configuration().messages().get(key));
    }
}
