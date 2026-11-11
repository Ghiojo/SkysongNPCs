package org.skysongdev.skysongnpcs.picks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.skysongdev.skysongnpcs.FakeProficiencyCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The proficiency-picks: section of templates. */
class TemplatePicksTest {
    @TempDir
    Path folder;

    private TemplatePicks picks() {
        return new TemplatePicks(folder.toFile(), new FakeProficiencyCatalog(), Logger.getLogger("test"));
    }

    @Test
    void readsEntriesWithAliasesAndADefaultCount() throws IOException {
        Files.writeString(folder.resolve("guard.yml"), """
                proficiency-picks:
                  - skill: fight
                    tier: expert
                    count: 2
                  - skill: metalworking
                    tier: proficient
                """);
        Files.writeString(folder.resolve("dog.yml"), "name: Dog\n");
        TemplatePicks picks = picks();

        assertEquals(List.of(), picks.reload());
        assertEquals(List.of(new TemplatePicks.Spec("combat", "expert", 2),
                new TemplatePicks.Spec("metalworking", "proficient", 1)), picks.of("guard"));
        assertEquals(List.of(), picks.of("dog"));
        assertEquals(List.of(), picks.of("missing"));
    }

    @Test
    void aMistakeKeepsThePreviousVersion() throws IOException {
        Path file = folder.resolve("guard.yml");
        Files.writeString(file, "proficiency-picks:\n  - skill: combat\n    tier: expert\n");
        TemplatePicks picks = picks();
        picks.reload();

        for (String broken : List.of(
                "proficiency-picks:\n  - skill: dancing\n    tier: expert\n",
                "proficiency-picks:\n  - skill: combat\n    tier: godlike\n",
                "proficiency-picks:\n  - skill: combat\n    tier: expert\n    count: 0\n",
                "proficiency-picks:\n  - tier: expert\n",
                "proficiency-picks: combat\n",
                "proficiency-picks:\n  - combat\n")) {
            Files.writeString(file, broken);
            assertEquals(List.of(folder.getFileName() + "/guard.yml"), picks.reload(), broken);
            assertEquals(List.of(new TemplatePicks.Spec("combat", "expert", 1)), picks.of("guard"), broken);
        }
    }
}
