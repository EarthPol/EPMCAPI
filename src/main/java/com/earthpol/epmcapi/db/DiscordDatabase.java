package com.earthpol.epmcapi.db;

import java.sql.Connection;
import java.sql.SQLException;

public final class DiscordDatabase {
    private static final DatabasePool pool = new DatabasePool("DiscordDatabase");
    private DiscordDatabase() {}

    public static void init(String host, String port, String database, String user, String pass) {
        pool.start(host, port, database, user, pass);
    }

    public static Connection getConnection() throws SQLException { return pool.connection(); }
    public static boolean isReady() { return pool.isReady(); }
    public static void close() { pool.close(); }
}
