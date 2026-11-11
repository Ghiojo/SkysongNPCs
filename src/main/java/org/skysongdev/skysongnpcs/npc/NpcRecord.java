package org.skysongdev.skysongnpcs.npc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What SkysongNPCs keeps for one managed NPC, one row in {@code snpc_npcs}. Immutable: a
 * change makes a new record. Later milestones add their own data (grants, policy, choices,
 * abilities, ticket) in their own tables, keyed by the same id.
 *
 * @param id       the Citizens UUID
 * @param owner    its owner
 * @param state    where it is in its life
 * @param templateId the template it came from, or null; {@link #template()} is the API's view of it
 */
public record NpcRecord(@NotNull UUID id, @NotNull NpcOwner owner, @NotNull NpcState state,
                        @Nullable String templateId)
        implements ManagedNpc {

    public NpcRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(state, "state");
    }

    /** A record for an NPC staff adopted: placed, with no template. */
    public static NpcRecord adopted(UUID id, NpcOwner owner) {
        return new NpcRecord(id, owner, NpcState.PLACED, null);
    }

    @Override
    public @NotNull Optional<String> template() {
        return Optional.ofNullable(templateId);
    }

    public NpcRecord withOwner(NpcOwner newOwner) {
        return new NpcRecord(id, newOwner, state, templateId);
    }

    /** The same record for another NPC: what a Citizens copy gets. */
    public NpcRecord copiedTo(UUID copy) {
        return new NpcRecord(copy, owner, state, templateId);
    }

    // M18: pickup

    /** The same record in another state. */
    public NpcRecord withState(NpcState newState) {
        return new NpcRecord(id, owner, newState, templateId);
    }
}
