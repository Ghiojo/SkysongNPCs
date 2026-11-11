package org.skysongdev.skysongnpcs.storage;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.skysongdev.skysongnpcs.abilities.AppliedAbility;

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
 * The {@code snpc_abilities} table: one row per NPC and ability, with what the ability did to
 * the NPC as JSON. The pool belongs to the NPC store, so closing this does nothing. Every call
 * runs on the caller's thread; {@link QueuedAbilityStore} moves the writes off the main thread.
 */
public final class SqlAbilityStore implements AbilityStore {
    static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS snpc_abilities (
              npc CHAR(36) NOT NULL,
              ability VARCHAR(64) NOT NULL,
              applied TEXT NOT NULL,
              PRIMARY KEY (npc, ability)
            )""";
    private static final String LOAD_ALL = "SELECT npc, ability, applied FROM snpc_abilities";
    private static final String SAVE = "INSERT INTO snpc_abilities (npc, ability, applied) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE applied = VALUES(applied)";
    private static final String DELETE = "DELETE FROM snpc_abilities WHERE npc = ? AND ability = ?";
    private static final String DELETE_ALL = "DELETE FROM snpc_abilities WHERE npc = ?";
    private static final Gson GSON = new Gson();

    private final DataSource dataSource;
    private final Consumer<String> log;

    /**
     * Creates the table when it is missing.
     *
     * @param log hears about rows that cannot be read, which are skipped
     */
    public SqlAbilityStore(DataSource dataSource, Consumer<String> log) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.log = Objects.requireNonNull(log, "log");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new StorageException("Unable to create the snpc_abilities table", e);
        }
    }

    @Override
    public List<StoredAbility> loadAll() {
        List<StoredAbility> abilities = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_ALL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String npc = results.getString("npc");
                try {
                    abilities.add(new StoredAbility(UUID.fromString(npc), results.getString("ability"),
                            decode(results.getString("applied"))));
                } catch (IllegalArgumentException | JsonParseException unreadable) {
                    log.accept("Skipped an ability of NPC " + npc + " that could not be read: " + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the NPC abilities", e);
        }
        return abilities;
    }

    @Override
    public void save(UUID npc, String ability, AppliedAbility applied) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SAVE)) {
            statement.setString(1, npc.toString());
            statement.setString(2, ability);
            statement.setString(3, encode(applied));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to save an ability of NPC " + npc, e);
        }
    }

    @Override
    public void delete(UUID npc, String ability) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, npc.toString());
            statement.setString(2, ability);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to delete an ability of NPC " + npc, e);
        }
    }

    @Override
    public void deleteAll(UUID npc) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            statement.setString(1, npc.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to delete the abilities of NPC " + npc, e);
        }
    }

    @Override
    public void close() {
    }

    static String encode(AppliedAbility applied) {
        return GSON.toJson(applied);
    }

    /** Missing parts read as empty, so rows from older versions still load. */
    static AppliedAbility decode(String json) {
        AppliedAbility read = GSON.fromJson(json, AppliedAbility.class);
        if (read == null) {
            throw new JsonParseException("empty");
        }
        return new AppliedAbility(read.modifiers(), read.tiers(), read.points(), read.eligible(), read.picks());
    }
}
