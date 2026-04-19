package com.mcp.adapter.mysql;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ouyanghang
 */
public final class MySqlPermissionErrors {

    private static final Pattern USER_PATTERN = Pattern.compile("Access denied for user '([^']+)'");

    private MySqlPermissionErrors() {}

    public static boolean isPermissionDenied(SQLException e) {
        int code = e.getErrorCode();
        if (code == 1044 || code == 1045 || code == 1142 || code == 1143) return true;
        String state = e.getSQLState();
        return "28000".equals(state);
    }

    public static String buildGrantHint(String datasourceName, String username, String database) {
        String db = (database != null && !database.isEmpty()) ? database : "<database>";
        String user = (username != null && !username.isEmpty()) ? username : "<username>";
        return String.format(
            "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON %s.* TO '%s'@'%%'; FLUSH PRIVILEGES;",
            db, user);
    }

    public static String extractUsername(SQLException e) {
        if (e.getMessage() == null) return null;
        Matcher m = USER_PATTERN.matcher(e.getMessage());
        return m.find() ? m.group(1) : null;
    }

    /** Extract database name from JDBC URL: jdbc:mysql://host:port/dbname?... */
    public static String extractDatabase(String jdbcUrl) {
        if (jdbcUrl == null) return null;
        int slash = jdbcUrl.lastIndexOf('/');
        if (slash < 0) return null;
        String rest = jdbcUrl.substring(slash + 1);
        int q = rest.indexOf('?');
        return q >= 0 ? rest.substring(0, q) : rest;
    }
}

