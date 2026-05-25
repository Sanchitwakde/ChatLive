package server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static Connection conn;

    public static Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            // Configure via env vars (or -D system properties) so you don't have to hardcode secrets.
            // Defaults match the previous sample values in this file.
            String url  = envOrProp(
                    "CHATAPP_DB_URL",
                    "chatapp.db.url",
                    "jdbc:mysql://localhost:3306/chatapp?useSSL=false&serverTimezone=UTC"
            );
            String user = envOrProp("CHATAPP_DB_USER", "chatapp.db.user", "toor");
            String pass = envOrProp("CHATAPP_DB_PASS", "chatapp.db.pass", "demo");
            conn = DriverManager.getConnection(url, user, pass);
        }
        return conn;
    }

    private static String envOrProp(String envKey, String propKey, String defaultValue) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v;
        v = System.getProperty(propKey);
        if (v != null && !v.isBlank()) return v;
        return defaultValue;
    }
}
