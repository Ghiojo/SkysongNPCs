package org.skysongdev.skysongnpcs.cloning;

import org.skysongdev.skysongnpcs.sheets.NpcSheetService;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps NPC stat sheets in step with Citizens: a copy of a managed NPC gets its own copy of
 * the original's sheet, and a removed NPC's sheet is deleted. Citizens and SkysongWardrobe
 * copy the outfit, inventory and equipment themselves.
 */
public final class NpcCloning {
    private final SheetCopier copier;
    private final NpcSheetService sheets;
    private final Predicate<UUID> guarantee;
    private final Logger logger;

    public NpcCloning(SheetCopier copier, NpcSheetService sheets, Logger logger) {
        this(copier, sheets, npc -> false, logger);
    }

    /**
     * @param guarantee gives the copy a fresh sheet when the original's could not be copied,
     *                  since every managed NPC has one; true when it made one
     */
    public NpcCloning(SheetCopier copier, NpcSheetService sheets, Predicate<UUID> guarantee,
                      Logger logger) {
        this.copier = copier;
        this.sheets = sheets;
        this.guarantee = guarantee;
        this.logger = logger;
    }

    /**
     * A managed NPC was copied and the copy has its record: gives the copy its own copy of
     * the original's sheet, or a new sheet when there is none to copy, and counts the items
     * the copy carries toward it.
     */
    public void copied(UUID original, UUID copy) {
        SheetCopier.CopyResult result;
        try {
            result = copier.copy(original, copy);
        } catch (RuntimeException failed) {
            logger.log(Level.SEVERE, "Could not copy NPC " + original + "'s stat sheet to its copy " + copy, failed);
            fresh(original, copy);
            return;
        }
        switch (result.outcome()) {
            case COPIED -> {
                sheets.recount(copy);
                result.gaps().forEach(gap -> logger.warning("Copied NPC " + original + "'s stat sheet to " + copy
                        + ", but " + gap + "."));
            }
            case TAKEN -> logger.warning("NPC " + copy + " already had a stat sheet, so NPC " + original
                    + "'s was not copied to it.");
            case NO_SHEET -> fresh(original, copy);
        }
    }

    /** The copy got no copied sheet: gives it a new one, so it is never managed without a sheet. */
    private void fresh(UUID original, UUID copy) {
        try {
            if (guarantee.test(copy)) {
                sheets.recount(copy);
                logger.warning("NPC " + original + " had no stat sheet to copy, so its copy " + copy
                        + " got a new one.");
            }
        } catch (RuntimeException failed) {
            logger.log(Level.SEVERE, "NPC " + original + "'s copy " + copy + " has no stat sheet and could not get one",
                    failed);
        }
    }

    /** An NPC was removed from Citizens for good: deletes its sheet, managed or not. */
    public void removed(UUID npc) {
        try {
            copier.delete(npc);
        } catch (RuntimeException failed) {
            logger.log(Level.SEVERE, "Could not delete removed NPC " + npc + "'s stat sheet", failed);
        }
    }
}
