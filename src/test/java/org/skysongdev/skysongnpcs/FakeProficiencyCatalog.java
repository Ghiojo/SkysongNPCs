package org.skysongdev.skysongnpcs;

import org.skysongdev.skysongnpcs.picks.ProficiencyCatalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Skills, proficiencies and tiers without SkysongStats. Skills: {@code combat} (alias
 * {@code fight}) with {@code swords}, {@code axes}, {@code bows}; {@code metalworking} with
 * {@code smithing}. Tiers: {@code untrained}, {@code proficient}, {@code expert}. An NPC has
 * tiers once it has a sheet, every proficiency starting untrained.
 */
public final class FakeProficiencyCatalog implements ProficiencyCatalog {
    private static final Map<String, List<String>> SKILLS = new LinkedHashMap<>();

    static {
        SKILLS.put("combat", List.of("swords", "axes", "bows"));
        SKILLS.put("metalworking", List.of("smithing"));
    }

    private final Map<UUID, Map<String, String>> tiers = new HashMap<>();
    private final Set<UUID> sheets = new HashSet<>();
    private final Predicate<UUID> hasSheet;

    /** NPCs have tiers once {@link #giveSheet} is called. */
    public FakeProficiencyCatalog() {
        this.hasSheet = sheets::contains;
    }

    /** NPCs have tiers when {@code hasSheet} says they have a sheet. */
    public FakeProficiencyCatalog(Predicate<UUID> hasSheet) {
        this.hasSheet = hasSheet;
    }

    public void giveSheet(UUID npc) {
        sheets.add(npc);
    }

    @Override
    public Optional<String> skillId(String idOrAlias) {
        String id = idOrAlias.equalsIgnoreCase("fight") ? "combat" : idOrAlias.toLowerCase();
        return SKILLS.containsKey(id) ? Optional.of(id) : Optional.empty();
    }

    @Override
    public List<String> skillIds() {
        return List.copyOf(SKILLS.keySet());
    }

    @Override
    public Optional<String> tierId(String id) {
        return tierIds().contains(id.toLowerCase()) ? Optional.of(id.toLowerCase()) : Optional.empty();
    }

    @Override
    public List<String> tierIds() {
        return List.of("untrained", "proficient", "expert");
    }

    @Override
    public Optional<String> proficiencyId(String idOrAlias) {
        return skillOf(idOrAlias.toLowerCase()).map(skill -> idOrAlias.toLowerCase());
    }

    @Override
    public Optional<String> skillOf(String proficiency) {
        return SKILLS.entrySet().stream().filter(entry -> entry.getValue().contains(proficiency))
                .map(Map.Entry::getKey).findFirst();
    }

    @Override
    public List<String> proficienciesOf(String skill) {
        return SKILLS.getOrDefault(skill, List.of());
    }

    @Override
    public String skillName(String skill) {
        return capital(skill);
    }

    @Override
    public String proficiencyName(String proficiency) {
        return capital(proficiency);
    }

    @Override
    public String tierName(String tier) {
        return capital(tier);
    }

    @Override
    public Optional<String> tier(UUID npc, String proficiency) {
        if (!hasSheet.test(npc) || skillOf(proficiency).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tiers.getOrDefault(npc, Map.of()).getOrDefault(proficiency, "untrained"));
    }

    @Override
    public boolean setTier(UUID npc, String proficiency, String tier) {
        if (!hasSheet.test(npc) || skillOf(proficiency).isEmpty() || !tierIds().contains(tier)) {
            return false;
        }
        tiers.computeIfAbsent(npc, ignored -> new HashMap<>()).put(proficiency, tier);
        return true;
    }

    private static String capital(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
