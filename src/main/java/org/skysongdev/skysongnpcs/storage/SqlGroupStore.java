package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.groups.GroupMember;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The {@code snpc_groups} table (one row per player group, its owner like {@code snpc_npcs}
 * keeps one: no player for the server) and {@code snpc_group_members} (one row per member, an
 * empty profile for every character of the player). The pool belongs to the NPC store, so
 * closing this does nothing. {@link QueuedGroupStore} moves the writes off the main thread.
 */
public final class SqlGroupStore implements GroupStore {
    static final String CREATE_GROUPS = """
            CREATE TABLE IF NOT EXISTS snpc_groups (
              id VARCHAR(32) NOT NULL PRIMARY KEY,
              owner_player CHAR(36) NULL,
              owner_profile VARCHAR(64) NULL
            )""";
    static final String CREATE_MEMBERS = """
            CREATE TABLE IF NOT EXISTS snpc_group_members (
              group_id VARCHAR(32) NOT NULL,
              player CHAR(36) NOT NULL,
              profile VARCHAR(64) NOT NULL,
              PRIMARY KEY (group_id, player, profile)
            )""";
    private static final String LOAD_GROUPS = "SELECT id, owner_player, owner_profile FROM snpc_groups";
    private static final String LOAD_MEMBERS = "SELECT group_id, player, profile FROM snpc_group_members";
    private static final String SAVE_GROUP = "INSERT INTO snpc_groups (id, owner_player, owner_profile) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE owner_player = VALUES(owner_player), owner_profile = VALUES(owner_profile)";
    private static final String DELETE_MEMBERS = "DELETE FROM snpc_group_members WHERE group_id = ?";
    private static final String ADD_MEMBER = "INSERT INTO snpc_group_members (group_id, player, profile) VALUES (?, ?, ?)";
    private static final String DELETE_GROUP = "DELETE FROM snpc_groups WHERE id = ?";
    /** The profile column of a member that stands for every character of the player. */
    private static final String EVERY_CHARACTER = "";

    private final DataSource dataSource;
    private final Consumer<String> log;

    /**
     * Creates the tables when they are missing.
     *
     * @param log hears about rows that cannot be read, which are skipped
     */
    public SqlGroupStore(DataSource dataSource, Consumer<String> log) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.log = Objects.requireNonNull(log, "log");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_GROUPS);
            statement.execute(CREATE_MEMBERS);
        } catch (SQLException e) {
            throw new StorageException("Unable to create the snpc_groups tables", e);
        }
    }

    @Override
    public List<PlayerGroup> loadAll() {
        Map<String, NpcOwner> owners = new LinkedHashMap<>();
        Map<String, Set<GroupMember>> members = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(LOAD_GROUPS);
                 ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String id = results.getString("id");
                    try {
                        String player = results.getString("owner_player");
                        owners.put(id, player == null ? NpcOwner.server()
                                : NpcOwner.character(UUID.fromString(player), results.getString("owner_profile")));
                    } catch (IllegalArgumentException | NullPointerException unreadable) {
                        log.accept("Skipped group " + id + ", whose owner could not be read: " + unreadable.getMessage());
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(LOAD_MEMBERS);
                 ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String id = results.getString("group_id");
                    try {
                        String profile = results.getString("profile");
                        members.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(new GroupMember(
                                UUID.fromString(results.getString("player")),
                                profile.equals(EVERY_CHARACTER) ? null : profile));
                    } catch (IllegalArgumentException unreadable) {
                        log.accept("Skipped a member of group " + id + " that could not be read: " + unreadable.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the player groups", e);
        }
        List<PlayerGroup> groups = new ArrayList<>();
        owners.forEach((id, owner) -> {
            try {
                groups.add(new PlayerGroup(id, owner, members.getOrDefault(id, Set.of())));
            } catch (IllegalArgumentException unreadable) {
                log.accept("Skipped group " + id + ": " + unreadable.getMessage());
            }
        });
        return groups;
    }

    @Override
    public void save(PlayerGroup group) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(SAVE_GROUP)) {
                    statement.setString(1, group.id());
                    if (group.owner() instanceof NpcOwner.CharacterOwner character) {
                        statement.setString(2, character.player().toString());
                        statement.setString(3, character.profile());
                    } else {
                        statement.setString(2, null);
                        statement.setString(3, null);
                    }
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(DELETE_MEMBERS)) {
                    statement.setString(1, group.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(ADD_MEMBER)) {
                    for (GroupMember member : group.members()) {
                        statement.setString(1, group.id());
                        statement.setString(2, member.player().toString());
                        statement.setString(3, member.profile() == null ? EVERY_CHARACTER : member.profile());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to save group " + group.id(), e);
        }
    }

    @Override
    public void delete(String id) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(DELETE_MEMBERS)) {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(DELETE_GROUP)) {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to delete group " + id, e);
        }
    }

    @Override
    public void close() {
    }
}
