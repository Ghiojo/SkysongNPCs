package org.skysongdev.skysongnpcs.cloning;

import org.skysongdev.skysongnpcs.sheets.NpcSheetService;

import java.util.UUID;
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
    private final Logger logger;

    public NpcCloning(SheetCopier copier, NpcSheetService sheets, Logger logger) {
        this.copier = copier;
        this.sheets = sheets;
        this.logger = logger;
    }

    /**
     * A managed NPC was copied and the copy has its record: gives the copy its own sheet
     * when the original has one, and counts the items the copy carries toward it.
     */
    public void copied(UUID original, UUID copy) {
        SheetCopier.CopyResult result;
        try {
            result = copier.copy(original, copy);
        } catch (RuntimeException failed) {
            logger.log(Level.SEVERE, "Could not copy NPC " + original + "'s stat sheet to its copy " + copy, failed);
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
            case NO_SHEET -> {
            }
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
