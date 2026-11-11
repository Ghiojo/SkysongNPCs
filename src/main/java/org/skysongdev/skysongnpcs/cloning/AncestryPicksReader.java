package org.skysongdev.skysongnpcs.cloning;

import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryAdjustment;
import org.skysongdev.skysongstats.api.AncestryChoice;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Works out which picks a character made for its ancestry's choices, from what each choice
 * gave it. SkysongStats 1.4.0 keeps what the choices gave but not the picks themselves, so
 * copying an ancestry has to read them back.
 */
final class AncestryPicksReader {
    private AncestryPicksReader() {
    }

    /**
     * The picks behind what the character's choices gave.
     *
     * @param ancestry the ancestry as ancestries.yml has it now
     * @param had      the character's ancestry, with its choices made
     * @return the picks; empty when they can't be told from what was given, for example when
     *         ancestries.yml changed the choice since, or two packages give the same thing
     *         differently
     */
    static Optional<AncestryPicks> read(Ancestry ancestry, CharacterAncestry had) {
        AncestryPicks.Builder picks = AncestryPicks.builder();
        for (int choice = 0; choice < ancestry.choices().size(); choice++) {
            List<CharacterAncestry.Given> given = new ArrayList<>();
            for (CharacterAncestry.Given adjustment : had.adjustments()) {
                if (adjustment.choice() == choice) {
                    given.add(adjustment);
                }
            }
            switch (ancestry.choices().get(choice)) {
                case AncestryChoice.Stats stats -> {
                    if (given.size() != stats.picks()
                            || given.stream().anyMatch(adjustment -> adjustment.amount() != stats.amount())) {
                        return Optional.empty();
                    }
                    picks.stats(choice, given.stream().map(CharacterAncestry.Given::stat).toArray(String[]::new));
                }
                case AncestryChoice.Packages packages -> {
                    int option = matchingOption(packages, given);
                    if (option < 0) {
                        return Optional.empty();
                    }
                    picks.option(choice, option);
                }
            }
        }
        return Optional.of(picks.build());
    }

    /** The first package that gives exactly what was given, or -1. */
    private static int matchingOption(AncestryChoice.Packages packages, List<CharacterAncestry.Given> given) {
        Map<String, Integer> wanted = new HashMap<>();
        given.forEach(adjustment -> wanted.merge(adjustment.stat(), adjustment.amount(), Integer::sum));
        for (int option = 0; option < packages.options().size(); option++) {
            Map<String, Integer> gives = new HashMap<>();
            for (AncestryAdjustment adjustment : packages.options().get(option).adjustments()) {
                gives.merge(adjustment.stat(), adjustment.amount(), Integer::sum);
            }
            if (gives.equals(wanted)) {
                return option;
            }
        }
        return -1;
    }
}
