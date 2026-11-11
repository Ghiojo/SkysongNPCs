package org.skysongdev.skysongnpcs.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.function.Consumer;

/** Opens the MySQL connection pool and the tables behind it. */
public final class Database {
    static final int MAXIMUM_POOL_SIZE = 3;

    private Database() {
    }

    /**
     * Connects, creates the tables when they are missing, and returns stores whose writes run
     * in the background. Every failed write goes to {@code log}.
     *
     * @throws RuntimeException when MySQL cannot be reached; the plugin cannot run without it
     */
    public static Stores open(DatabaseSettings settings, Consumer<String> log) {
        HikariDataSource pool = new HikariDataSource(config(settings));
        try {
            // Every table first, so a failure leaves no writer thread behind.
            SqlNpcStore npcTable = new SqlNpcStore(pool, pool::close);
            SqlGrantStore grantTable = new SqlGrantStore(pool, log);
            SqlPolicyStore policyTables = new SqlPolicyStore(pool, log);
            // M4
            SqlGroupStore groupTable = new SqlGroupStore(pool, log);
            SqlNpcItemStore itemTable = new SqlNpcItemStore(pool, log);
            // M14
            SqlChoiceStore choiceTables = new SqlChoiceStore(pool, log);
            SqlPickStore pickTables = new SqlPickStore(pool, log);
            NpcStore npcs = new QueuedNpcStore(npcTable,
                    failed -> log.accept("Could not save an NPC change: " + failed.getMessage()));
            GrantStore grants = new QueuedGrantStore(grantTable,
                    failed -> log.accept("Could not save an access change: " + failed.getMessage()));
            PolicyStore policies = new QueuedPolicyStore(policyTables,
                    failed -> log.accept("Could not save a policy change: " + failed.getMessage()));
            // M4
            GroupStore groups = new QueuedGroupStore(groupTable,
                    failed -> log.accept("Could not save a group change: " + failed.getMessage()));
            NpcItemStore items = new QueuedNpcItemStore(itemTable,
                    failed -> log.accept("Could not save an NPC item change: " + failed.getMessage()));
            // M14
            ChoiceStore choices = new QueuedChoiceStore(choiceTables,
                    failed -> log.accept("Could not save a choice change: " + failed.getMessage()));
            PickStore picks = new QueuedPickStore(pickTables,
                    failed -> log.accept("Could not save a proficiency pick change: " + failed.getMessage()));
            return new Stores(npcs, grants, policies, groups, items, choices, picks);
        } catch (RuntimeException failed) {
            pool.close();
            throw failed;
        }
    }

    static HikariConfig config(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("SkysongNPCs");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        // A few background writers and a load on enable need very few connections.
        config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        // Fail on enable, not on the first write, when MySQL cannot be reached.
        config.setInitializationFailTimeout(1);
        return config;
    }
}
