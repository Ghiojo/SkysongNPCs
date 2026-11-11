package org.skysongdev.skysongnpcs.sheets;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.skysongdev.skysongstats.api.event.SkysongStatsReloadEvent;

/**
 * After {@code /ssreload}, stats.yml may count other item tags, so every NPC's items are
 * counted again. Managed NPCs whose sheet is gone (deleted in SkysongStats) get a new one first.
 */
public final class StatsReloadListener implements Listener {
    private final NpcSheetService sheets;
    private final Runnable repair;

    public StatsReloadListener(NpcSheetService sheets) {
        this(sheets, () -> {
        });
    }

    /** @param repair gives managed NPCs without a sheet one, before their items are counted */
    public StatsReloadListener(NpcSheetService sheets, Runnable repair) {
        this.sheets = sheets;
        this.repair = repair;
    }

    @EventHandler
    public void onStatsReload(SkysongStatsReloadEvent event) {
        repair.run();
        sheets.recountAll(true);
    }
}
