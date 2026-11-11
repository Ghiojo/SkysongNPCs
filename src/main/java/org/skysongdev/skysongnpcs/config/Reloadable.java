package org.skysongdev.skysongnpcs.config;

import java.util.List;

/**
 * A set of files beside config.yml and messages.yml that {@code /snpc reload} rereads, such
 * as the templates folder. A file with a mistake keeps its previous contents.
 */
public interface Reloadable {

    /**
     * Rereads the files.
     *
     * @return the files that had mistakes and were not applied, as paths inside the plugin's
     *         folder (for example {@code templates/guard.yml}); empty when all were applied
     */
    List<String> reload();
}
