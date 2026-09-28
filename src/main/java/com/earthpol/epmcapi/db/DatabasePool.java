package com.earthpol.epmcapi.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

final class DatabasePool {
    private final String name;
    private volatile HikariDataSource source;

    DatabasePool(String name) { this.name = name; }

    synchronized void start(String host, String port, String database, String user, String pass) {
        if (source != null) return;
        HikariConfig config = new HikariConfig();
        config.setPoolName("EPMCAPI-" + name);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?connectTimeout=3000&socketTimeout=5000&cachePrepStmts=true");
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setInitializationFailTimeout(1);
        config.setReadOnly(true);
        source = new HikariDataSource(config);
    }

    boolean isReady() {
        HikariDataSource current = source;
        return current != null && !current.isClosed();
    }

    Connection connection() throws SQLException {
        HikariDataSource current = source;
        if (current == null || current.isClosed()) throw new SQLException("Database unavailable");
        return current.getConnection();
    }

    synchronized void close() {
        if (source != null) { source.close(); source = null; }
    }
}
