package org.skysongdev.skysongnpcs.apiimpl;

import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.access.Access;
import org.skysongdev.skysongnpcs.api.menu.NpcMenu;
import org.skysongdev.skysongnpcs.api.npc.Npcs;
import org.skysongdev.skysongnpcs.api.sheet.NpcSheets;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.templates.TemplateRegistry;

/** The plugin's implementation of {@link SkysongNpcsApi}; each milestone adds its part here. */
public final class SkysongNpcsApiImpl implements SkysongNpcsApi {
    /** Raised with every change to the api module, as {@link SkysongNpcsApi#apiVersion()} describes. */
    public static final String API_VERSION = "0.5.0";

    private final Npcs npcs;
    private final Access access;
    private final NpcMenu menu;
    private final NpcSheets sheets;

    public SkysongNpcsApiImpl(NpcRegistry registry, NpcLookup citizens, AccessService access, NpcMenu menu,
                              NpcSheetService sheets, TemplateRegistry templates) {
        this.npcs = new NpcsImpl(registry, citizens);
        this.access = new AccessImpl(registry, access);
        this.menu = menu;
        this.sheets = new NpcSheetsImpl(sheets, templates);
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
}
