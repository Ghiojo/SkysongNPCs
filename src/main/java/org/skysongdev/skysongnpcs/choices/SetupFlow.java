package org.skysongdev.skysongnpcs.choices;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.storage.ChoiceStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An NPC's first setup: its {@link SetupStep}s in order, each confirmed by the player before
 * the next opens. The steps an NPC has done are saved, so a setup closed halfway goes on where
 * it stopped. When no step is left, the NPC becomes PLACED.
 */
public final class SetupFlow {
    private static final Pattern ID = Pattern.compile("[a-z0-9-]{1,64}");

    private final ChoiceStore store;
    private final NpcRegistry registry;
    private final Completed completed;
    private final List<SetupStep> steps = new ArrayList<>();
    private final Map<UUID, Set<String>> done = new HashMap<>();

    /** @param completed hears about every finished setup, after the NPC is PLACED */
    public SetupFlow(ChoiceStore store, NpcRegistry registry, Completed completed) {
        this.store = store;
        this.registry = registry;
        this.completed = completed;
    }

    /** Reads the steps NPCs have done. Called once on enable. */
    public void load() {
        done.clear();
        store.loadSteps().forEach(step -> done.computeIfAbsent(step.npc(), npc -> new HashSet<>()).add(step.step()));
    }

    /** Adds a step after the ones already added. */
    public void addStep(SetupStep step) {
        if (!ID.matcher(step.id()).matches()) {
            throw new IllegalArgumentException("A setup step id is 1 to 64 lowercase letters, digits or -: " + step.id());
        }
        if (steps.stream().anyMatch(other -> other.id().equals(step.id()))) {
            throw new IllegalArgumentException("A setup step " + step.id() + " is added already");
        }
        steps.add(step);
    }

    public List<SetupStep> steps() {
        return List.copyOf(steps);
    }

    /** The step the NPC is on: the first not done that applies to it. */
    public Optional<SetupStep> next(NpcRecord npc) {
        Set<String> finished = done.getOrDefault(npc.id(), Set.of());
        return steps.stream().filter(step -> !finished.contains(step.id()) && step.applies(npc)).findFirst();
    }

    public boolean isDone(UUID npc, String step) {
        return done.getOrDefault(npc, Set.of()).contains(step);
    }

    /**
     * Opens the NPC's current step for the player, or finishes the setup when none is left.
     *
     * @return false when the NPC is not in setup
     */
    public boolean open(Player player, NpcRecord npc) {
        if (npc.state() != NpcState.SETUP) {
            return false;
        }
        Optional<SetupStep> step = next(npc);
        if (step.isPresent()) {
            step.get().open(player, npc, this);
        } else {
            complete(player, npc);
        }
        return true;
    }

    /** A step tells the flow the player confirmed it; the next one opens. */
    public void stepDone(Player player, NpcRecord npc, String step) {
        if (done.computeIfAbsent(npc.id(), id -> new HashSet<>()).add(step)) {
            store.saveStep(npc.id(), step);
        }
        registry.get(npc.id()).ifPresent(current -> open(player, current));
    }

    /**
     * Ends the setup: the NPC is PLACED, whatever steps are left.
     *
     * @param player who finished it; null for staff or a plugin
     */
    public NpcRecord complete(@Nullable Player player, NpcRecord npc) {
        NpcRecord placed = registry.setState(npc.id(), NpcState.PLACED);
        forget(npc.id());
        completed.completed(placed, player);
        return placed;
    }

    /** Drops the steps of an NPC that finished its setup or was deleted. */
    public void forget(UUID npc) {
        if (done.remove(npc) != null) {
            store.deleteSteps(npc);
        }
    }

    /** Gives a copied NPC the steps its original has done. */
    public void copy(UUID original, UUID copy) {
        for (String step : done.getOrDefault(original, Set.of())) {
            done.computeIfAbsent(copy, id -> new HashSet<>()).add(step);
            store.saveStep(copy, step);
        }
    }

    @FunctionalInterface
    public interface Completed {
        void completed(NpcRecord npc, @Nullable Player player);
    }
}
