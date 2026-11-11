package org.skysongdev.skysongnpcs.cloning;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Copies and deletes NPC stat sheets in SkysongStats. It is the one place cloning touches
 * SkysongStats, so that once SkysongStats can copy a character itself
 * ({@code characters().copy}), only the implementation changes. {@link RebuildingSheetCopier}
 * is the one for SkysongStats 1.4.0; tests use their own.
 */
public interface SheetCopier {

    /**
     * Gives {@code to} a stat sheet of its own, copied from {@code from}'s. Every modifier is
     * copied once, in the original's order (oldest first), with a new id.
     *
     * @return what happened, with anything the copy could not carry over exactly
     */
    CopyResult copy(UUID from, UUID to);

    /**
     * Deletes an NPC's stat sheet.
     *
     * @return false when it had none
     */
    boolean delete(UUID npc);

    /** What {@link #copy} did. */
    enum Outcome {
        /** The copy has its own sheet now. */
        COPIED,
        /** The original has no sheet, so there was nothing to copy. */
        NO_SHEET,
        /** The copy already had a sheet, and it was left as it was. */
        TAKEN
    }

    /**
     * @param outcome what happened
     * @param gaps    what the copy could not carry over exactly, as sentences for the log;
     *                empty when it is exact
     */
    record CopyResult(Outcome outcome, List<String> gaps) {
        public CopyResult {
            Objects.requireNonNull(outcome, "outcome");
            gaps = List.copyOf(gaps);
        }

        public static CopyResult of(Outcome outcome) {
            return new CopyResult(outcome, List.of());
        }
    }
}
