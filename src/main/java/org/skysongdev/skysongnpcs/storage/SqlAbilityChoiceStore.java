package org.skysongdev.skysongnpcs.storage;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.skysongdev.skysongnpcs.abilities.AbilityChoiceGrant;

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
 * The {@code snpc_ability_choice_grants} and {@code snpc_ability_choices} tables. A grant's
 * options are a JSON list, and {@code seq} keeps rows in the order they were first saved. A
 * choice row names its grant and carries the NPC, so one NPC's rows go in one statement. The
 * pool belongs to the NPC store, so closing this does nothing. Every call runs on the caller's
 * thread; {@link QueuedAbilityChoiceStore} moves the writes off the main thread.
 */
public final class SqlAbilityChoiceStore implements AbilityChoiceStore {
    static final String CREATE_GRANTS = """
            CREATE TABLE IF NOT EXISTS snpc_ability_choice_grants (
              id CHAR(36) NOT NULL PRIMARY KEY,
              npc CHAR(36) NOT NULL,
              source VARCHAR(64) NOT NULL,
              options TEXT NOT NULL,
              choice_count INT NOT NULL,
              seq BIGINT NOT NULL AUTO_INCREMENT UNIQUE
            )""";
    static final String CREATE_CHOICES = """
            CREATE TABLE IF NOT EXISTS snpc_ability_choices (
              grant_id CHAR(36) NOT NULL,
              npc CHAR(36) NOT NULL,
              ability VARCHAR(64) NOT NULL,
              seq BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
              PRIMARY KEY (grant_id, ability)
            )""";
    private static final String LOAD_GRANTS = "SELECT id, npc, source, options, choice_count "
            + "FROM snpc_ability_choice_grants ORDER BY seq";
    private static final String LOAD_CHOICES = "SELECT grant_id, ability FROM snpc_ability_choices ORDER BY seq";
    private static final String SAVE_GRANT = "INSERT INTO snpc_ability_choice_grants (id, npc, source, options, choice_count) "
            + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE options = VALUES(options), choice_count = VALUES(choice_count)";
    private static final String DELETE_GRANT = "DELETE FROM snpc_ability_choice_grants WHERE id = ?";
    private static final String DELETE_GRANT_CHOICES = "DELETE FROM snpc_ability_choices WHERE grant_id = ?";
    private static final String SAVE_CHOICE = "INSERT INTO snpc_ability_choices (grant_id, npc, ability) "
            + "VALUES (?, (SELECT npc FROM snpc_ability_choice_grants WHERE id = ?), ?) "
            + "ON DUPLICATE KEY UPDATE ability = VALUES(ability)";
    private static final String DELETE_CHOICE = "DELETE FROM snpc_ability_choices WHERE grant_id = ? AND ability = ?";
    private static final String DELETE_ALL_GRANTS = "DELETE FROM snpc_ability_choice_grants WHERE npc = ?";
    private static final String DELETE_ALL_CHOICES = "DELETE FROM snpc_ability_choices WHERE npc = ?";
    private static final Gson GSON = new Gson();

    private final DataSource dataSource;
    private final Consumer<String> log;

    /**
     * Creates the tables when they are missing.
     *
     * @param log hears about rows that cannot be read, which are skipped
     */
    public SqlAbilityChoiceStore(DataSource dataSource, Consumer<String> log) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.log = Objects.requireNonNull(log, "log");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_GRANTS);
            statement.execute(CREATE_CHOICES);
        } catch (SQLException e) {
            throw new StorageException("Unable to create the ability choice tables", e);
        }
    }

    @Override
    public List<AbilityChoiceGrant> loadGrants() {
        List<AbilityChoiceGrant> grants = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_GRANTS);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String id = results.getString("id");
                try {
                    List<String> options = GSON.fromJson(results.getString("options"),
                            new TypeToken<List<String>>() { }.getType());
                    grants.add(new AbilityChoiceGrant(UUID.fromString(id), UUID.fromString(results.getString("npc")),
                            results.getString("source"), options == null ? List.of() : options,
                            results.getInt("choice_count")));
                } catch (IllegalArgumentException | JsonParseException unreadable) {
                    log.accept("Skipped the ability choice " + id + " that could not be read: " + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the ability choices", e);
        }
        return grants;
    }

    @Override
    public List<StoredChoice> loadChoices() {
        List<StoredChoice> choices = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_CHOICES);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String grant = results.getString("grant_id");
                try {
                    choices.add(new StoredChoice(UUID.fromString(grant), results.getString("ability")));
                } catch (IllegalArgumentException unreadable) {
                    log.accept("Skipped a choice of ability choice " + grant + " that could not be read: "
                            + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the chosen abilities", e);
        }
        return choices;
    }

    @Override
    public void saveGrant(AbilityChoiceGrant grant) {
        update("Unable to save the ability choice " + grant.id(), SAVE_GRANT, grant.id().toString(),
                grant.npc().toString(), grant.source(), GSON.toJson(grant.options()), grant.count());
    }

    @Override
    public void deleteGrant(UUID grant) {
        String failure = "Unable to delete the ability choice " + grant;
        update(failure, DELETE_GRANT_CHOICES, grant.toString());
        update(failure, DELETE_GRANT, grant.toString());
    }

    @Override
    public void saveChoice(UUID grant, String ability) {
        update("Unable to save a choice of ability choice " + grant, SAVE_CHOICE, grant.toString(), grant.toString(),
                ability);
    }

    @Override
    public void deleteChoice(UUID grant, String ability) {
        update("Unable to delete a choice of ability choice " + grant, DELETE_CHOICE, grant.toString(), ability);
    }

    @Override
    public void deleteAll(UUID npc) {
        String failure = "Unable to delete the ability choices of NPC " + npc;
        update(failure, DELETE_ALL_CHOICES, npc.toString());
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
