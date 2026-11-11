package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.npc.NpcState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The {@code snpc_items} table: one row per NPC with its blueprint item, ticket and the state
 * it goes back to. The pool belongs to the NPC store, so closing this does nothing. Every call
 * runs on the caller's thread; {@link QueuedNpcItemStore} moves the writes off the main thread.
 */
public final class SqlNpcItemStore implements NpcItemStore {
    static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS snpc_items (
              npc CHAR(36) NOT NULL,
              blueprint MEDIUMBLOB NULL,
              ticket CHAR(36) NULL,
              resume_state VARCHAR(16) NULL,
              PRIMARY KEY (npc)
            )""";
    private static final String LOAD_ALL = "SELECT npc, blueprint, ticket, resume_state FROM snpc_items";
    private static final String SAVE = "INSERT INTO snpc_items (npc, blueprint, ticket, resume_state) VALUES (?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE blueprint = VALUES(blueprint), ticket = VALUES(ticket), "
            + "resume_state = VALUES(resume_state)";
    private static final String DELETE = "DELETE FROM snpc_items WHERE npc = ?";

    private final DataSource dataSource;
    private final Consumer<String> log;

    /**
     * Creates the table when it is missing.
     *
     * @param log hears about rows that cannot be read, which are skipped
     */
    public SqlNpcItemStore(DataSource dataSource, Consumer<String> log) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.log = Objects.requireNonNull(log, "log");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new StorageException("Unable to create the snpc_items table", e);
        }
    }

    @Override
    public Map<UUID, StoredItem> loadAll() {
        Map<UUID, StoredItem> items = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_ALL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String npc = results.getString("npc");
                try {
                    String ticket = results.getString("ticket");
                    String resume = results.getString("resume_state");
                    items.put(UUID.fromString(npc), new StoredItem(results.getBytes("blueprint"),
                            ticket == null ? null : UUID.fromString(ticket),
                            resume == null ? null : NpcState.valueOf(resume)));
                } catch (IllegalArgumentException unreadable) {
                    log.accept("Skipped the item row of NPC " + npc + ", which could not be read: "
                            + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the NPC items", e);
        }
        return items;
    }

    @Override
    public void save(UUID npc, StoredItem item) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE)) {
            statement.setString(1, npc.toString());
            if (item.blueprint() == null) {
                statement.setNull(2, Types.BLOB);
            } else {
                statement.setBytes(2, item.blueprint());
            }
            statement.setString(3, item.ticket() == null ? null : item.ticket().toString());
            statement.setString(4, item.resume() == null ? null : item.resume().name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to save the item of NPC " + npc, e);
        }
    }

    @Override
    public void delete(UUID npc) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, npc.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to delete the item of NPC " + npc, e);
        }
    }

    @Override
    public void close() {
    }
}
