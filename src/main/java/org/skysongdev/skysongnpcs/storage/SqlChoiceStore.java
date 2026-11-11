package org.skysongdev.skysongnpcs.storage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The choice tables: {@code snpc_point_grants} (one row per grant, eligible stats as a comma
 * list), {@code snpc_point_spends} (points of a grant on a stat) and {@code snpc_setup_steps}.
 * The pool belongs to the NPC store, so closing this does nothing. Every call runs on the
 * caller's thread; {@link QueuedChoiceStore} moves the writes off the main thread.
 */
public final class SqlChoiceStore implements ChoiceStore {
    static final List<String> CREATE_TABLES = List.of("""
            CREATE TABLE IF NOT EXISTS snpc_point_grants (
              id CHAR(36) NOT NULL PRIMARY KEY,
              npc CHAR(36) NOT NULL,
              source VARCHAR(64) NOT NULL,
              amount INT NOT NULL,
              eligible VARCHAR(512) NOT NULL,
              created BIGINT NOT NULL
            )""", """
            CREATE TABLE IF NOT EXISTS snpc_point_spends (
              npc CHAR(36) NOT NULL,
              grant_id CHAR(36) NOT NULL,
              stat VARCHAR(64) NOT NULL,
              amount INT NOT NULL,
              PRIMARY KEY (npc, grant_id, stat)
            )""", """
            CREATE TABLE IF NOT EXISTS snpc_setup_steps (
              npc CHAR(36) NOT NULL,
              step VARCHAR(64) NOT NULL,
              PRIMARY KEY (npc, step)
            )""");
    private static final String LOAD_GRANTS =
            "SELECT id, npc, source, amount, eligible, created FROM snpc_point_grants ORDER BY created, id";
    private static final String LOAD_SPENDS = "SELECT npc, grant_id, stat, amount FROM snpc_point_spends";
    private static final String LOAD_STEPS = "SELECT npc, step FROM snpc_setup_steps";
    private static final String SAVE_GRANT = "INSERT INTO snpc_point_grants (id, npc, source, amount, eligible, created) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount), eligible = VALUES(eligible)";
    private static final String DELETE_GRANT = "DELETE FROM snpc_point_grants WHERE id = ?";
    private static final String SAVE_SPEND = "INSERT INTO snpc_point_spends (npc, grant_id, stat, amount) "
            + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
    private static final String DELETE_SPEND = "DELETE FROM snpc_point_spends WHERE npc = ? AND grant_id = ? AND stat = ?";
    private static final String DELETE_SPENDS = "DELETE FROM snpc_point_spends WHERE npc = ?";
    private static final String SAVE_STEP = "INSERT IGNORE INTO snpc_setup_steps (npc, step) VALUES (?, ?)";
    private static final String DELETE_STEPS = "DELETE FROM snpc_setup_steps WHERE npc = ?";
    private static final String DELETE_GRANTS = "DELETE FROM snpc_point_grants WHERE npc = ?";

    private final DataSource dataSource;
    private final Consumer<String> log;

    /**
     * Creates the tables when they are missing.
     *
     * @param log hears about rows that cannot be read, which are skipped
     */
    public SqlChoiceStore(DataSource dataSource, Consumer<String> log) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.log = Objects.requireNonNull(log, "log");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String table : CREATE_TABLES) {
                statement.execute(table);
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to create the choice tables", e);
        }
    }

    @Override
    public List<StoredPointGrant> loadGrants() {
        List<StoredPointGrant> grants = new ArrayList<>();
        query(LOAD_GRANTS, results -> grants.add(new StoredPointGrant(
                UUID.fromString(results.getString("id")), UUID.fromString(results.getString("npc")),
                results.getString("source"), results.getInt("amount"), stats(results.getString("eligible")),
                results.getLong("created"))));
        return grants;
    }

    @Override
    public List<StoredSpend> loadSpends() {
        List<StoredSpend> spends = new ArrayList<>();
        query(LOAD_SPENDS, results -> spends.add(new StoredSpend(UUID.fromString(results.getString("npc")),
                UUID.fromString(results.getString("grant_id")), results.getString("stat"), results.getInt("amount"))));
        return spends;
    }

    @Override
    public List<StoredStep> loadSteps() {
        List<StoredStep> steps = new ArrayList<>();
        query(LOAD_STEPS, results -> steps.add(new StoredStep(UUID.fromString(results.getString("npc")),
                results.getString("step"))));
        return steps;
    }

    @Override
    public void saveGrant(StoredPointGrant grant) {
        update(SAVE_GRANT, "save a point grant of NPC " + grant.npc(), grant.id().toString(), grant.npc().toString(),
                grant.source(), grant.amount(), String.join(",", grant.eligible()), grant.created());
    }

    @Override
    public void deleteGrant(UUID grant) {
        update(DELETE_GRANT, "delete point grant " + grant, grant.toString());
    }

    @Override
    public void saveSpend(StoredSpend spend) {
        if (spend.amount() == 0) {
            update(DELETE_SPEND, "delete a spend of NPC " + spend.npc(), spend.npc().toString(),
                    spend.grant().toString(), spend.stat());
        } else {
            update(SAVE_SPEND, "save a spend of NPC " + spend.npc(), spend.npc().toString(),
                    spend.grant().toString(), spend.stat(), spend.amount());
        }
    }

    @Override
    public void deleteSpends(UUID npc) {
        update(DELETE_SPENDS, "delete the spends of NPC " + npc, npc.toString());
    }

    @Override
    public void saveStep(UUID npc, String step) {
        update(SAVE_STEP, "save a setup step of NPC " + npc, npc.toString(), step);
    }

    @Override
    public void deleteSteps(UUID npc) {
        update(DELETE_STEPS, "delete the setup steps of NPC " + npc, npc.toString());
    }

    @Override
    public void deleteAll(UUID npc) {
        deleteSteps(npc);
        deleteSpends(npc);
        update(DELETE_GRANTS, "delete the point grants of NPC " + npc, npc.toString());
    }

    @Override
    public void close() {
    }

    private void query(String sql, Row row) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                try {
                    row.read(results);
                } catch (IllegalArgumentException unreadable) {
                    log.accept("Skipped a choice row that could not be read: " + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the NPC choices", e);
        }
    }

    private void update(String sql, String what, Object... values) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to " + what, e);
        }
    }

    private static Set<String> stats(String list) {
        return Arrays.stream(list.split(",")).map(String::trim).filter(stat -> !stat.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @FunctionalInterface
    private interface Row {
        void read(ResultSet results) throws SQLException;
    }
}
