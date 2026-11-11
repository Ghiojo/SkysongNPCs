package org.skysongdev.skysongnpcs.sheets;

import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the rule that every managed NPC has a stat sheet. Adopting an NPC, and copying one,
 * make the sheet through here in the same step; NPCs registered before the rule, or whose
 * SkysongStats character was deleted by hand, get one back on start and after a SkysongStats
 * reload.
 *
 * <p>A missing sheet starts from the NPC's own template when it has one, otherwise from
 * config.yml's {@code sheets.adopt-template}, otherwise with every stat at its stats.yml
 * start value.
 */
public final class SheetGuarantee {
    private final NpcRegistry registry;
    private final NpcSheetService sheets;
    private final Function<String, Optional<NpcTemplate>> templates;
    private final Supplier<String> adoptTemplate;
    private final Logger logger;

    /**
     * @param templates     a template by id
     * @param adoptTemplate the id of the template adopted NPCs start from; blank for none
     */
    public SheetGuarantee(NpcRegistry registry, NpcSheetService sheets,
                          Function<String, Optional<NpcTemplate>> templates, Supplier<String> adoptTemplate,
                          Logger logger) {
        this.registry = registry;
        this.sheets = sheets;
        this.templates = templates;
        this.adoptTemplate = adoptTemplate;
        this.logger = logger;
    }

    /**
     * Gives a managed NPC a stat sheet when it has none.
     *
     * @return true when a sheet was made, false when it had one already
     * @throws IllegalArgumentException when the NPC is not managed
     * @throws IllegalStateException    when SkysongStats would not make the sheet
     */
    public boolean ensure(UUID npc) {
        NpcRecord record = registry.get(npc)
                .orElseThrow(() -> new IllegalArgumentException("NPC " + npc + " is not managed"));
        if (sheets.hasSheet(npc)) {
            return false;
        }
        Optional<NpcTemplate> template = templateOf(record);
        try {
            if (template.isPresent()) {
                try {
                    sheets.create(npc, template.get());
                    return true;
                } catch (IllegalArgumentException staleStat) {
                    // A stat the template names left stats.yml after the template was read.
                    logger.warning("NPC " + npc + "'s stat sheet could not start from template " + template.get().id()
                            + " (" + staleStat.getMessage() + "), so every stat starts at its stats.yml value.");
                }
            }
            sheets.create(npc, Map.of());
            return true;
        } catch (IllegalStateException refused) {
            throw new IllegalStateException("SkysongStats would not make a stat sheet for NPC " + npc
                    + ": its character id " + npc + "/" + StatSheets.PROFILE + " may belong to another plugin", refused);
        }
    }

    /**
     * Gives every managed NPC without a stat sheet one. Problems are logged, never thrown.
     *
     * @return how many sheets were made
     */
    public int repairAll() {
        int made = 0;
        for (NpcRecord record : registry.all()) {
            try {
                if (ensure(record.id())) {
                    made++;
                }
            } catch (RuntimeException failed) {
                logger.log(Level.SEVERE, "NPC " + record.id() + " has no stat sheet and could not get one", failed);
            }
        }
        if (made > 0) {
            logger.info("Gave " + made + " Skysong NPC(s) that had no stat sheet a new one.");
        }
        return made;
    }

    private Optional<NpcTemplate> templateOf(NpcRecord record) {
        Optional<String> id = record.template();
        if (id.isEmpty()) {
            String fallback = adoptTemplate.get();
            id = fallback == null || fallback.isBlank() ? Optional.empty() : Optional.of(fallback.trim().toLowerCase(java.util.Locale.ROOT));
        }
        Optional<NpcTemplate> found = id.flatMap(templates);
        if (id.isPresent() && found.isEmpty()) {
            logger.warning("NPC " + record.id() + "'s stat sheet should start from template " + id.get()
                    + ", which does not exist, so every stat starts at its stats.yml value.");
        }
        return found;
    }
}
