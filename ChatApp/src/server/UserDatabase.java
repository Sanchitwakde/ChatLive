package server;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserDatabase {

    private static final List<String> TABLE_CANDIDATES = Arrays.asList(
            "users", "user", "accounts", "account", "login", "credentials"
    );

    private static final List<String> USER_CANDIDATES = Arrays.asList(
            "username", "user_name", "email", "useremail", "login", "userid", "user_id"
    );

    private static final List<String> PASSWORD_CANDIDATES = Arrays.asList(
            "password", "pass", "pwd", "passwd"
    );

    private static final ThreadLocal<String> LAST_AUTH_ERROR = new ThreadLocal<>();

    public static boolean authenticate(String username, String password) {
        String loginId = username == null ? "" : username.trim();
        String secret = password == null ? "" : password.trim();
        LAST_AUTH_ERROR.remove();

        if (loginId.isEmpty() || secret.isEmpty()) {
            setAuthError("empty username/email or password");
            System.out.println("Login rejected: empty username/email or password");
            return false;
        }

        try {
            Connection conn = DatabaseConnection.getConnection();
            AuthSpec spec = resolveAuthSpec(conn);

            if (spec == null) {
                setAuthError("could not find a usable auth table/columns");
                System.out.println("Login failed: could not find a usable auth table/columns.");
                dumpTables(conn);
                return false;
            }

            String sql = "SELECT 1 FROM " + spec.tableName + " WHERE " + spec.userColumn + " = ? AND " + spec.passwordColumn + " = ? LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, loginId);
                ps.setString(2, secret);

                try (ResultSet rs = ps.executeQuery()) {
                    boolean ok = rs.next();
                    System.out.println("Login attempt using " + spec.tableName + "." + spec.userColumn + "/" + spec.passwordColumn + " for " + loginId + " -> " + ok);
                    if (!ok) {
                        setAuthError("no matching row in " + spec.tableName + " for the values you typed");
                        System.out.println("No matching row found. Check the exact saved value and whether the password is plain text.");
                    }
                    return ok;
                }
            }
        } catch (Exception e) {
            setAuthError(e.getMessage() == null ? "database error" : e.getMessage());
            System.out.println("Login failed for " + loginId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static String getLastAuthError() {
        return LAST_AUTH_ERROR.get();
    }

    private static AuthSpec resolveAuthSpec(Connection conn) throws SQLException {
        String tableOverride = envOrProp("CHATAPP_AUTH_TABLE", "chatapp.auth.table", null);
        String userOverride = envOrProp("CHATAPP_AUTH_USER_COLUMN", "chatapp.auth.userColumn", null);
        String passOverride = envOrProp("CHATAPP_AUTH_PASSWORD_COLUMN", "chatapp.auth.passwordColumn", null);

        if (tableOverride != null && userOverride != null && passOverride != null) {
            if (hasColumn(conn, tableOverride, userOverride) && hasColumn(conn, tableOverride, passOverride)) {
                return new AuthSpec(tableOverride, userOverride, passOverride);
            }
        }

        String tableName = findTable(conn);
        if (tableName == null) {
            return null;
        }

        String userColumn = userOverride != null ? userOverride : findColumn(conn, tableName, USER_CANDIDATES);
        String passwordColumn = passOverride != null ? passOverride : findColumn(conn, tableName, PASSWORD_CANDIDATES);

        if (userColumn == null || passwordColumn == null) {
            System.out.println("Login schema mismatch in table " + tableName + ": userColumn=" + userColumn + ", passwordColumn=" + passwordColumn);
            return null;
        }

        return new AuthSpec(tableName, userColumn, passwordColumn);
    }

    private static String findTable(Connection conn) throws SQLException {
        String tableOverride = envOrProp("CHATAPP_AUTH_TABLE", "chatapp.auth.table", null);
        if (tableOverride != null && !tableOverride.isBlank()) {
            return tableOverride;
        }

        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[] { "TABLE" })) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                for (String candidate : TABLE_CANDIDATES) {
                    if (candidate.equalsIgnoreCase(table)) {
                        return table;
                    }
                }
            }
        }
        return null;
    }

    private static String findColumn(Connection conn, String tableName, List<String> candidates) throws SQLException {
        for (String candidate : candidates) {
            if (hasColumn(conn, tableName, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static String envOrProp(String envKey, String propKey, String defaultValue) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v;
        v = System.getProperty(propKey);
        if (v != null && !v.isBlank()) return v;
        return defaultValue;
    }

    private static void setAuthError(String message) {
        LAST_AUTH_ERROR.set(message);
    }

    private static void dumpTables(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[] { "TABLE" })) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            System.out.println("Visible tables: " + tables);
        } catch (Exception e) {
            System.out.println("Unable to list tables: " + e.getMessage());
        }
    }

    private static final class AuthSpec {
        private final String tableName;
        private final String userColumn;
        private final String passwordColumn;

        private AuthSpec(String tableName, String userColumn, String passwordColumn) {
            this.tableName = tableName;
            this.userColumn = userColumn;
            this.passwordColumn = passwordColumn;
        }
    }
}
