package com.mcp.adapter.mysql;

import com.mcp.adapter.spi.QueryExecutor;
import com.mcp.infrastructure.datasource.DatasourcePermissionException;

import java.sql.*;
import java.util.*;

/**
 * MySQL 只读查询执行器，实现 SELECT 查询和 EXPLAIN 分析。
 *
 * <p>关键设计点：
 * <ul>
 *   <li>通过 {@code stmt.setMaxRows(maxRows + 1)} 在 JDBC 层截断结果，而非全量读取后裁剪，
 *       避免大结果集导致 JVM 堆溢出。取 +1 行用于判断是否截断。</li>
 *   <li>超时由 {@code stmt.setQueryTimeout} 控制；超时后 JDBC 驱动 cancel 语句，
 *       MySQL 服务端不会继续占用 CPU。</li>
 *   <li>权限相关 SQLException（错误码 1044/1045/1142/1143 或 SQLState 28000）
 *       被包装为 {@link DatasourcePermissionException}，携带 GRANT 修复建议。</li>
 * </ul>
 * @author ouyanghang
 */
public class MySqlQueryExecutor implements QueryExecutor {

    @Override
    public QueryResult executeQuery(Connection conn, String sql, int maxRows, int timeoutSeconds) throws Exception {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(timeoutSeconds);
            // 多取 1 行用于判断是否存在更多行（truncated 检测），不会全量读入内存
            stmt.setMaxRows(maxRows + 1);

            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                String[] colNames = new String[colCount];
                for (int i = 1; i <= colCount; i++) {
                    colNames[i - 1] = meta.getColumnLabel(i);
                }

                int count = 0;
                while (rs.next()) {
                    if (count >= maxRows) {
                        long dbCostMs = System.currentTimeMillis() - start;
                        return QueryResult.builder()
                                .rows(rows)
                                .returnedRows(rows.size())
                                .totalRows(null)
                                .truncated(true)
                                .dbCostMs(dbCostMs)
                                .build();
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < colCount; i++) {
                        row.put(colNames[i], rs.getObject(i + 1));
                    }
                    rows.add(row);
                    count++;
                }
            }
        } catch (SQLTimeoutException e) {
            // setQueryTimeout 超时后驱动取消语句，避免数据库侧继续消耗资源
            throw new SQLException("Query timed out after " + timeoutSeconds + "s. "
                    + "The statement has been cancelled.", "QUERY_TIMEOUT", e);
        } catch (SQLException e) {
            if (MySqlPermissionErrors.isPermissionDenied(e)) {
                throw new DatasourcePermissionException("", e.getMessage(),
                        buildHint(conn, e));
            }
            throw e;
        }

        long dbCostMs = System.currentTimeMillis() - start;
        return QueryResult.builder()
                .rows(rows)
                .returnedRows(rows.size())
                .totalRows((long) rows.size())
                .truncated(false)
                .dbCostMs(dbCostMs)
                .build();
    }

    @Override
    public ExplainResult explainQuery(Connection conn, String sql, int timeoutSeconds) throws Exception {
        long start = System.currentTimeMillis();
        StringBuilder plan = new StringBuilder();
        List<String> indexesUsed = new ArrayList<>();
        Long estimatedRows = null;

        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(timeoutSeconds);
            try (ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                String[] headers = new String[colCount];
                for (int i = 1; i <= colCount; i++) headers[i - 1] = meta.getColumnLabel(i);

                while (rs.next()) {
                    for (int i = 0; i < colCount; i++) {
                        plan.append(headers[i]).append(": ").append(rs.getString(i + 1)).append("  ");
                    }
                    plan.append("\n");

                    // EXPLAIN 输出中 key 列为实际使用的索引名，rows 列为预估扫描行数
                    try {
                        String key = rs.getString("key");
                        if (key != null && !key.isBlank()) indexesUsed.add(key);
                    } catch (SQLException ignored) {}
                    try {
                        String rowsVal = rs.getString("rows");
                        if (rowsVal != null) estimatedRows = Long.parseLong(rowsVal);
                    } catch (Exception ignored) {}
                }
            }
        } catch (SQLTimeoutException e) {
            throw new SQLException("EXPLAIN timed out after " + timeoutSeconds + "s. "
                    + "The statement has been cancelled.", "QUERY_TIMEOUT", e);
        } catch (SQLException e) {
            if (MySqlPermissionErrors.isPermissionDenied(e)) {
                throw new DatasourcePermissionException("", e.getMessage(), buildHint(conn, e));
            }
            throw e;
        }

        return ExplainResult.builder()
                .plan(plan.toString().trim())
                .indexesUsed(indexesUsed)
                .estimatedRows(estimatedRows)
                .dbCostMs(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * 从连接元数据和异常消息中提取用户名与数据库名，生成 GRANT 修复建议。
     * 优先从异常消息正则提取用户名（更可靠），回退到 JDBC DatabaseMetaData。
     */
    private String buildHint(Connection conn, SQLException e) {
        String username = MySqlPermissionErrors.extractUsername(e);
        String database = null;
        try {
            if (username == null) username = conn.getMetaData().getUserName();
            database = conn.getCatalog();
        } catch (SQLException ignored) {}
        return MySqlPermissionErrors.buildGrantHint("", username, database);
    }
}


