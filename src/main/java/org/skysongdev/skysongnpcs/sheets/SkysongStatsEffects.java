package org.skysongdev.skysongnpcs.sheets;

import org.skysongdev.skysongstats.api.ActiveCondition;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;
import org.skysongdev.skysongstats.api.ConditionType;
import org.skysongdev.skysongstats.api.Injury;
import org.skysongdev.skysongstats.api.InjuryType;
import org.skysongdev.skysongstats.api.SkysongStatsApi;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@link SheetEffects} through the SkysongStats API (1.4.0) on the NPC's plugin character. */
public final class SkysongStatsEffects implements SheetEffects {

    @Override
    public List<InjuryType> injuryTypes() {
        return stats().injuries().types();
    }

    @Override
    public Optional<InjuryType> injuryType(String id) {
        return stats().injuries().type(id);
    }

    @Override
    public List<Injury> injuries(UUID npc) {
        return stats().injuries().current(SkysongStatsSheets.character(npc));
    }

    @Override
    public Set<String> knownInjuries(UUID npc) {
        return stats().injuries().known(SkysongStatsSheets.character(npc));
    }

    @Override
    public boolean awardInjury(UUID npc, String injury) {
        return stats().injuries().award(SkysongStatsSheets.character(npc), injury).isPresent();
    }

    @Override
    public boolean removeInjury(UUID npc, String injury) {
        return stats().injuries().remove(SkysongStatsSheets.character(npc), injury);
    }

    @Override
    public boolean discoverInjury(UUID npc, String injury) {
        return stats().injuries().discover(SkysongStatsSheets.character(npc), injury);
    }

    @Override
    public boolean forgetInjury(UUID npc, String injury) {
        return stats().injuries().forget(SkysongStatsSheets.character(npc), injury);
    }

    @Override
    public List<ConditionType> conditionTypes() {
        return stats().conditions().types();
    }

    @Override
    public Optional<ConditionType> conditionType(String id) {
        return stats().conditions().type(id);
    }

    @Override
    public List<ActiveCondition> conditions(UUID npc) {
        return stats().conditions().current(SkysongStatsSheets.character(npc));
    }

    @Override
    public Optional<ActiveCondition> awardCondition(UUID npc, String condition, int level) {
        return stats().conditions().award(SkysongStatsSheets.character(npc), condition, level);
    }

    @Override
    public boolean removeCondition(UUID npc, String condition) {
        return stats().conditions().remove(SkysongStatsSheets.character(npc), condition);
    }

    @Override
    public List<Ancestry> ancestries() {
        return stats().ancestries().all();
    }

    @Override
    public Optional<Ancestry> ancestry(String idOrName) {
        return stats().ancestries().ancestry(idOrName);
    }

    @Override
    public List<String> ancestryProblems(Ancestry ancestry, AncestryPicks picks) {
        return stats().ancestries().problems(ancestry, picks);
    }

    @Override
    public Optional<CharacterAncestry> ancestryOf(UUID npc) {
        return stats().ancestries().of(SkysongStatsSheets.character(npc));
    }

    @Override
    public boolean setAncestry(UUID npc, Ancestry ancestry, AncestryPicks picks) {
        return stats().ancestries().set(SkysongStatsSheets.character(npc), ancestry, picks).isPresent();
    }

    @Override
    public boolean setAncestry(UUID npc, Ancestry ancestry) {
        return stats().ancestries().set(SkysongStatsSheets.character(npc), ancestry).isPresent();
    }

    @Override
    public boolean chooseAncestry(UUID npc, AncestryPicks picks) {
        return stats().ancestries().choose(SkysongStatsSheets.character(npc), picks).isPresent();
    }

    @Override
    public boolean rechooseAncestry(UUID npc) {
        return stats().ancestries().rechoose(SkysongStatsSheets.character(npc)).isPresent();
    }

    @Override
    public boolean removeAncestry(UUID npc) {
        return stats().ancestries().remove(SkysongStatsSheets.character(npc)).isPresent();
    }

    private static SkysongStatsApi stats() {
        return SkysongStatsApi.get();
    }
}
