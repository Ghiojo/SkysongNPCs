package org.skysongdev.skysongnpcs.sheets;

import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Managed NPCs' stat sheets: creating them from a template's statline, and keeping the items
 * each NPC carries counted toward its item-tag stats (Arcrot, Fictis...).
 *
 * <p>SkysongStats does not save item counts and cannot see Citizens, so {@link #recountAll}
 * runs on enable, after a SkysongStats reload, and on a timer. Unchanged items are not sent
 * again, so the timer costs little.
 */
public final class NpcSheetService {
    private final NpcRegistry registry;
    private final StatSheets sheets;
    private final CarriedItems items;
    /** What each NPC was last counted with. */
    private final Map<UUID, List<ItemStack>> counted = new HashMap<>();

    public NpcSheetService(NpcRegistry registry, StatSheets sheets, CarriedItems items) {
        this.registry = registry;
        this.sheets = sheets;
        this.items = items;
    }

    /** The SkysongStats side, for commands that edit a sheet. */
    public StatSheets stats() {
        return sheets;
    }

    public boolean hasSheet(UUID npc) {
        return sheets.exists(npc);
    }

    /**
     * Gives a managed NPC a sheet with the template's starting stats.
     *
     * @throws IllegalArgumentException see {@link #create(UUID, Map)}
     */
    public void create(UUID npc, NpcTemplate template) {
        create(npc, template.stats());
    }

    /**
     * Gives a managed NPC a sheet, then counts the items it carries.
     *
     * @param stats starting base values by SkysongStats stat id or alias; the others start at
     *              their stats.yml value
     * @throws IllegalArgumentException when the NPC is not managed or a stat does not exist
     * @throws IllegalStateException    when the NPC has a sheet already
     */
    public void create(UUID npc, Map<String, Integer> stats) {
        if (!registry.isManaged(npc)) {
            throw new IllegalArgumentException("NPC " + npc + " is not managed");
        }
        Map<String, Integer> byId = new HashMap<>();
        stats.forEach((stat, value) -> byId.put(sheets.statId(stat)
                .orElseThrow(() -> new IllegalArgumentException("SkysongStats has no stat " + stat)), value));
        if (!sheets.create(npc, byId)) {
            throw new IllegalStateException("NPC " + npc + " has a stat sheet already");
        }
        counted.remove(npc);
        recount(npc);
    }

    /**
     * Counts the items a managed NPC with a sheet carries, when they changed since the last
     * count. Nothing happens for other NPCs.
     */
    public void recount(UUID npc) {
        if (!registry.isManaged(npc) || !sheets.exists(npc)) {
            counted.remove(npc);
            return;
        }
        Optional<List<ItemStack>> carried = items.carried(npc);
        if (carried.isEmpty() || carried.get().equals(counted.get(npc))) {
            return;
        }
        if (sheets.setItems(npc, carried.get())) {
            counted.put(npc, List.copyOf(carried.get()));
        }
    }

    /**
     * Counts every managed NPC's items.
     *
     * @param force send them even when they did not change, as SkysongStats needs on enable
     *              and after its reload
     */
    public void recountAll(boolean force) {
        if (force) {
            counted.clear();
        }
        Set<UUID> managed = registry.all().stream().map(record -> record.id()).collect(Collectors.toSet());
        counted.keySet().retainAll(managed);
        managed.forEach(this::recount);
    }
}
