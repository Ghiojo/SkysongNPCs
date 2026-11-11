package org.skysongdev.skysongnpcs.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Copies the keys a server's file lacks from the copy bundled in the jar, with their comments,
 * so a config.yml or messages.yml from an older version gains every new option and text on
 * update. Keys the server already has are never touched, and keys the jar no longer has stay.
 */
final class DefaultsMerger {
    private DefaultsMerger() {
    }

    /**
     * Adds every key of {@code defaults} that {@code target} lacks.
     *
     * @return the keys that were added, in file order; empty when nothing was missing
     */
    static List<String> mergeMissing(YamlConfiguration target, YamlConfiguration defaults) {
        List<String> added = new ArrayList<>();
        // Deep keys come parent first, so a missing section is created before its children.
        for (String key : defaults.getKeys(true)) {
            if (target.contains(key, true)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                target.createSection(key);
            } else {
                target.set(key, defaults.get(key));
                added.add(key);
            }
            target.setComments(key, defaults.getComments(key));
            target.setInlineComments(key, defaults.getInlineComments(key));
        }
        return added;
    }

}
