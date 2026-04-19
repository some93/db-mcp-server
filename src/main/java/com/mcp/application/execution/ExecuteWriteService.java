package com.mcp.application.execution;

import com.mcp.adapter.AdapterRegistry;
import com.mcp.adapter.spi.DatasourceAdapter;
import com.mcp.adapter.spi.WriteExecutor;
import com.mcp.domain.audit.AuditLogger;
import com.mcp.domain.health.HealthService;
import com.mcp.domain.security.SqlBlockedException;
import com.mcp.domain.security.SqlGuard;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import com.mcp.infrastructure.datasource.DatasourcePermissionException;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 工具 {@code executeWrite} 的应用服务，负责执行 INSERT / UPDATE / DELETE 语句。
 *
 * <p>执行链：分号拆分多语句 → 逐条安全检查 → 健康校验 → 获取连接 → 注释清洗 → 批量执行。
 *
 * <p>多语句处理设计：
 * <ul>
 *   <li>在应用层而非数据库层拆分语句（{@link #splitStatements}），逐条传入安全检查，
 *       避免 JSqlParser 多语句解析的兼容性问题。</li>
 *   <li>拆分逻辑感知单引号、双引号、反引号中的分号，防止字符串字面量中的分号被误拆。</li>
 *   <li>拆分后的语句列表传入 executor，MySQL executor 在单个事务内执行（全成功或全回滚），
 *       TDengine executor 逐条独立执行（失败后 SKIPPED）。</li>
 * </ul>
 * @author ouyanghang
 */
@Service
public class ExecuteWriteService {

    private final DataSourceManager dataSourceManager;
    private final AdapterRegistry adapterRegistry;
    private final HealthService healthService;
    private final AppConfig appConfig;

    public ExecuteWriteService(DataSourceManager dataSourceManager, AdapterRegistry adapterRegistry,
                               HealthService healthService, AppConfig appConfig) {
        this.dataSourceManager = dataSourceManager;
        this.adapterRegistry = adapterRegistry;
        this.healthService = healthService;
        this.appConfig = appConfig;
    }

    public McpResponse execute(String datasourceName, String sql) {
        long start = System.currentTimeMillis();

        // 拆分多语句（分号分隔），逐条安全校验
        List<String> statements = splitStatements(sql);
        if (statements.isEmpty()) {
            AuditLogger.log("executeWrite", datasourceName, sql, false, 0, "PARAM_SQL_EMPTY", "SQL must not be empty.");
            return McpResponse.error("PARAM_SQL_EMPTY", "SQL must not be empty.", 0);
        }

        for (String stmt : statements) {
            try {
                SqlGuard.checkWrite(stmt, appConfig.getSql().getMaxLength(), appConfig.getSql().getBatchInsertMaxRows());
            } catch (SqlBlockedException e) {
                AuditLogger.log("executeWrite", datasourceName, sql, false,
                        System.currentTimeMillis() - start, e.getCode(), e.getMessage());
                return McpResponse.error(e.getCode(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
        healthService.quickCheck(datasourceName);
        DataSourceWrapper wrapper = dataSourceManager.getDataSource(datasourceName);
        DatasourceAdapter adapter = adapterRegistry.getAdapter(wrapper.getConfig().getType());

        try (Connection conn = wrapper.getHikariDataSource().getConnection()) {
            List<String> cleanStatements = statements.stream()
                    .map(SqlGuard::stripComments)
                    .collect(Collectors.toList());
            WriteExecutor.WriteResult result = adapter.writeExecutor().executeWrite(
                    conn, cleanStatements, appConfig.getSql().getTimeoutSeconds());

            boolean allSuccess = result.getDetails().stream()
                    .allMatch(d -> "SUCCESS".equals(d.getStatus()));

            List<Map<String, Object>> details = result.getDetails().stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("statementIndex", d.getStatementIndex());
                m.put("statementType", d.getStatementType());
                m.put("affectedRows", d.getAffectedRows());
                m.put("status", d.getStatus());
                m.put("message", d.getMessage());
                m.put("dbCostMs", d.getDbCostMs());
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("affectedRows", result.getTotalAffectedRows());
            data.put("warnings", Collections.emptyList());

            String summary;
            String code;
            String msg;
            if (allSuccess) {
                summary = "All " + statements.size() + " statement(s) executed successfully, "
                        + result.getTotalAffectedRows() + " row(s) affected.";
                code = "SUCCESS";
                msg = "Write executed successfully.";
            } else if (result.isRolledBack()) {
                summary = "Execution failed and rolled back.";
                code = "SQL_EXECUTE_ROLLBACK";
                msg = "Write failed and rolled back.";
            } else {
                summary = "Execution failed. No transaction support — already-executed statements were NOT rolled back.";
                code = "SQL_EXECUTE_PARTIAL";
                msg = "Write failed (no transaction rollback). Some statements may have been committed.";
            }

            data.put("summary", summary);
            data.put("details", details);
            data.put("dbCostMs", result.getDbCostMs());

            McpResponse resp = allSuccess
                    ? McpResponse.ok(msg, data, System.currentTimeMillis() - start)
                    : McpResponse.error(code, msg, System.currentTimeMillis() - start);
            resp.setData(data);
            AuditLogger.log("executeWrite", datasourceName, sql, allSuccess,
                    System.currentTimeMillis() - start, allSuccess ? null : code, allSuccess ? null : msg);
            return resp;
        } catch (DatasourcePermissionException e) {
            String msg = e.getMessage() + " | Fix: " + e.getGrantHint();
            AuditLogger.log("executeWrite", datasourceName, sql, false,
                    System.currentTimeMillis() - start, e.getCode(), msg);
            return McpResponse.error(e.getCode(), msg, System.currentTimeMillis() - start);
        } catch (Exception e) {
            AuditLogger.log("executeWrite", datasourceName, sql, false,
                    System.currentTimeMillis() - start, "SQL_EXECUTE_ERROR", e.getMessage());
            return McpResponse.error("SQL_EXECUTE_ERROR", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private List<String> splitStatements(String sql) {
        if (sql == null || sql.isBlank()) {
            return Collections.emptyList();
        }

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (ch == '\\' && (inSingleQuote || inDoubleQuote)) {
                current.append(ch);
                if (next != '\0') {
                    current.append(next);
                    i++;
                }
                continue;
            }
            if (ch == '\'' && !inDoubleQuote && !inBacktick) {
                current.append(ch);
                if (inSingleQuote && next == '\'') {
                    current.append(next);
                    i++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (ch == '"' && !inSingleQuote && !inBacktick) {
                current.append(ch);
                if (inDoubleQuote && next == '"') {
                    current.append(next);
                    i++;
                } else {
                    inDoubleQuote = !inDoubleQuote;
                }
                continue;
            }
            if (ch == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
                current.append(ch);
                continue;
            }
            if (ch == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }
}


