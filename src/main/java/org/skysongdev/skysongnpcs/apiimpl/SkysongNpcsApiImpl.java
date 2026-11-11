package org.skysongdev.skysongnpcs.apiimpl;

import org.skysongdev.skysongnpcs.api.sheet.NpcEffects;
import org.skysongdev.skysongnpcs.sheets.SheetEffects;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.access.Access;
import org.skysongdev.skysongnpcs.api.inventory.NpcInventories;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroups;
import org.skysongdev.skysongnpcs.groups.PlayerGroupRegistry;
import org.skysongdev.skysongnpcs.api.appearance.NpcAppearance;
import org.skysongdev.skysongnpcs.appearance.AppearanceService;
import org.skysongdev.skysongnpcs.api.menu.NpcMenu;
import org.skysongdev.skysongnpcs.api.movement.NpcMovement;
import org.skysongdev.skysongnpcs.api.items.NpcItems;
import org.skysongdev.skysongnpcs.api.npc.Npcs;
import org.skysongdev.skysongnpcs.api.sheet.NpcSheets;
import org.skysongdev.skysongnpcs.items.Blueprints;
import org.skysongdev.skysongnpcs.items.NpcFactory;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.templates.TemplateRegistry;
// M5: access policies
import org.skysongdev.skysongnpcs.api.policy.Policies;
import org.skysongdev.skysongnpcs.policy.PolicyRegistry;

/** The plugin's implementation of {@link SkysongNpcsApi}; each milestone adds its part here. */
public final class SkysongNpcsApiImpl implements SkysongNpcsApi {
    /** Raised with every change to the api module, as {@link SkysongNpcsApi#apiVersion()} describes. */
    public static final String API_VERSION = "0.12.0";

    private final Npcs npcs;
    private final Access access;
    private final NpcMenu menu;
    private final NpcSheets sheets;
    private final NpcItems items;
    // M5: access policies
    private final Policies policies;
    // M9
    private final NpcInventories inventories;
    // M7: movement
    private final NpcMovement movement;
    // M4
    private final PlayerGroups groups;
    // M12
    private final NpcEffects effects;
    // M8: appearance
    private final NpcAppearance appearance;

    public SkysongNpcsApiImpl(NpcRegistry registry, NpcLookup citizens, AccessService access, NpcMenu menu,
                              NpcSheetService sheets, TemplateRegistry templates,
                              NpcFactory factory, Blueprints blueprints,
                              PolicyRegistry policies,
                              NpcInventories inventories, NpcMovement movement,
                              PlayerGroupRegistry groups, SheetEffects effects,
                              AppearanceService appearance) {
        this.npcs = new NpcsImpl(registry, citizens, factory);
        this.access = new AccessImpl(registry, access);
        this.menu = menu;
        this.sheets = new NpcSheetsImpl(sheets, templates);
        this.items = new NpcItemsImpl(blueprints, factory);
        this.policies = new PoliciesImpl(registry, policies);
        this.inventories = inventories;
        this.movement = movement;
        this.groups = new PlayerGroupsImpl(groups, access);
        this.effects = new NpcEffectsImpl(registry, sheets, effects);
        this.appearance = new NpcAppearanceImpl(appearance);
    }

    @Override
    public @NotNull String apiVersion() {
        return API_VERSION;
    }

    @Override
    public @NotNull Npcs npcs() {
        return npcs;
    }

    @Override
    public @NotNull Access access() {
        return access;
    }

    @Override
    public @NotNull NpcMenu menu() {
        return menu;
    }

    @Override
    public @NotNull NpcSheets sheets() {
        return sheets;
    }

    // M17

    @Override
    public @NotNull NpcItems items() {
        return items;
    }

    // M5: access policies

    @Override
    public @NotNull Policies policies() {
        return policies;
    }

    // M9

    @Override
    public @NotNull NpcInventories inventories() {
        return inventories;
    }

    // M7: movement

    @Override
    public @NotNull NpcMovement movement() {
        return movement;
    }

    // M4

    @Override
    public @NotNull PlayerGroups groups() {
        return groups;
    }

    // M12

    @Override
    public @NotNull NpcEffects effects() {
        return effects;
    }

    // M8: appearance

    @Override
    public @NotNull NpcAppearance appearance() {
        return appearance;
    }
}
