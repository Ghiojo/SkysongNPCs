package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.picks.PickGrant;
import org.skysongdev.skysongnpcs.picks.PickSpend;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The {@code snpc_pick_grants} and {@code snpc_pick_spends} tables. A spend row names its
 * grant, and every spend row also carries the NPC so one NPC's rows go in one statement. The
 * pool belongs to the NPC store, so closing this does nothing. Every call runs on the caller's
 * thread; {@link QueuedPickStore} moves the writes off the main thread.
 */
public final class SqlPickStore implements PickStore {
    static final String CREATE_GRANTS = """
            CREATE TABLE IF NOT EXISTS snpc_pick_grants (
              id CHAR(36) NOT NULL PRIMARY KEY,
              npc CHAR(36) NOT NULL,
              source VARCHAR(64) NOT NULL,
              skill VARCHAR(64) NOT NULL,
              tier VARCHAR(64) NOT NULL,
              pick_count INT NOT NULL
            )""";
    static final String CREATE_SPENDS = """
            CREATE TABLE IF NOT EXISTS snpc_pick_spends (
              grant_id CHAR(36) NOT NULL,
              npc CHAR(36) NOT NULL,
              proficiency VARCHAR(64) NOT NULL,
              previous_tier VARCHAR(64) NOT NULL,
              PRIMARY KEY (grant_id, proficiency)
            )""";
    private static final String LOAD_GRANTS = "SELECT id, npc, source, skill, tier, pick_count FROM snpc_pick_grants";
    private static final String LOAD_SPENDS = "SELECT grant_id, proficiency, previous_tier FROM snpc_pick_spends";
    private static final String SAVE_GRANT = "INSERT INTO snpc_pick_grants (id, npc, source, skill, tier, pick_count) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE pick_count = VALUES(pick_count)";
    private static final String DELETE_GRANT = "DELETE FROM snpc_pick_grants WHERE id = ?";
    private static final String DELETE_GRANT_SPENDS = "DELETE FROM snpc_pick_spends WHERE grant_id = ?";
    private static final String SAVE_SPEND = "INSERT INTO snpc_pick_spends (grant_id, npc, proficiency, previous_tier) "
            + "VALUES (?, (SELECT npc FROM snpc_pick_grants WHERE id = ?), ?, ?) "
            + "ON DUPLICATE KEY UPDATE previous_tier = VALUES(previous_tier)";
    private static final String DELETE_SPEND = "DELETE FROM snpc_pick_spends WHERE grant_id = ? AND proficiency = ?";
    private static final String DELETE_ALL_GRANTS = "DELETE FROM snpc_pick_grants WHERE npc = ?";
    private static final String DELETE_ALL_SPENDS = "DELETE FROM snpc_pick_spends WHERE npc = ?";

    private final DataSource dataSource;
    private final Consumer<String> log;

    /**
     * Creates the tables when they are missing.
     *
     * @param log hears about rows that cannot be read, which are skipped
     */
    public SqlPickStore(DataSource dataSource, Consumer<String> log) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.log = Objects.requireNonNull(log, "log");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_GRANTS);
            statement.execute(CREATE_SPENDS);
        } catch (SQLException e) {
            throw new StorageException("Unable to create the proficiency pick tables", e);
        }
    }

    @Override
    public List<PickGrant> loadGrants() {
        List<PickGrant> grants = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_GRANTS);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String id = results.getString("id");
                try {
                    grants.add(new PickGrant(UUID.fromString(id), UUID.fromString(results.getString("npc")),
                            results.getString("source"), results.getString("skill"), results.getString("tier"),
                            results.getInt("pick_count")));
                } catch (IllegalArgumentException unreadable) {
                    log.accept("Skipped the proficiency pick grant " + id + " that could not be read: "
                            + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the proficiency pick grants", e);
        }
        return grants;
    }

    @Override
    public List<PickSpend> loadSpends() {
        List<PickSpend> spends = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_SPENDS);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String grant = results.getString("grant_id");
                try {
                    spends.add(new PickSpend(UUID.fromString(grant), results.getString("proficiency"),
                            results.getString("previous_tier")));
                } catch (IllegalArgumentException unreadable) {
                    log.accept("Skipped a proficiency pick of grant " + grant + " that could not be read: "
                            + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the proficiency picks", e);
        }
        return spends;
    }

    @Override
    public void saveGrant(PickGrant grant) {
        update("Unable to save the proficiency pick grant " + grant.id(), SAVE_GRANT, grant.id().toString(),
                grant.npc().toString(), grant.source(), grant.skill(), grant.tier(), grant.count());
    }

    @Override
    public void deleteGrant(UUID grant) {
        String failure = "Unable to delete the proficiency pick grant " + grant;
        update(failure, DELETE_GRANT_SPENDS, grant.toString());
        update(failure, DELETE_GRANT, grant.toString());
    }

    @Override
    public void saveSpend(PickSpend spend) {
        update("Unable to save a proficiency pick of grant " + spend.grant(), SAVE_SPEND, spend.grant().toString(),
                spend.grant().toString(), spend.proficiency(), spend.previousTier());
    }

    @Override
    public void deleteSpend(UUID grant, String proficiency) {
        update("Unable to delete a proficiency pick of grant " + grant, DELETE_SPEND, grant.toString(), proficiency);
    }

    @Override
    public void deleteAll(UUID npc) {
        String failure = "Unable to delete the proficiency picks of NPC " + npc;
        update(failure, DELETE_ALL_SPENDS, npc.toString());
        update(failure, DELETE_ALL_GRANTS, npc.toString());
    }

    @Override
    public void close() {
    }

    private void update(String failure, String sql, Object... values) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException(failure, e);
        }
    }
}
