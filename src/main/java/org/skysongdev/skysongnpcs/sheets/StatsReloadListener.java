package org.skysongdev.skysongnpcs.sheets;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.skysongdev.skysongstats.api.event.SkysongStatsReloadEvent;

/** After {@code /ssreload}, stats.yml may count other item tags, so every NPC's items are counted again. */
public final class StatsReloadListener implements Listener {
    private final NpcSheetService sheets;

    public StatsReloadListener(NpcSheetService sheets) {
        this.sheets = sheets;
    }

    @EventHandler
    public void onStatsReload(SkysongStatsReloadEvent event) {
        sheets.recountAll(true);
    }
}
