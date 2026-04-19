package com.mcp.application.execution;

import com.mcp.adapter.AdapterRegistry;
import com.mcp.adapter.spi.DatasourceAdapter;
import com.mcp.adapter.spi.QueryExecutor;
import com.mcp.domain.audit.AuditLogger;
import com.mcp.domain.health.HealthService;
import com.mcp.domain.security.SqlBlockedException;
import com.mcp.domain.security.SqlGuard;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import com.mcp.infrastructure.datasource.DatasourcePermissionException;
import com.mcp.infrastructure.support.MarkdownTable;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具 {@code executeQuery} 的应用服务，负责执行只读 SELECT 查询并返回结构化结果。
 *
 * <p>执行链：安全检查 → 方言检查 → 健康校验 → 获取连接 → 注释清洗 → 执行查询 → 渲染 Markdown 表格。
 *
 * <p>权限错误（{@link DatasourcePermissionException}）优先于通用异常捕获，
 * 以便在响应中附带 GRANT 修复建议，帮助 AI 客户端引导用户自助解决权限问题。
 * @author ouyanghang
 */
@Service
public class ExecuteQueryService {

    private final DataSourceManager dataSourceManager;
    private final AdapterRegistry adapterRegistry;
    private final HealthService healthService;
    private final AppConfig appConfig;

    public ExecuteQueryService(DataSourceManager dataSourceManager, AdapterRegistry adapterRegistry,
                               HealthService healthService, AppConfig appConfig) {
        this.dataSourceManager = dataSourceManager;
        this.adapterRegistry = adapterRegistry;
        this.healthService = healthService;
        this.appConfig = appConfig;
    }

    public McpResponse execute(String datasourceName, String sql) {
        long start = System.currentTimeMillis();
        McpResponse resp;

        try {
            SqlGuard.checkQuery(sql, appConfig.getSql().getMaxLength());
        } catch (SqlBlockedException e) {
            resp = McpResponse.error(e.getCode(), e.getMessage(), System.currentTimeMillis() - start);
            AuditLogger.log("executeQuery", datasourceName, sql, false,
                    System.currentTimeMillis() - start, e.getCode(), e.getMessage());
            return resp;
        }

        healthService.quickCheck(datasourceName);
        DataSourceWrapper wrapper = dataSourceManager.getDataSource(datasourceName);
        DatasourceAdapter adapter = adapterRegistry.getAdapter(wrapper.getConfig().getType());

        // 方言级检查
        String dialectBlock = adapter.sqlGuard().checkDialectRules(sql);
        if (dialectBlock != null) {
            resp = McpResponse.error("SQL_BLOCK_DIALECT", dialectBlock, System.currentTimeMillis() - start);
            AuditLogger.log("executeQuery", datasourceName, sql, false,
                    System.currentTimeMillis() - start, "SQL_BLOCK_DIALECT", dialectBlock);
            return resp;
        }

        try (Connection conn = wrapper.getHikariDataSource().getConnection()) {
            String cleanSql = SqlGuard.stripComments(sql);
            QueryExecutor.QueryResult result = adapter.queryExecutor().executeQuery(
                    conn, cleanSql, appConfig.getSql().getMaxRows(), appConfig.getSql().getTimeoutSeconds());

            String markdownTable = MarkdownTable.render(result.getRows());
            String summary = result.isTruncated()
                    ? "Returned " + result.getReturnedRows() + " rows (truncated, more rows exist)."
                    : "Returned " + result.getReturnedRows() + " row(s).";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", result.getRows());
            data.put("returnedRows", result.getReturnedRows());
            data.put("totalRows", result.getTotalRows());
            data.put("truncated", result.isTruncated());
            data.put("markdownTable", markdownTable);
            data.put("summary", summary);
            data.put("dbCostMs", result.getDbCostMs());

            resp = McpResponse.ok(summary, data, System.currentTimeMillis() - start);
            AuditLogger.log("executeQuery", datasourceName, sql, true,
                    System.currentTimeMillis() - start, null, null);
            return resp;
        } catch (DatasourcePermissionException e) {
            String msg = e.getMessage() + " | Fix: " + e.getGrantHint();
            AuditLogger.log("executeQuery", datasourceName, sql, false,
                    System.currentTimeMillis() - start, e.getCode(), msg);
            return McpResponse.error(e.getCode(), msg, System.currentTimeMillis() - start);
        } catch (Exception e) {
            resp = McpResponse.error("SQL_EXECUTE_ERROR", e.getMessage(), System.currentTimeMillis() - start);
            AuditLogger.log("executeQuery", datasourceName, sql, false,
                    System.currentTimeMillis() - start, "SQL_EXECUTE_ERROR", e.getMessage());
            return resp;
        }
    }
}


