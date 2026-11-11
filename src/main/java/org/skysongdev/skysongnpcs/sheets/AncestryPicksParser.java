package org.skysongdev.skysongnpcs.sheets;

import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryChoice;
import org.skysongdev.skysongstats.api.AncestryPicks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads ancestry picks typed in a command: one entry per choice, separated by spaces, each
 * {@code <choice>=<pick>}. Choices are numbered from 1, in ancestries.yml order. A stat
 * choice takes stat ids or aliases separated by commas ({@code 1=dex,con}); a package
 * choice takes the package's number, from 1 ({@code 2=1}). Whether the picks fit the
 * ancestry (how many stats, which ones) is left to SkysongStats.
 */
public final class AncestryPicksParser {
    private AncestryPicksParser() {
    }

    /**
     * @throws IllegalArgumentException with a message for the player when an entry is malformed
     */
    public static AncestryPicks parse(Ancestry ancestry, String typed) {
        AncestryPicks.Builder builder = AncestryPicks.builder();
        Set<Integer> seen = new HashSet<>();
        for (String entry : typed.trim().split("\\s+")) {
            if (entry.isEmpty()) {
                continue;
            }
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals == entry.length() - 1) {
                throw new IllegalArgumentException("'" + entry + "' is not <choice>=<pick>");
            }
            int choice = number(entry.substring(0, equals), "choice") - 1;
            if (choice >= ancestry.choices().size()) {
                throw new IllegalArgumentException("The ancestry has " + ancestry.choices().size()
                        + " choice(s), not " + (choice + 1));
            }
            if (!seen.add(choice)) {
                throw new IllegalArgumentException("Choice " + (choice + 1) + " is picked twice");
            }
            String pick = entry.substring(equals + 1);
            if (ancestry.choices().get(choice) instanceof AncestryChoice.Packages packages) {
                int option = number(pick, "package") - 1;
                if (option >= packages.options().size()) {
                    throw new IllegalArgumentException("Choice " + (choice + 1) + " has "
                            + packages.options().size() + " package(s), not " + (option + 1));
                }
                builder.option(choice, option);
            } else {
                String[] stats = Arrays.stream(pick.split(",")).filter(stat -> !stat.isEmpty()).toArray(String[]::new);
                builder.stats(choice, stats);
            }
        }
        return builder.build();
    }

    private static int number(String text, String what) {
        try {
            int number = Integer.parseInt(text);
            if (number >= 1) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // Falls through to the message below.
        }
        throw new IllegalArgumentException("'" + text + "' is not a " + what + " number (1 or more)");
    }
}
