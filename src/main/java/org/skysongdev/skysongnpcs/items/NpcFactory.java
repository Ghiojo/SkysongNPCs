package org.skysongdev.skysongnpcs.items;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;
import org.skysongdev.skysongnpcs.templates.TemplateRegistry;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Makes a managed NPC from a template: spawns the Citizens NPC, gives it a record in state
 * SETUP, and its stat sheet when the template has one. Placing a blueprint and the API's
 * {@code npcs().create} both come here. If any step fails, the steps before it are undone, so
 * a half-made NPC never stays in the world.
 */
public final class NpcFactory {
    private final TemplateRegistry templates;
    private final NpcRegistry registry;
    private final NpcSpawner spawner;
    private final SheetMaker sheets;

    public NpcFactory(TemplateRegistry templates, NpcRegistry registry, NpcSpawner spawner, SheetMaker sheets) {
        this.templates = templates;
        this.registry = registry;
        this.spawner = spawner;
        this.sheets = sheets;
    }

    /** The template with this id, ignoring case. */
    public Optional<NpcTemplate> template(String id) {
        return templates.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Makes the NPC.
     *
     * @param name its name, or null for the template's
     * @return the new record; empty when Citizens could not spawn it there
     * @throws RuntimeException when the record or the sheet could not be made; the NPC is gone again
     */
    public Optional<NpcRecord> create(NpcTemplate template, @Nullable String name, NpcOwner owner, Location location) {
        String shownName = name == null || name.isBlank() ? template.name() : name;
        Optional<UUID> spawned = spawner.spawn(template.entityType(), shownName, template.skin(), location);
        if (spawned.isEmpty()) {
            return Optional.empty();
        }
        UUID npc = spawned.get();
        try {
            NpcRecord record = registry.create(npc, owner, template.id());
            if (template.sheet()) {
                sheets.create(npc, template);
            }
            return Optional.of(record);
        } catch (RuntimeException failed) {
            spawner.destroy(npc);
            // Citizens' remove event already dropped the record on a server; this covers the rest.
            registry.removed(npc);
            throw failed;
        }
    }

    /** Gives a new NPC its stat sheet; M11's NpcSheetService on a server. */
    @FunctionalInterface
    public interface SheetMaker {
        void create(UUID npc, NpcTemplate template);
    }
}
