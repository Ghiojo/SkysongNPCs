package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.sheet.NpcSheets;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;
import org.skysongdev.skysongnpcs.templates.TemplateRegistry;
import org.skysongdev.skysongstats.api.CharacterId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link NpcSheets} over the plugin's {@link NpcSheetService}. */
final class NpcSheetsImpl implements NpcSheets {
    private final NpcSheetService sheets;
    private final TemplateRegistry templates;

    NpcSheetsImpl(NpcSheetService sheets, TemplateRegistry templates) {
        this.sheets = sheets;
        this.templates = templates;
    }

    @Override
    public boolean hasSheet(@NotNull UUID npc) {
        mainThread();
        return sheets.hasSheet(Objects.requireNonNull(npc, "npc"));
    }

    @Override
    public @NotNull Optional<CharacterId> character(@NotNull UUID npc) {
        return hasSheet(npc) ? Optional.of(idOf(npc)) : Optional.empty();
    }

    @Override
    public @NotNull CharacterId create(@NotNull UUID npc, @NotNull Map<String, Integer> stats) {
        mainThread();
        sheets.create(Objects.requireNonNull(npc, "npc"), Map.copyOf(Objects.requireNonNull(stats, "stats")));
        return idOf(npc);
    }

    @Override
    public @NotNull CharacterId createFromTemplate(@NotNull UUID npc, @NotNull String template) {
        mainThread();
        Objects.requireNonNull(npc, "npc");
        NpcTemplate found = templates.get(Objects.requireNonNull(template, "template"))
                .orElseThrow(() -> new IllegalArgumentException("There is no template " + template));
        sheets.create(npc, found);
        return idOf(npc);
    }

    @Override
    public void recountItems(@NotNull UUID npc) {
        mainThread();
        sheets.recount(Objects.requireNonNull(npc, "npc"));
    }

    private static CharacterId idOf(UUID npc) {
        return new CharacterId(npc, PROFILE);
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
