package org.skysongdev.skysongnpcs.items;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;
import org.skysongdev.skysongnpcs.templates.TemplateRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Makes a managed NPC from a template: spawns the Citizens NPC, gives it a record in state
 * SETUP, and its stat sheet. Placing a blueprint and the API's
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
        return create(template, name, owner, location, null);
    }

    /**
     * M14: makes the NPC from a blueprint item, which the {@link Created} hooks may read
     * (such as its npc_points tag).
     *
     * @param blueprint the item that placed it, or null
     */
    public Optional<NpcRecord> create(NpcTemplate template, @Nullable String name, NpcOwner owner, Location location,
                                      @Nullable ItemStack blueprint) {
        String shownName = name == null || name.isBlank() ? template.name() : name;
        Optional<UUID> spawned = spawner.spawn(template.entityType(), shownName, template.skin(), location);
        if (spawned.isEmpty()) {
            return Optional.empty();
        }
        UUID npc = spawned.get();
        try {
            NpcRecord record = registry.create(npc, owner, template.id());
            // Every Skysong NPC has a stat sheet.
            sheets.create(npc, template);
            for (Created hook : created) {
                hook.created(record, template, blueprint);
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

    // M14: other parts set up a new NPC after it is made.

    private final List<Created> created = new ArrayList<>();

    /** Adds a hook every new NPC goes through, after its record and sheet exist. */
    public void addCreatedHook(Created hook) {
        created.add(hook);
    }

    /** Sets up a new NPC; a hook that throws undoes the NPC like any other failed step. */
    @FunctionalInterface
    public interface Created {
        /** @param blueprint the item that placed it; null when the API made it */
        void created(NpcRecord npc, NpcTemplate template, @Nullable ItemStack blueprint);
    }
}
