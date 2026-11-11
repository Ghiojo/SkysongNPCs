package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.sheet.NpcEffects;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.sheets.SheetEffects;
import org.skysongdev.skysongstats.api.ActiveCondition;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;
import org.skysongdev.skysongstats.api.Injury;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link NpcEffects} over the plugin's {@link SheetEffects}. */
final class NpcEffectsImpl implements NpcEffects {
    private final NpcRegistry registry;
    private final NpcSheetService sheets;
    private final SheetEffects effects;

    NpcEffectsImpl(NpcRegistry registry, NpcSheetService sheets, SheetEffects effects) {
        this.registry = registry;
        this.sheets = sheets;
        this.effects = effects;
    }

    @Override
    public @NotNull List<Injury> injuries(@NotNull UUID npc) {
        return effects.injuries(withSheet(npc));
    }

    @Override
    public boolean awardInjury(@NotNull UUID npc, @NotNull String injury) {
        UUID id = withSheet(npc);
        return effects.injuryType(Objects.requireNonNull(injury, "injury"))
                .map(type -> effects.awardInjury(id, type.id())).orElse(false);
    }

    @Override
    public boolean removeInjury(@NotNull UUID npc, @NotNull String injury) {
        return effects.removeInjury(withSheet(npc), Objects.requireNonNull(injury, "injury"));
    }

    @Override
    public boolean discoverInjury(@NotNull UUID npc, @NotNull String injury) {
        return effects.discoverInjury(withSheet(npc), Objects.requireNonNull(injury, "injury"));
    }

    @Override
    public @NotNull List<ActiveCondition> conditions(@NotNull UUID npc) {
        return effects.conditions(withSheet(npc));
    }

    @Override
    public @NotNull Optional<ActiveCondition> awardCondition(@NotNull UUID npc, @NotNull String condition, int level) {
        UUID id = withSheet(npc);
        Objects.requireNonNull(condition, "condition");
        if (level < 1) {
            throw new IllegalArgumentException("A condition's level is at least 1, got " + level);
        }
        return effects.awardCondition(id, condition, level);
    }

    @Override
    public boolean removeCondition(@NotNull UUID npc, @NotNull String condition) {
        return effects.removeCondition(withSheet(npc), Objects.requireNonNull(condition, "condition"));
    }

    @Override
    public @NotNull Optional<CharacterAncestry> ancestry(@NotNull UUID npc) {
        return effects.ancestryOf(withSheet(npc));
    }

    @Override
    public boolean setAncestry(@NotNull UUID npc, @NotNull String ancestry, @NotNull AncestryPicks picks) {
        UUID id = withSheet(npc);
        Objects.requireNonNull(picks, "picks");
        Optional<Ancestry> found = effects.ancestry(Objects.requireNonNull(ancestry, "ancestry"));
        if (found.isEmpty()) {
            return false;
        }
        List<String> problems = effects.ancestryProblems(found.get(), picks);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("The picks don't fit " + found.get().id() + ": "
                    + String.join("; ", problems));
        }
        return effects.setAncestry(id, found.get(), picks);
    }

    @Override
    public boolean removeAncestry(@NotNull UUID npc) {
        return effects.removeAncestry(withSheet(npc));
    }

    /** The NPC, once it is checked to be managed with a sheet, on the main thread. */
    private UUID withSheet(UUID npc) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
        Objects.requireNonNull(npc, "npc");
        if (!registry.isManaged(npc)) {
            throw new IllegalArgumentException("NPC " + npc + " is not managed");
        }
        if (!sheets.hasSheet(npc)) {
            throw new IllegalArgumentException("NPC " + npc + " has no stat sheet");
        }
        return npc;
    }
}
