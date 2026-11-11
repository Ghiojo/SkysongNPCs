package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Three tables: {@code snpc_policies} (id, owner), {@code snpc_policy_grants} (one row per
 * policy and group, actions as a comma list of ids) and {@code snpc_policy_members} (one row
 * per NPC that follows a policy). The pool belongs to the NPC store, so closing this does
 * nothing. Every call runs on the caller's thread; {@link QueuedPolicyStore} moves the writes
 * off the main thread.
 */
public final class SqlPolicyStore implements PolicyStore {
    static final List<String> CREATE_TABLES = List.of("""
            CREATE TABLE IF NOT EXISTS snpc_policies (
              id VARCHAR(64) NOT NULL PRIMARY KEY,
              owner VARCHAR(191) NOT NULL,
              created BIGINT NOT NULL
            )""", """
            CREATE TABLE IF NOT EXISTS snpc_policy_grants (
              policy VARCHAR(64) NOT NULL,
              member_group VARCHAR(191) NOT NULL,
              actions VARCHAR(512) NOT NULL,
              PRIMARY KEY (policy, member_group)
            )""", """
            CREATE TABLE IF NOT EXISTS snpc_policy_members (
              npc CHAR(36) NOT NULL PRIMARY KEY,
              policy VARCHAR(64) NOT NULL
            )""");
    private static final String LOAD_POLICIES = "SELECT id, owner FROM snpc_policies ORDER BY created, id";
    private static final String LOAD_GRANTS = "SELECT policy, member_group, actions FROM snpc_policy_grants";
    private static final String LOAD_MEMBERS = "SELECT npc, policy FROM snpc_policy_members";
    private static final String SAVE_POLICY = "INSERT INTO snpc_policies (id, owner, created) VALUES (?, ?, ?)";
    private static final List<String> DELETE_POLICY = List.of(
            "DELETE FROM snpc_policy_members WHERE policy = ?",
            "DELETE FROM snpc_policy_grants WHERE policy = ?",
            "DELETE FROM snpc_policies WHERE id = ?");
    private static final String SAVE_GRANT = "INSERT INTO snpc_policy_grants (policy, member_group, actions) "
            + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE actions = VALUES(actions)";
    private static final String DELETE_GRANT = "DELETE FROM snpc_policy_grants WHERE policy = ? AND member_group = ?";
    private static final String SET_MEMBER = "INSERT INTO snpc_policy_members (npc, policy) VALUES (?, ?) "
            + "ON DUPLICATE KEY UPDATE policy = VALUES(policy)";
    private static final String REMOVE_MEMBER = "DELETE FROM snpc_policy_members WHERE npc = ?";
    private static final String CHARACTER = "character:";
    private static final String PLUGIN = "plugin:";

    private final DataSource dataSource;
    private final Consumer<String> log;

    /**
     * Creates the tables when they are missing.
     *
     * @param log hears about rows that cannot be read, which are skipped
     */
    public SqlPolicyStore(DataSource dataSource, Consumer<String> log) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.log = Objects.requireNonNull(log, "log");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String create : CREATE_TABLES) {
                statement.execute(create);
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to create the snpc_policy tables", e);
        }
    }

    @Override
    public Snapshot loadAll() {
        Map<String, PolicyOwner> owners = new LinkedHashMap<>();
        Map<String, Map<AccessGroup, Set<NpcAction>>> grants = new HashMap<>();
        Map<UUID, String> members = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(LOAD_POLICIES);
                 ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String id = results.getString("id");
                    try {
                        owners.put(id, owner(results.getString("owner")));
                    } catch (IllegalArgumentException unreadable) {
                        log.accept("Skipped policy " + id + ", whose owner could not be read: " + unreadable.getMessage());
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(LOAD_GRANTS);
                 ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String policy = results.getString("policy");
                    Set<NpcAction> actions = actions(results.getString("actions"));
                    try {
                        AccessGroup group = AccessGroup.parse(results.getString("member_group"));
                        if (actions.isEmpty()) {
                            throw new IllegalArgumentException("no known action");
                        }
                        grants.computeIfAbsent(policy, ignored -> new LinkedHashMap<>()).put(group, actions);
                    } catch (IllegalArgumentException unreadable) {
                        log.accept("Skipped a grant of policy " + policy + " that could not be read: "
                                + unreadable.getMessage());
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(LOAD_MEMBERS);
                 ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String npc = results.getString("npc");
                    try {
                        members.put(UUID.fromString(npc), results.getString("policy"));
                    } catch (IllegalArgumentException unreadable) {
                        log.accept("Skipped NPC " + npc + "'s policy, which could not be read.");
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the access policies", e);
        }
        List<StoredPolicy> policies = new ArrayList<>();
        owners.forEach((id, owner) -> policies.add(new StoredPolicy(id, owner, grants.getOrDefault(id, Map.of()))));
        members.values().removeIf(policy -> !owners.containsKey(policy));
        return new Snapshot(policies, members);
    }

    @Override
    public void savePolicy(String id, PolicyOwner owner) {
        update(SAVE_POLICY, "save policy " + id, id, key(owner), System.currentTimeMillis());
    }

    @Override
    public void deletePolicy(String id) {
        try (Connection connection = dataSource.getConnection()) {
            for (String sql : DELETE_POLICY) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to delete policy " + id, e);
        }
    }

    @Override
    public void saveGrant(String policy, AccessGroup group, Set<NpcAction> actions) {
        update(SAVE_GRANT, "save a grant of policy " + policy, policy, group.toString(),
                actions.stream().map(NpcAction::key).collect(Collectors.joining(",")));
    }

    @Override
    public void deleteGrant(String policy, AccessGroup group) {
        update(DELETE_GRANT, "delete a grant of policy " + policy, policy, group.toString());
    }

    @Override
    public void setMember(UUID npc, String policy) {
        update(SET_MEMBER, "save NPC " + npc + "'s policy", npc.toString(), policy);
    }

    @Override
    public void removeMember(UUID npc) {
        update(REMOVE_MEMBER, "delete NPC " + npc + "'s policy", npc.toString());
    }

    @Override
    public void close() {
    }

    private void update(String sql, String what, Object... parameters) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to " + what, e);
        }
    }

    /** {@code character:<uuid>/<profile>} or {@code plugin:<name>}. */
    static String key(PolicyOwner owner) {
        return switch (owner) {
            case PolicyOwner.CharacterOwner character -> CHARACTER + character.player() + "/" + character.profile();
            case PolicyOwner.PluginOwner plugin -> PLUGIN + plugin.plugin();
        };
    }

    static PolicyOwner owner(String key) {
        if (key.startsWith(CHARACTER)) {
            String rest = key.substring(CHARACTER.length());
            int slash = rest.indexOf('/');
            if (slash < 0) {
                throw new IllegalArgumentException("no profile in " + key);
            }
            return PolicyOwner.character(UUID.fromString(rest.substring(0, slash)), rest.substring(slash + 1));
        }
        if (key.startsWith(PLUGIN)) {
            return new PolicyOwner.PluginOwner(key.substring(PLUGIN.length()));
        }
        throw new IllegalArgumentException("unknown owner " + key);
    }

    /** Ids no longer known (an action removed in an update) are dropped. */
    private static Set<NpcAction> actions(String keys) {
        Set<NpcAction> actions = EnumSet.noneOf(NpcAction.class);
        for (String key : keys.split(",")) {
            NpcAction.fromKey(key).ifPresent(actions::add);
        }
        return actions;
    }
}
