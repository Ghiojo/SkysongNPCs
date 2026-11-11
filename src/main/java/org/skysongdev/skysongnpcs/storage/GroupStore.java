package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;

import java.util.List;

/** Where player groups ({@code /snpc group}) are kept between restarts. */
public interface GroupStore extends AutoCloseable {

    /** Every saved group. Called once on enable, on the main thread. */
    List<PlayerGroup> loadAll();

    /** Saves a group with its owner and members, replacing what was saved under its name. */
    void save(PlayerGroup group);

    /** Deletes a group and its members, if it is there. */
    void delete(String id);

    /** Finishes pending writes. */
    @Override
    void close();
}
