package org.skysongdev.skysongnpcs.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.function.Consumer;

/** Opens the MySQL connection pool and the NPC table behind it. */
public final class Database {
    static final int MAXIMUM_POOL_SIZE = 3;

    private Database() {
    }

    /**
     * Connects, creates the table when it is missing, and returns a store whose writes run in
     * the background. Every failed write goes to {@code log}.
     *
     * @throws RuntimeException when MySQL cannot be reached; the plugin cannot run without it
     */
    public static NpcStore open(DatabaseSettings settings, Consumer<String> log) {
        HikariDataSource pool = new HikariDataSource(config(settings));
        try {
            return new QueuedNpcStore(new SqlNpcStore(pool, pool::close),
                    failed -> log.accept("Could not save an NPC change: " + failed.getMessage()));
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
        // One background writer and a load on enable need very few connections.
        config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        // Fail on enable, not on the first write, when MySQL cannot be reached.
        config.setInitializationFailTimeout(1);
        return config;
    }
}
