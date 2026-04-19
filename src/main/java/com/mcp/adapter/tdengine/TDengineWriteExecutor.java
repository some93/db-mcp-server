package com.mcp.adapter.tdengine;

import com.mcp.adapter.spi.WriteExecutor;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * TDengine 写入执行器。
 *
 * <p>TDengine 作为时序数据库采用追加写入模型，<b>不支持跨语句事务</b>。
 * 因此本实现逐条独立执行每条语句，失败后立即停止并将剩余语句标记为 SKIPPED。
 * 已成功执行的语句无法回滚，返回的 {@link WriteResult} 中 {@code rolledBack=false}
 * 明确告知调用方这一行为差异，避免上层误导 AI 客户端。
 *
 * <p>与 MySQL 实现的关键区别：
 * <ul>
 *   <li>不开启事务，无 rollback 操作。</li>
 *   <li>失败时返回 FAILED + 后续 SKIPPED，而非全部 ROLLBACK。</li>
 *   <li>{@code rolledBack} 字段固定为 {@code false}。</li>
 * </ul>
 * @author ouyanghang
 */
public class TDengineWriteExecutor implements WriteExecutor {

    @Override
    public WriteResult executeWrite(Connection conn, List<String> statements, int timeoutSeconds) throws Exception {
        long totalStart = System.currentTimeMillis();
        int totalAffected = 0;
        List<WriteResult.StatementResult> details = new ArrayList<>();
        int size = statements.size();

        for (int i = 0; i < size; i++) {
            String stmt = statements.get(i);
            long stmtStart = System.currentTimeMillis();
            String stmtType = detectType(stmt);

            try (Statement st = conn.createStatement()) {
                try { st.setQueryTimeout(timeoutSeconds); } catch (Exception ignored) {}
                int affected = st.executeUpdate(stmt);
                totalAffected += affected;
                details.add(WriteResult.StatementResult.builder()
                        .statementIndex(i)
                        .statementType(stmtType)
                        .affectedRows(affected)
                        .status("SUCCESS")
                        .dbCostMs(System.currentTimeMillis() - stmtStart)
                        .build());
            } catch (Exception e) {
                // TDengine 无事务，记录失败并将剩余语句标记为 SKIPPED 后终止
                details.add(WriteResult.StatementResult.builder()
                        .statementIndex(i)
                        .statementType(stmtType)
                        .affectedRows(0)
                        .status("FAILED")
                        .message(e.getMessage())
                        .dbCostMs(System.currentTimeMillis() - stmtStart)
                        .build());
                for (int j = i + 1; j < size; j++) {
                    details.add(WriteResult.StatementResult.builder()
                            .statementIndex(j)
                            .statementType(detectType(statements.get(j)))
                            .affectedRows(0)
                            .status("SKIPPED")
                            .message("Skipped due to previous failure.")
                            .dbCostMs(0)
                            .build());
                }
                break;
            }
        }

        return WriteResult.builder()
                .totalAffectedRows(totalAffected)
                .details(details)
                .rolledBack(false)  // TDengine 不支持事务，失败前已执行的语句不可撤销
                .dbCostMs(System.currentTimeMillis() - totalStart)
                .build();
    }

    private String detectType(String sql) {
        if (sql == null || sql.isBlank()) return "UNKNOWN";
        String upper = sql.stripLeading().substring(0, Math.min(6, sql.stripLeading().length())).toUpperCase();
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("CREATE")) return "CREATE";
        return "OTHER";
    }
}


