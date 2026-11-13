package org.skysongdev.skysongnpcs.choices;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.event.NpcRespecEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;
import org.skysongdev.skysongnpcs.choices.menu.Guis;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Where a player makes an NPC's pending choices: the Assign points button and
 * {@code /snpc choose}. An NPC in setup gets its setup instead; afterwards the menu lists the
 * {@link ChoicePage}s, each with how many choices it has pending.
 */
public final class ChoiceMenu {
    private static final int ROWS = 3;
    private static final int FIRST_PAGE_SLOT = 10;
    private static final int MAX_PAGES = 7;
    private static final int CLOSE_SLOT = 26;

    private final Guis guis;
    private final SetupFlow setup;
    private final NpcRegistry registry;
    private final NpcLookup citizens;
    private final List<ChoicePage> pages = new ArrayList<>();
    private final Consumer<NpcRespecEvent> respecced;

    /**
     * @param respecced hears about every respec; the plugin fires {@link NpcRespecEvent} with it
     */
    public ChoiceMenu(Guis guis, SetupFlow setup, NpcRegistry registry, NpcLookup citizens,
                      Consumer<NpcRespecEvent> respecced) {
        this.guis = guis;
        this.setup = setup;
        this.registry = registry;
        this.citizens = citizens;
        this.respecced = Objects.requireNonNull(respecced, "respecced");
    }

    /** Adds a page after the ones already added. */
    public void addPage(ChoicePage page) {
        if (pages.stream().anyMatch(other -> other.id().equals(page.id()))) {
            throw new IllegalArgumentException("A choice page " + page.id() + " is added already");
        }
        if (pages.size() == MAX_PAGES) {
            throw new IllegalStateException("The choice menu holds " + MAX_PAGES + " pages at most");
        }
        pages.add(page);
    }

    public List<ChoicePage> pages() {
        return List.copyOf(pages);
    }

    public SetupFlow setup() {
        return setup;
    }

    /** Every pending choice of the NPC, on every page. */
    public int pending(UUID npc) {
        return pages.stream().mapToInt(page -> page.pending(npc)).sum();
    }

    /**
     * Opens the NPC's setup when it is in setup, otherwise this menu. The caller checks access.
     *
     * @return false when the NPC is not managed
     */
    public boolean open(Player player, UUID npc) {
        return registry.get(npc).map(record -> {
            if (record.state() == NpcState.SETUP) {
                return setup.open(player, record);
            }
            guis.show(draw(player, record));
            return true;
        }).orElse(false);
    }

    /** Gives back every choice the NPC made, on every page. @return how many */
    public int respec(UUID npc) {
        return respec(npc, null);
    }

    /**
     * Gives back the choices one source's grants paid for, on every page, or every choice when
     * {@code source} is null, and fires {@link NpcRespecEvent}. Nothing happens for an NPC
     * that is not managed.
     *
     * @return how many were given back
     */
    public int respec(UUID npc, @Nullable String source) {
        if (!registry.isManaged(npc)) {
            return 0;
        }
        int givenBack = pages.stream().mapToInt(page -> page.respec(npc, source)).sum();
        respecced.accept(new NpcRespecEvent(npc, source, givenBack));
        return givenBack;
    }

    private ChoiceGui draw(Player player, NpcRecord npc) {
        ChoiceGui gui = guis.page(player, ROWS, "choices.menu.title", Map.of("npc", citizens.name(npc.id())));
        for (int i = 0; i < pages.size(); i++) {
            ChoicePage page = pages.get(i);
            gui.button(FIRST_PAGE_SLOT + i, page.icon(npc), click -> registry.get(npc.id())
                    .ifPresent(current -> page.open(player, current, () -> open(player, current.id()))));
        }
        gui.button(CLOSE_SLOT, guis.icon(Material.BARRIER, "choices.menu.close", Map.of()), click -> guis.close(gui));
        return gui;
    }
}
