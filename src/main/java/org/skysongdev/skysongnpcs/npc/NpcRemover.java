package org.skysongdev.skysongnpcs.npc;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Removes a managed NPC for good: the Citizens NPC, its record and its stat sheet, the same as
 * {@code /npc remove}. Plain Citizens NPCs are never touched.
 *
 * <p>The Citizens NPC is destroyed first. Citizens then fires its remove event at once, and the
 * plugin's listeners delete the record (firing
 * {@link org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent}) and the sheet, so removing
 * through here and through Citizens are one path. When Citizens has lost the NPC already, no
 * event comes, and the record and sheet are deleted here instead.
 */
public final class NpcRemover {
    private final NpcRegistry registry;
    private final Predicate<UUID> destroy;
    private final Consumer<UUID> deleteSheet;

    /**
     * @param destroy     destroys the Citizens NPC; false when Citizens has no such NPC
     * @param deleteSheet deletes the NPC's stat sheet
     */
    public NpcRemover(NpcRegistry registry, Predicate<UUID> destroy, Consumer<UUID> deleteSheet) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.destroy = Objects.requireNonNull(destroy, "destroy");
        this.deleteSheet = Objects.requireNonNull(deleteSheet, "deleteSheet");
    }

    /**
     * Removes a managed NPC. Everything is done when this returns.
     *
     * @return false when the NPC is not managed; then nothing happens
     */
    public boolean remove(UUID npc) {
        Objects.requireNonNull(npc, "npc");
        if (!registry.isManaged(npc)) {
            return false;
        }
        boolean inCitizens = destroy.test(npc);
        // Citizens' remove event dropped the record already; this covers an NPC Citizens had lost.
        registry.removed(npc);
        if (!inCitizens) {
            deleteSheet.accept(npc);
        }
        return true;
    }
}
