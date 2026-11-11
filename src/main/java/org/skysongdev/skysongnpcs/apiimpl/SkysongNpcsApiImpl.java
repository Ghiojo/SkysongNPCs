package org.skysongdev.skysongnpcs.apiimpl;

import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;

/** The plugin's implementation of {@link SkysongNpcsApi}; each milestone adds its part here. */
public final class SkysongNpcsApiImpl implements SkysongNpcsApi {
    /** Raised with every change to the api module, as {@link SkysongNpcsApi#apiVersion()} describes. */
    public static final String API_VERSION = "0.1.0";

    @Override
    public @NotNull String apiVersion() {
        return API_VERSION;
    }
}
