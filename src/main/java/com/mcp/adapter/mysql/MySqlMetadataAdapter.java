package com.mcp.adapter.mysql;

import com.mcp.adapter.spi.*;
import com.mcp.infrastructure.datasource.DatasourcePermissionException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 元数据查询适配器，通过 {@code information_schema} 系统视图获取表、列、索引、视图信息。
 *
 * <p>所有查询均使用 PreparedStatement + TABLE_SCHEMA 参数化过滤，
 * 确保只返回当前连接数据库的元数据，不跨库泄露信息。
 *
 * <p>权限不足（错误码 1142/1143 等）时，通过 {@link #throwIfPermissionDenied} 统一包装为
 * {@link com.mcp.infrastructure.datasource.DatasourcePermissionException}，携带 GRANT 修复建议。
 * @author ouyanghang
 */
public class MySqlMetadataAdapter implements MetadataAdapter {

    @Override
    public List<TableInfo> listTables(Connection conn) throws Exception {
        List<TableInfo> result = new ArrayList<>();
        String db = conn.getCatalog();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = "VIEW".equals(rs.getString("TABLE_TYPE")) ? "VIEW" : "TABLE";
                    result.add(TableInfo.builder()
                            .name(rs.getString("TABLE_NAME"))
                            .tableType(type)
                            .build());
                }
            }
        } catch (SQLException e) {
            throwIfPermissionDenied(conn, e);
            throw e;
        }
        return result;
    }

    @Override
    public ObjectDetail describeObject(Connection conn, String objectName, String objectType) throws Exception {
        String db = conn.getCatalog();
        List<ColumnInfo> columns = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, " +
                "COLUMN_KEY, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, db);
            ps.setString(2, objectName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(ColumnInfo.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .dataType(rs.getString("DATA_TYPE"))
                            .length(toIntOrNull(rs.getObject("CHARACTER_MAXIMUM_LENGTH")))
                            .precision(toIntOrNull(rs.getObject("NUMERIC_PRECISION")))
                            .primaryKey("PRI".equals(rs.getString("COLUMN_KEY")))
                            .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                            .defaultValue(rs.getString("COLUMN_DEFAULT"))
                            .comment(rs.getString("COLUMN_COMMENT"))
                            .build());
                }
            }
        } catch (SQLException e) {
            throwIfPermissionDenied(conn, e);
            throw e;
        }

        if (columns.isEmpty()) {
            return null;
        }

        String resolvedType = resolveObjectType(conn, db, objectName, objectType);
        return ObjectDetail.builder()
                .objectName(objectName)
                .objectType(resolvedType)
                .tableType(resolvedType)
                .columns(columns)
                .build();
    }

    @Override
    public List<IndexInfo> listIndexes(Connection conn) throws Exception {
        String db = conn.getCatalog();
        List<IndexInfo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT INDEX_NAME, TABLE_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLS " +
                "FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = ? AND INDEX_NAME != 'PRIMARY' " +
                "GROUP BY INDEX_NAME, TABLE_NAME, NON_UNIQUE ORDER BY TABLE_NAME, INDEX_NAME")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(IndexInfo.builder()
                            .indexName(rs.getString("INDEX_NAME"))
                            .tableName(rs.getString("TABLE_NAME"))
                            .unique(rs.getInt("NON_UNIQUE") == 0)
                            .columns(rs.getString("COLS"))
                            .build());
                }
            }
        } catch (SQLException e) {
            throwIfPermissionDenied(conn, e);
            throw e;
        }
        return result;
    }

    @Override
    public List<ViewInfo> listViews(Connection conn) throws Exception {
        String db = conn.getCatalog();
        List<ViewInfo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME, VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(ViewInfo.builder()
                            .viewName(rs.getString("TABLE_NAME"))
                            .definition(rs.getString("VIEW_DEFINITION"))
                            .build());
                }
            }
        } catch (SQLException e) {
            throwIfPermissionDenied(conn, e);
            throw e;
        }
        return result;
    }

    /**
     * 权限检测辅助方法：若异常为权限类错误则封装为 {@link DatasourcePermissionException} 抛出，否则静默返回。
     * 通过 {@code conn.getMetaData().getUserName()} 和 {@code conn.getCatalog()} 补充 GRANT hint 所需的
     * 用户名和数据库名。
     */
    private void throwIfPermissionDenied(Connection conn, SQLException e) throws DatasourcePermissionException {
        if (!MySqlPermissionErrors.isPermissionDenied(e)) return;
        String username = null;
        String database = null;
        try {
            username = conn.getMetaData().getUserName();
            database = conn.getCatalog();
        } catch (SQLException ignored) {}
        String hint = MySqlPermissionErrors.buildGrantHint("", username, database);
        throw new DatasourcePermissionException("", e.getMessage(), hint);
    }

    private String resolveObjectType(Connection conn, String db, String name, String hint) throws Exception {
        if ("VIEW".equalsIgnoreCase(hint)) return "VIEW";
        if ("TABLE".equalsIgnoreCase(hint)) return "TABLE";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            ps.setString(1, db);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "VIEW".equals(rs.getString("TABLE_TYPE")) ? "VIEW" : "TABLE";
                }
            }
        }
        return "TABLE";
    }

    private Integer toIntOrNull(Object val) {
        if (val == null) return null;
        return ((Number) val).intValue();
    }
}


