package org.skysongdev.skysongnpcs.picks;

import org.skysongdev.skysongnpcs.sheets.SkysongStatsSheets;
import org.skysongdev.skysongstats.api.Proficiencies;
import org.skysongdev.skysongstats.api.Proficiency;
import org.skysongdev.skysongstats.api.Skill;
import org.skysongdev.skysongstats.api.SkysongStatsApi;
import org.skysongdev.skysongstats.api.Tier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link ProficiencyCatalog} through the SkysongStats API (1.4.0). */
public final class SkysongStatsProficiencyCatalog implements ProficiencyCatalog {

    @Override
    public Optional<String> skillId(String idOrAlias) {
        return api().skills().find(idOrAlias).map(Skill::id);
    }

    @Override
    public List<String> skillIds() {
        return api().skills().all().stream().map(Skill::id).toList();
    }

    @Override
    public Optional<String> tierId(String id) {
        return proficiencies().findTier(id).map(Tier::id);
    }

    @Override
    public List<String> tierIds() {
        return proficiencies().tiers().stream().map(Tier::id).toList();
    }

    @Override
    public Optional<String> proficiencyId(String idOrAlias) {
        return proficiencies().find(idOrAlias).map(Proficiency::id);
    }

    @Override
    public Optional<String> skillOf(String proficiency) {
        return proficiencies().find(proficiency).map(Proficiency::skillId);
    }

    @Override
    public List<String> proficienciesOf(String skill) {
        return api().skills().find(skill)
                .map(found -> proficiencies().ofSkill(found).stream().map(Proficiency::id).toList())
                .orElse(List.of());
    }

    @Override
    public String skillName(String skill) {
        return api().skills().find(skill).map(Skill::name).orElse(skill);
    }

    @Override
    public String proficiencyName(String proficiency) {
        return proficiencies().find(proficiency).map(Proficiency::name).orElse(proficiency);
    }

    @Override
    public String tierName(String tier) {
        return proficiencies().findTier(tier).map(Tier::name).orElse(tier);
    }

    @Override
    public Optional<String> tier(UUID npc, String proficiency) {
        return proficiencies().find(proficiency)
                .flatMap(found -> proficiencies().tier(SkysongStatsSheets.character(npc), found))
                .map(Tier::id);
    }

    @Override
    public boolean setTier(UUID npc, String proficiency, String tier) {
        Optional<Proficiency> found = proficiencies().find(proficiency);
        Optional<Tier> foundTier = proficiencies().findTier(tier);
        if (found.isEmpty() || foundTier.isEmpty()) {
            return false;
        }
        return proficiencies().setTier(SkysongStatsSheets.character(npc), found.get(), foundTier.get()).isPresent();
    }

    private static Proficiencies proficiencies() {
        return api().proficiencies();
    }

    private static SkysongStatsApi api() {
        return SkysongStatsApi.get();
    }
}
