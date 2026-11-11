package org.skysongdev.skysongnpcs.cloning;

import org.junit.jupiter.api.Test;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryAdjustment;
import org.skysongdev.skysongstats.api.AncestryChoice;
import org.skysongdev.skysongstats.api.AncestryPackage;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;
import org.skysongdev.skysongstats.api.CharacterAncestry.Given;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AncestryPicksReaderTest {
    private static final AncestryChoice.Stats TWO_STATS =
            new AncestryChoice.Stats("", 1, 2, List.of(), true, false);
    private static final AncestryChoice.Packages HERITAGE = new AncestryChoice.Packages("", List.of(
            new AncestryPackage("Hill", "STONE", List.of(new AncestryAdjustment("con", 1))),
            new AncestryPackage("Sea", "WATER_BUCKET", List.of(
                    new AncestryAdjustment("dex", 1), new AncestryAdjustment("wis", -1)))), false);

    @Test
    void readsStatPicksAndPackagesFromWhatTheyGave() {
        Ancestry elf = ancestry(TWO_STATS, HERITAGE);
        CharacterAncestry had = had(new Given("str", 2, -1), new Given("int", 1, 0), new Given("cha", 1, 0),
                new Given("dex", 1, 1), new Given("wis", -1, 1));

        assertEquals(Optional.of(AncestryPicks.builder().stats(0, "int", "cha").option(1, 1).build()),
                AncestryPicksReader.read(elf, had));
    }

    @Test
    void anAncestryWithoutChoicesNeedsNoPicks() {
        assertEquals(Optional.of(AncestryPicks.builder().build()),
                AncestryPicksReader.read(ancestry(), had(new Given("str", 2, -1))));
    }

    @Test
    void aChoiceThatChangedSinceCannotBeRead() {
        Ancestry elf = ancestry(TWO_STATS);

        assertEquals(Optional.empty(), AncestryPicksReader.read(elf, had(new Given("int", 1, 0))));
        assertEquals(Optional.empty(), AncestryPicksReader.read(elf,
                had(new Given("int", 2, 0), new Given("cha", 2, 0))));
    }

    @Test
    void aPackageNoOptionGivesCannotBeRead() {
        assertEquals(Optional.empty(), AncestryPicksReader.read(ancestry(HERITAGE), had(new Given("str", 1, 0))));
    }

    private static Ancestry ancestry(AncestryChoice... choices) {
        return new Ancestry("elf", "Elf", "FEATHER", List.of(), "", List.of(), List.of(choices));
    }

    private static CharacterAncestry had(Given... given) {
        return new CharacterAncestry("elf", "Elf", false, List.of(given));
    }
}
