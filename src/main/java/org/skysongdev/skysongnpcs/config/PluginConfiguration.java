package org.skysongdev.skysongnpcs.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Owns config.yml and messages.yml: writes the defaults on first start, adds the keys a file
 * lacks from the copy in the jar, and swaps in the new copies on {@link #reload()}. Other parts
 * ask it for the current values, so they see a reload without having to be rebuilt.
 *
 * <p>A file with a YAML mistake is not applied: the copy read before it stays in use (or, on
 * first start, the copy bundled in the jar), the file on disk is left untouched, and
 * {@link #reload()} names it so the reloader hears about it.
 */
public final class PluginConfiguration {
    static final String CONFIG_FILE = "config.yml";
    static final String MESSAGES_FILE = "messages.yml";

    private final Plugin plugin;
    private YamlConfiguration config;
    private Messages messages;

    public PluginConfiguration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Writes any missing default file, then reads both.
     *
     * @return the files that could not be read and kept their previous values; empty when all were applied
     */
    public List<String> reload() {
        List<String> broken = new ArrayList<>();

        YamlConfiguration bundledConfig = bundled(CONFIG_FILE);
        YamlConfiguration newConfig = read(CONFIG_FILE, bundledConfig);
        if (newConfig != null) {
            config = newConfig;
        } else {
            broken.add(CONFIG_FILE);
            if (config == null) {
                config = bundledConfig;
            }
        }

        YamlConfiguration bundledMessages = bundled(MESSAGES_FILE);
        YamlConfiguration newMessages = read(MESSAGES_FILE, bundledMessages);
        if (newMessages != null) {
            messages = new Messages(newMessages, bundledMessages, plugin.getLogger());
        } else {
            broken.add(MESSAGES_FILE);
            if (messages == null) {
                messages = new Messages(bundledMessages, bundledMessages, plugin.getLogger());
            }
        }
        return List.copyOf(broken);
    }

    /** config.yml as last read without mistakes. Later milestones read their own sections from it. */
    public FileConfiguration config() {
        return Objects.requireNonNull(config, "reload() has not run yet");
    }

    public Messages messages() {
        return Objects.requireNonNull(messages, "reload() has not run yet");
    }

    /**
     * Reads {@code name} from the data folder, writing the bundled copy first if it is missing,
     * and saves back any keys the bundled copy has and the file lacks.
     *
     * @return the file's contents, or null when it has a mistake
     */
    private YamlConfiguration read(String name, YamlConfiguration defaults) {
        File file = file(name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException failed) {
            plugin.getLogger().log(Level.SEVERE, name + " could not be read, so it was not applied: "
                    + failed.getMessage());
            return null;
        }
        List<String> added = DefaultsMerger.mergeMissing(loaded, defaults);
        if (!added.isEmpty()) {
            try {
                loaded.save(file);
                plugin.getLogger().info("Added " + added.size() + " missing key(s) to " + name + ": "
                        + String.join(", ", added));
            } catch (IOException failed) {
                // The merged keys are still used from memory; only the file stays as it was.
                plugin.getLogger().log(Level.WARNING, "Could not save the missing keys to " + name + ".", failed);
            }
        }
        return loaded;
    }

    private File file(String name) {
        return new File(plugin.getDataFolder(), name);
    }

    private YamlConfiguration bundled(String name) {
        InputStream resource = plugin.getResource(name);
        if (resource == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }
}
