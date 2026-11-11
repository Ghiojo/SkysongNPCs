package org.skysongdev.skysongnpcs.choices;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The choice parts of a template file (M14), read beside {@link org.skysongdev.skysongnpcs.templates.NpcTemplate}
 * from the same {@code templates/<id>.yml}.
 *
 * @param template   the template's id
 * @param points     the stat points a new NPC starts with
 * @param eligible   which stats its points may go on; null for config.yml's
 * @param max        caps by stat id; null for config.yml's
 * @param ancestries the SkysongStats ancestries its setup offers, by id; empty for no ancestry step
 * @param allowOther whether the setup also offers Other (no ancestry, for animals and creatures)
 */
public record TemplateChoices(String template, int points, @Nullable List<String> eligible,
                              @Nullable Map<String, Integer> max, List<String> ancestries, boolean allowOther) {

    public TemplateChoices {
        eligible = eligible == null ? null : List.copyOf(eligible);
        max = max == null ? null : Map.copyOf(max);
        ancestries = List.copyOf(ancestries);
    }

    /** What a template without a file, or without these keys, gets. */
    public static TemplateChoices none(String template) {
        return new TemplateChoices(template, 0, null, null, List.of(), true);
    }
}
