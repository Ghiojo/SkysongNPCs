package org.skysongdev.skysongnpcs;

import org.skysongdev.skysongnpcs.cloning.SheetCopier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Copies sheets between {@link FakeStatSheets}' NPCs, and remembers what it was asked. */
public final class FakeSheetCopier implements SheetCopier {
    private final FakeStatSheets sheets;
    /** Every NPC whose sheet was deleted, in order. */
    public final List<UUID> deleted = new ArrayList<>();
    /** What the next copy reports it could not carry over. */
    public List<String> gaps = List.of();
    /** When set, copying and deleting throw it. */
    public RuntimeException failure;

    public FakeSheetCopier(FakeStatSheets sheets) {
        this.sheets = sheets;
    }

    @Override
    public CopyResult copy(UUID from, UUID to) {
        if (failure != null) {
            throw failure;
        }
        if (!sheets.exists(from)) {
            return CopyResult.of(Outcome.NO_SHEET);
        }
        Map<String, Integer> values = new HashMap<>();
        sheets.statIds().forEach(stat -> values.put(stat, sheets.value(from, stat)));
        if (!sheets.create(to, values)) {
            return CopyResult.of(Outcome.TAKEN);
        }
        return new CopyResult(Outcome.COPIED, gaps);
    }

    @Override
    public boolean delete(UUID npc) {
        if (failure != null) {
            throw failure;
        }
        deleted.add(npc);
        return sheets.exists(npc);
    }
}
