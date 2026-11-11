package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The {@code snpc_grants} table: one row per NPC, source and group, with the actions as a
 * comma list of their ids. The pool belongs to the NPC store, so closing this does nothing.
 * Every call runs on the caller's thread; {@link QueuedGrantStore} moves the writes off the
 * main thread.
 */
public final class SqlGrantStore implements GrantStore {
    static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS snpc_grants (
              npc CHAR(36) NOT NULL,
              source VARCHAR(64) NOT NULL,
              member_group VARCHAR(191) NOT NULL,
              actions VARCHAR(512) NOT NULL,
              PRIMARY KEY (npc, source, member_group)
            )""";
    private static final String LOAD_ALL = "SELECT npc, source, member_group, actions FROM snpc_grants";
    private static final String SAVE = "INSERT INTO snpc_grants (npc, source, member_group, actions) "
            + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE actions = VALUES(actions)";
    private static final String DELETE = "DELETE FROM snpc_grants WHERE npc = ? AND source = ? AND member_group = ?";
    private static final String DELETE_ALL = "DELETE FROM snpc_grants WHERE npc = ?";
    private static final String OWNER = "owner";

    private final DataSource dataSource;
    private final Consumer<String> log;

    /**
     * Creates the table when it is missing.
     *
     * @param log hears about rows that cannot be read, which are skipped
     */
    public SqlGrantStore(DataSource dataSource, Consumer<String> log) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.log = Objects.requireNonNull(log, "log");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new StorageException("Unable to create the snpc_grants table", e);
        }
    }

    @Override
    public List<StoredGrant> loadAll() {
        List<StoredGrant> grants = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_ALL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String npc = results.getString("npc");
                try {
                    grants.add(new StoredGrant(UUID.fromString(npc), new AccessGrant(
                            AccessGroup.parse(results.getString("member_group")),
                            source(results.getString("source")),
                            actions(results.getString("actions")))));
                } catch (IllegalArgumentException unreadable) {
                    log.accept("Skipped a grant of NPC " + npc + " that could not be read: " + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the NPC grants", e);
        }
        return grants;
    }

    @Override
    public void save(UUID npc, AccessGrant grant) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE)) {
            statement.setString(1, npc.toString());
            statement.setString(2, grant.source().key());
            statement.setString(3, grant.group().toString());
            statement.setString(4, grant.actions().stream().map(NpcAction::key).collect(Collectors.joining(",")));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to save a grant of NPC " + npc, e);
        }
    }

    @Override
    public void delete(UUID npc, GrantSource source, AccessGroup group) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, npc.toString());
            statement.setString(2, source.key());
            statement.setString(3, group.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to delete a grant of NPC " + npc, e);
        }
    }

    @Override
    public void deleteAll(UUID npc) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            statement.setString(1, npc.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to delete the grants of NPC " + npc, e);
        }
    }

    @Override
    public void close() {
    }

    private static GrantSource source(String key) {
        return key.equals(OWNER) ? GrantSource.owner() : GrantSource.plugin(key);
    }

    /** Ids no longer known (an action removed in an update) are dropped; a row left with none is unreadable. */
    private static Set<NpcAction> actions(String keys) {
        Set<NpcAction> actions = EnumSet.noneOf(NpcAction.class);
        for (String key : keys.split(",")) {
            NpcAction.fromKey(key).ifPresent(actions::add);
        }
        return actions;
    }
}
