package org.skysongdev.skysongnpcs.sheets;

import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.FakeSheetEffects;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryPicks;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AncestryPicksParserTest {
    private final Ancestry elf = new FakeSheetEffects().ancestry("elf").orElseThrow();

    @Test
    void readsStatsAndPackagesNumberedFromOne() {
        AncestryPicks picks = AncestryPicksParser.parse(elf, " 2=2   1=str,con ");

        assertEquals(List.of("str", "con"), picks.stats(0).orElseThrow());
        assertEquals(OptionalInt.of(1), picks.option(1));
    }

    @Test
    void nothingTypedIsNoPicks() {
        assertEquals(AncestryPicks.none(), AncestryPicksParser.parse(elf, ""));
    }

    @Test
    void refusesMalformedEntries() {
        assertEquals("'x' is not <choice>=<pick>",
                assertThrows(IllegalArgumentException.class, () -> AncestryPicksParser.parse(elf, "x")).getMessage());
        assertEquals("'0' is not a choice number (1 or more)",
                assertThrows(IllegalArgumentException.class, () -> AncestryPicksParser.parse(elf, "0=str")).getMessage());
        assertEquals("The ancestry has 2 choice(s), not 3",
                assertThrows(IllegalArgumentException.class, () -> AncestryPicksParser.parse(elf, "3=str")).getMessage());
        assertEquals("Choice 1 is picked twice",
                assertThrows(IllegalArgumentException.class, () -> AncestryPicksParser.parse(elf, "1=str 1=con")).getMessage());
        assertEquals("'woodland' is not a package number (1 or more)",
                assertThrows(IllegalArgumentException.class, () -> AncestryPicksParser.parse(elf, "2=woodland")).getMessage());
    }
}
