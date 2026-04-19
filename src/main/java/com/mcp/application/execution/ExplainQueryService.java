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
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具 {@code explainQuery} 的应用服务，返回 SELECT 语句的执行计划。
 *
 * <p>解析 EXPLAIN 输出，提取实际使用的索引和预估扫描行数，
 * 生成易于 AI 客户端理解的 {@code summary} 字段（如 "No indexes used" 或 "Uses index(es): PRIMARY"）。
 * @author ouyanghang
 */
@Service
public class ExplainQueryService {

    private final DataSourceManager dataSourceManager;
    private final AdapterRegistry adapterRegistry;
    private final HealthService healthService;
    private final AppConfig appConfig;

    public ExplainQueryService(DataSourceManager dataSourceManager, AdapterRegistry adapterRegistry,
                               HealthService healthService, AppConfig appConfig) {
        this.dataSourceManager = dataSourceManager;
        this.adapterRegistry = adapterRegistry;
        this.healthService = healthService;
        this.appConfig = appConfig;
    }

    public McpResponse execute(String datasourceName, String sql) {
        long start = System.currentTimeMillis();

        try {
            SqlGuard.checkExplain(sql, appConfig.getSql().getMaxLength());
        } catch (SqlBlockedException e) {
            return McpResponse.error(e.getCode(), e.getMessage(), System.currentTimeMillis() - start);
        }

        healthService.quickCheck(datasourceName);
        DataSourceWrapper wrapper = dataSourceManager.getDataSource(datasourceName);
        DatasourceAdapter adapter = adapterRegistry.getAdapter(wrapper.getConfig().getType());

        try (Connection conn = wrapper.getHikariDataSource().getConnection()) {
            String cleanSql = SqlGuard.stripComments(sql);
            QueryExecutor.ExplainResult result = adapter.queryExecutor().explainQuery(
                    conn, cleanSql, appConfig.getSql().getTimeoutSeconds());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("plan", result.getPlan());
            data.put("indexesUsed", result.getIndexesUsed());
            data.put("estimatedRows", result.getEstimatedRows());
            data.put("summary", result.getIndexesUsed() == null || result.getIndexesUsed().isEmpty()
                    ? "No indexes used. Consider adding an index."
                    : "Uses index(es): " + String.join(", ", result.getIndexesUsed()));
            data.put("dbCostMs", result.getDbCostMs());

            AuditLogger.log("explainQuery", datasourceName, sql, true,
                    System.currentTimeMillis() - start, null, null);
            return McpResponse.ok(data, System.currentTimeMillis() - start);
        } catch (DatasourcePermissionException e) {
            String msg = e.getMessage() + " | Fix: " + e.getGrantHint();
            AuditLogger.log("explainQuery", datasourceName, sql, false,
                    System.currentTimeMillis() - start, e.getCode(), msg);
            return McpResponse.error(e.getCode(), msg, System.currentTimeMillis() - start);
        } catch (Exception e) {
            AuditLogger.log("explainQuery", datasourceName, sql, false,
                    System.currentTimeMillis() - start, "SQL_EXECUTE_ERROR", e.getMessage());
            return McpResponse.error("SQL_EXECUTE_ERROR", e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}


