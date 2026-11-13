package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.picks.PickGrant;
import org.skysongdev.skysongnpcs.picks.PickPromotion;
import org.skysongdev.skysongnpcs.picks.PickSpend;
import org.skysongdev.skysongnpcs.picks.PromotionSpend;

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
 * The {@code snpc_pick_grants} and {@code snpc_pick_spends} tables, and since 0.22.0
 * {@code snpc_pick_promotion_rules} (a grant's promotion, one row per grant that has one) and
 * {@code snpc_pick_promotions} (the promotions made). The promotion tables are new tables
 * rather than new columns, so existing tables need no migration. A spend row names its
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
    static final String CREATE_PROMOTION_RULES = """
            CREATE TABLE IF NOT EXISTS snpc_pick_promotion_rules (
              grant_id CHAR(36) NOT NULL PRIMARY KEY,
              npc CHAR(36) NOT NULL,
              max_tier VARCHAR(64) NOT NULL,
              cost INT NOT NULL
            )""";
    static final String CREATE_PROMOTIONS = """
            CREATE TABLE IF NOT EXISTS snpc_pick_promotions (
              grant_id CHAR(36) NOT NULL,
              step INT NOT NULL,
              npc CHAR(36) NOT NULL,
              proficiency VARCHAR(64) NOT NULL,
              from_tier VARCHAR(64) NOT NULL,
              to_tier VARCHAR(64) NOT NULL,
              cost INT NOT NULL,
              PRIMARY KEY (grant_id, step)
            )""";
    private static final String LOAD_GRANTS = "SELECT g.id, g.npc, g.source, g.skill, g.tier, g.pick_count, "
            + "r.max_tier, r.cost FROM snpc_pick_grants g LEFT JOIN snpc_pick_promotion_rules r ON r.grant_id = g.id";
    private static final String SAVE_RULE = "INSERT INTO snpc_pick_promotion_rules (grant_id, npc, max_tier, cost) "
            + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE max_tier = VALUES(max_tier), cost = VALUES(cost)";
    private static final String DELETE_RULE = "DELETE FROM snpc_pick_promotion_rules WHERE grant_id = ?";
    private static final String LOAD_PROMOTIONS = "SELECT grant_id, step, proficiency, from_tier, to_tier, cost "
            + "FROM snpc_pick_promotions ORDER BY grant_id, step";
    private static final String SAVE_PROMOTION = "INSERT INTO snpc_pick_promotions "
            + "(grant_id, step, npc, proficiency, from_tier, to_tier, cost) "
            + "VALUES (?, ?, (SELECT npc FROM snpc_pick_grants WHERE id = ?), ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE proficiency = VALUES(proficiency), from_tier = VALUES(from_tier), "
            + "to_tier = VALUES(to_tier), cost = VALUES(cost)";
    private static final String DELETE_PROMOTION = "DELETE FROM snpc_pick_promotions WHERE grant_id = ? AND step = ?";
    private static final String DELETE_GRANT_PROMOTIONS = "DELETE FROM snpc_pick_promotions WHERE grant_id = ?";
    private static final String DELETE_ALL_PROMOTIONS = "DELETE FROM snpc_pick_promotions WHERE npc = ?";
    private static final String DELETE_ALL_RULES = "DELETE FROM snpc_pick_promotion_rules WHERE npc = ?";
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
            statement.execute(CREATE_PROMOTION_RULES);
            statement.execute(CREATE_PROMOTIONS);
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
                    String maxTier = results.getString("max_tier");
                    PickPromotion promotion = maxTier == null ? null : new PickPromotion(maxTier, results.getInt("cost"));
                    grants.add(new PickGrant(UUID.fromString(id), UUID.fromString(results.getString("npc")),
                            results.getString("source"), results.getString("skill"), results.getString("tier"),
                            results.getInt("pick_count"), promotion));
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
    public List<PromotionSpend> loadPromotions() {
        List<PromotionSpend> promotions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOAD_PROMOTIONS);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String grant = results.getString("grant_id");
                try {
                    promotions.add(new PromotionSpend(UUID.fromString(grant), results.getInt("step"),
                            results.getString("proficiency"), results.getString("from_tier"),
                            results.getString("to_tier"), results.getInt("cost")));
                } catch (IllegalArgumentException unreadable) {
                    log.accept("Skipped a promotion of proficiency pick grant " + grant + " that could not be read: "
                            + unreadable.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Unable to load the proficiency promotions", e);
        }
        return promotions;
    }

    @Override
    public void saveGrant(PickGrant grant) {
        String failure = "Unable to save the proficiency pick grant " + grant.id();
        update(failure, SAVE_GRANT, grant.id().toString(),
                grant.npc().toString(), grant.source(), grant.skill(), grant.tier(), grant.count());
        if (grant.promotion() == null) {
            update(failure, DELETE_RULE, grant.id().toString());
        } else {
            update(failure, SAVE_RULE, grant.id().toString(), grant.npc().toString(), grant.promotion().tier(),
                    grant.promotion().cost());
        }
    }

    @Override
    public void deleteGrant(UUID grant) {
        String failure = "Unable to delete the proficiency pick grant " + grant;
        update(failure, DELETE_GRANT_PROMOTIONS, grant.toString());
        update(failure, DELETE_RULE, grant.toString());
        update(failure, DELETE_GRANT_SPENDS, grant.toString());
        update(failure, DELETE_GRANT, grant.toString());
    }

    @Override
    public void savePromotion(PromotionSpend promotion) {
        update("Unable to save a promotion of proficiency pick grant " + promotion.grant(), SAVE_PROMOTION,
                promotion.grant().toString(), promotion.step(), promotion.grant().toString(), promotion.proficiency(),
                promotion.fromTier(), promotion.toTier(), promotion.cost());
    }

    @Override
    public void deletePromotion(UUID grant, int step) {
        update("Unable to delete a promotion of proficiency pick grant " + grant, DELETE_PROMOTION,
                grant.toString(), step);
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
        update(failure, DELETE_ALL_PROMOTIONS, npc.toString());
        update(failure, DELETE_ALL_RULES, npc.toString());
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
