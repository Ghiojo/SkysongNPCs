package org.skysongdev.skysongnpcs;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.characters.CharacterLookup;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.storage.DatabaseSettings;
import org.skysongdev.skysongnpcs.storage.InMemoryGrantStore;
import org.skysongdev.skysongnpcs.storage.InMemoryNpcStore;
import org.skysongdev.skysongnpcs.storage.Stores;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The plugin with its records in memory, and Citizens and SkysongStats replaced by fakes the
 * test controls, since neither plugin is on the test classpath. The same approach as
 * SkysongStats' OfflineSkysongStats.
 */
public class OfflineSkysongNPCs extends SkysongNPCs {
    private final FakeNpcLookup fakeCitizens = new FakeNpcLookup();
    private final FakeCharacterLookup fakeCharacters = new FakeCharacterLookup();

    /** Loads it with the commands and permissions of the real plugin.yml. */
    public static OfflineSkysongNPCs load() {
        return MockBukkit.loadWith(OfflineSkysongNPCs.class, description());
    }

    // Only the main line changes. Going through YamlConfiguration would split the
    // dotted permission names into nested sections and lose them.
    private static PluginDescriptionFile description() {
        try (InputStream in = Objects.requireNonNull(
                SkysongNPCs.class.getClassLoader().getResourceAsStream("plugin.yml"), "plugin.yml")) {
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceFirst("(?m)^main: .*$", "main: " + OfflineSkysongNPCs.class.getName());
            return new PluginDescriptionFile(new StringReader(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InvalidDescriptionException e) {
            throw new IllegalStateException("plugin.yml is broken", e);
        }
    }

    @Override
    protected Stores openStores(DatabaseSettings settings) {
        return new Stores(new InMemoryNpcStore(), new InMemoryGrantStore());
    }

    @Override
    protected NpcLookup hookCitizens(NpcRegistry registry) {
        return fakeCitizens;
    }

    @Override
    protected CharacterLookup hookCharacters() {
        return fakeCharacters;
    }

    public FakeNpcLookup fakeCitizens() {
        return fakeCitizens;
    }

    public FakeCharacterLookup fakeCharacters() {
        return fakeCharacters;
    }
}
