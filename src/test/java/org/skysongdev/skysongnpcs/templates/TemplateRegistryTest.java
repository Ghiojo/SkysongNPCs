package org.skysongdev.skysongnpcs.templates;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.FakeStatSheets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading templates/*.yml. Stats come from {@link FakeStatSheets}: strength (alias str) and hp. */
class TemplateRegistryTest {
    @TempDir
    Path dataFolder;
    private File folder;
    private int examplesWritten;
    private TemplateRegistry templates;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        folder = dataFolder.resolve("templates").toFile();
        templates = new TemplateRegistry(folder, () -> {
            examplesWritten++;
            folder.mkdirs();
        }, new FakeStatSheets()::statId, Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void writesTheExamplesOnlyWhenTheFolderIsMissing() {
        templates.reload();
        templates.reload();

        assertEquals(1, examplesWritten);
    }

    @Test
    void readsEveryPart() throws IOException {
        write("guard_dog", """
                entity-type: wolf
                name: "Rex"
                skin: ""
                sheet: true
                stats:
                  str: 3
                  hp: 12
                """);

        assertEquals(List.of(), templates.reload());

        NpcTemplate dog = templates.get("guard_dog").orElseThrow();
        assertEquals(EntityType.WOLF, dog.entityType());
        assertEquals("Rex", dog.name());
        assertNull(dog.skin());
        assertTrue(dog.sheet());
        assertEquals(Map.of("strength", 3, "hp", 12), dog.stats());
    }

    @Test
    void leftOutPartsTakeTheirDefaults() throws IOException {
        write("clerk", "skin: Notch\n");

        templates.reload();

        NpcTemplate clerk = templates.get("clerk").orElseThrow();
        assertEquals(EntityType.PLAYER, clerk.entityType());
        assertEquals("clerk", clerk.name());
        assertEquals("Notch", clerk.skin());
        assertTrue(clerk.sheet());
        assertEquals(Map.of(), clerk.stats());
    }

    @Test
    void aFileWithAMistakeIsNamedAndNotLoaded() throws IOException {
        write("ok", "name: Fine\n");
        write("bad", "stats:\n  luck: 2\n");

        assertEquals(List.of("templates/bad.yml"), templates.reload());
        assertTrue(templates.get("ok").isPresent());
        assertFalse(templates.get("bad").isPresent());
    }

    @Test
    void aBrokenFileKeepsItsPreviousVersionOnReload() throws IOException {
        write("guard", "name: Old\n");
        templates.reload();
        write("guard", "entity-type: DRAGONISH\n");

        assertEquals(List.of("templates/guard.yml"), templates.reload());
        assertEquals("Old", templates.get("guard").orElseThrow().name());
    }

    @Test
    void aDeletedFileTakesItsTemplateAway() throws IOException {
        write("guard", "name: Guard\n");
        templates.reload();
        Files.delete(folder.toPath().resolve("guard.yml"));

        templates.reload();

        assertFalse(templates.get("guard").isPresent());
    }

    @Test
    void refusesWrongValues() throws IOException {
        write("not_number", "stats:\n  hp: lots\n");
        write("twice", "stats:\n  str: 1\n  strength: 2\n");
        write("sheet_word", "sheet: maybe\n");
        write("Capital", "name: Capital\n");

        assertEquals(List.of("templates/Capital.yml", "templates/not_number.yml", "templates/sheet_word.yml",
                "templates/twice.yml"), templates.reload());
        assertTrue(templates.all().isEmpty());
    }

    private void write(String id, String yaml) throws IOException {
        folder.mkdirs();
        Files.writeString(folder.toPath().resolve(id + ".yml"), yaml);
    }
}
