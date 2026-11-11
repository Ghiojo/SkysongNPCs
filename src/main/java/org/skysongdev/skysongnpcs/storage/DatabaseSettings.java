package org.skysongdev.skysongnpcs.storage;

/**
 * The MySQL connection from the {@code database:} section of config.yml, the same keys
 * SkysongStats uses. Read once on enable: {@code /snpc reload} does not reconnect.
 */
public record DatabaseSettings(String host, String port, String database, String user, String password) {

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    /** Keeps the password out of logs and error messages. */
    @Override
    public String toString() {
        return "DatabaseSettings[" + user + "@" + host + ":" + port + "/" + database + "]";
    }
}
