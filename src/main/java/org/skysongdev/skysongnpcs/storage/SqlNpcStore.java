package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The {@code snpc_npcs} table: one row per managed NPC. A server-owned NPC has no owner
 * player or profile. Every call runs on the caller's thread; {@link QueuedNpcStore} moves
 * the writes off the main thread.
 */
public final class SqlNpcStore implements NpcStore {
    static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS snpc_npcs (
              npc CHAR(36) NOT NULL,
              owner_player CHAR(36) NULL,
              owner_profile VARCHAR(64) NULL,
              state VARCHAR(16) NOT NULL,
              template VARCHAR(64) NULL,
              PRIMARY KEY (npc)
            )""";
    private static final String LOAD_ALL = "SELECT npc, owner_player, owner_profile, state, template FROM snpc_npcs";
    private static final String SAVE = "INSERT INTO snpc_npcs (npc, owner_player, owner_profile, state, template) "
            + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE owner_player = VALUES(owner_player), "
            + "owner_profile = VALUES(owner_profile), state = VALUES(state), template = VALUES(template)";
    private static final String DELETE = "DELETE FROM snpc_npcs WHERE npc = ?";

    private final DataSource dataSource;
    private final Runnable onClose;

    /**
     * Creates the table when it is missing.
     *
     * @param onClose closes the data source (the connection pool)
     */
    public SqlNpcStore(DataSource dataSource, Runnable onClose) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new StorageException("Unable to create the snpc_npcs table", e);
        }
    }

    @Override
    public List<NpcRecord> loadAll() {
        List<NpcRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_ALL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                records.add(read(results));
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the NPC records", e);
        }
        return records;
    }

    private static NpcRecord read(ResultSet row) throws SQLException {
        String player = row.getString("owner_player");
        NpcOwner owner = player == null
                ? NpcOwner.server()
                : NpcOwner.character(UUID.fromString(player), row.getString("owner_profile"));
        return new NpcRecord(UUID.fromString(row.getString("npc")), owner,
                NpcState.valueOf(row.getString("state")), row.getString("template"));
    }

    @Override
    public void save(NpcRecord record) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE)) {
            statement.setString(1, record.id().toString());
            if (record.owner() instanceof NpcOwner.CharacterOwner character) {
                statement.setString(2, character.player().toString());
                statement.setString(3, character.profile());
            } else {
                statement.setNull(2, Types.CHAR);
                statement.setNull(3, Types.VARCHAR);
            }
            statement.setString(4, record.state().name());
            statement.setString(5, record.template().orElse(null));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to save the record of NPC " + record.id(), e);
        }
    }

    @Override
    public void delete(UUID npc) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, npc.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to delete the record of NPC " + npc, e);
        }
    }

    @Override
    public void close() {
        onClose.run();
    }
}
