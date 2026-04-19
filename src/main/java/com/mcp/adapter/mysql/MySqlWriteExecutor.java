package com.mcp.adapter.mysql;

import com.mcp.adapter.spi.WriteExecutor;
import com.mcp.infrastructure.datasource.DatasourcePermissionException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 写入执行器，支持在单个事务内批量执行 INSERT / UPDATE / DELETE / DDL 语句。
 *
 * <p>事务处理策略：
 * <ul>
 *   <li>开启事务前保存原始 {@code autoCommit} 状态，{@code finally} 中严格恢复，
 *       防止连接归还连接池时状态污染后续请求。</li>
 *   <li>任意语句失败时立即回滚整个事务，保证请求级原子性（全成功或全回滚）。</li>
 *   <li>权限不足时先回滚事务再抛出 {@link DatasourcePermissionException}，
 *       确保失败路径也能清理事务状态。</li>
 * </ul>
 *
 * <p>返回的 {@link WriteResult} 中 {@code rolledBack=true}（默认值），
 * 表示失败时数据库状态已完整回滚，无残留数据。
 * @author ouyanghang
 */
public class MySqlWriteExecutor implements WriteExecutor {

    @Override
    public WriteResult executeWrite(Connection conn, List<String> statements, int timeoutSeconds) throws Exception {
        long totalStart = System.currentTimeMillis();
        List<WriteResult.StatementResult> details = new ArrayList<>();
        int totalAffected = 0;

        // 手动管理事务：实现请求级原子性（全成功或全回滚）。
        // 保存原 autoCommit 状态，finally 中严格恢复，避免连接池复用时状态污染。
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (int i = 0; i < statements.size(); i++) {
                String sql = statements.get(i).trim();
                long stmtStart = System.currentTimeMillis();
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(timeoutSeconds);
                    int affected;
                    try {
                        affected = stmt.executeUpdate(sql);
                    } catch (SQLTimeoutException te) {
                        throw new SQLException("Write statement timed out after " + timeoutSeconds + "s. "
                                + "The statement has been cancelled.", "QUERY_TIMEOUT", te);
                    }
                    totalAffected += affected;
                    details.add(WriteResult.StatementResult.builder()
                            .statementIndex(i)
                            .statementType(detectType(sql))
                            .affectedRows(affected)
                            .status("SUCCESS")
                            .message(null)
                            .dbCostMs(System.currentTimeMillis() - stmtStart)
                            .build());
                } catch (SQLException e) {
                    if (MySqlPermissionErrors.isPermissionDenied(e)) {
                        // 权限错误：先回滚再抛出，确保事务清理
                        conn.rollback();
                        String username = MySqlPermissionErrors.extractUsername(e);
                        String database = null;
                        try {
                            if (username == null) username = conn.getMetaData().getUserName();
                            database = conn.getCatalog();
                        } catch (SQLException ignored) {}
                        throw new DatasourcePermissionException("", e.getMessage(),
                                MySqlPermissionErrors.buildGrantHint("", username, database));
                    }
                    // 单条失败：记录明细，回滚整个事务，保留所有已执行语句的状态供调用方诊断
                    details.add(WriteResult.StatementResult.builder()
                            .statementIndex(i)
                            .statementType(detectType(sql))
                            .affectedRows(0)
                            .status("FAILED")
                            .message(e.getMessage())
                            .dbCostMs(System.currentTimeMillis() - stmtStart)
                            .build());
                    conn.rollback();
                    return WriteResult.builder()
                            .totalAffectedRows(0)
                            .details(details)
                            .dbCostMs(System.currentTimeMillis() - totalStart)
                            .build();
                }
            }
            conn.commit();
        } finally {
            conn.setAutoCommit(autoCommit);
        }

        return WriteResult.builder()
                .totalAffectedRows(totalAffected)
                .details(details)
                .dbCostMs(System.currentTimeMillis() - totalStart)
                .build();
    }

    private String detectType(String sql) {
        String upper = sql.stripLeading().toUpperCase();
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("CREATE")) return "CREATE";
        return "UNKNOWN";
    }
}


