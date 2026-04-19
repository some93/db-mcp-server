package com.mcp.application.execution;

import com.mcp.adapter.AdapterRegistry;
import com.mcp.adapter.spi.DatasourceAdapter;
import com.mcp.adapter.spi.WriteExecutor;
import com.mcp.domain.audit.AuditLogger;
import com.mcp.domain.cache.MetadataCache;
import com.mcp.domain.health.HealthService;
import com.mcp.domain.security.SqlBlockedException;
import com.mcp.domain.security.SqlGuard;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import com.mcp.infrastructure.datasource.DatasourcePermissionException;
import com.mcp.infrastructure.support.McpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具 {@code createTable} 的应用服务，负责执行 CREATE TABLE DDL 语句。
 *
 * <p>创建成功后触发全量元数据刷新（对所有数据源），确保后续 listTables 和 describeObject
 * 能立即看到新建表，无需等待定时刷新周期。刷新失败时仅记录 WARN，不影响 CREATE 的成功响应。
 * @author ouyanghang
 */
@Service
public class CreateTableService {

    private static final Logger log = LoggerFactory.getLogger(CreateTableService.class);

    private final DataSourceManager dataSourceManager;
    private final AdapterRegistry adapterRegistry;
    private final HealthService healthService;
    private final MetadataCache metadataCache;
    private final AppConfig appConfig;

    public CreateTableService(DataSourceManager dataSourceManager, AdapterRegistry adapterRegistry,
                              HealthService healthService, MetadataCache metadataCache, AppConfig appConfig) {
        this.dataSourceManager = dataSourceManager;
        this.adapterRegistry = adapterRegistry;
        this.healthService = healthService;
        this.metadataCache = metadataCache;
        this.appConfig = appConfig;
    }

    public McpResponse execute(String datasourceName, String sql) {
        long start = System.currentTimeMillis();

        try {
            SqlGuard.checkCreateTable(sql, appConfig.getSql().getMaxLength());
        } catch (SqlBlockedException e) {
            return McpResponse.error(e.getCode(), e.getMessage(), System.currentTimeMillis() - start);
        }

        healthService.quickCheck(datasourceName);
        DataSourceWrapper wrapper = dataSourceManager.getDataSource(datasourceName);
        DatasourceAdapter adapter = adapterRegistry.getAdapter(wrapper.getConfig().getType());

        try (Connection conn = wrapper.getHikariDataSource().getConnection()) {
            String cleanSql = SqlGuard.stripComments(sql);
            WriteExecutor.WriteResult result = adapter.writeExecutor().executeWrite(
                    conn, Collections.singletonList(cleanSql), appConfig.getSql().getTimeoutSeconds());

            boolean success = result.getDetails().stream().allMatch(d -> "SUCCESS".equals(d.getStatus()));

            boolean refreshed = false;
            if (success) {
                // 触发全量元数据刷新
                for (var dsConfig : appConfig.getDatasources()) {
                    try {
                        metadataCache.refresh(dsConfig.getName());
                    } catch (Exception e) {
                        log.warn("Post-createTable metadata refresh failed for [{}]: {}", dsConfig.getName(), e.getMessage());
                    }
                }
                refreshed = true;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("summary", success ? "Table created successfully." : "CREATE TABLE failed.");
            data.put("generatedObjects", success ? extractTableName(sql) : Collections.emptyList());
            data.put("warnings", Collections.emptyList());
            data.put("metadataRefreshTriggered", refreshed);
            data.put("dbCostMs", result.getDbCostMs());

            String errorMsg = success ? null : result.getDetails().get(0).getMessage();
            AuditLogger.log("createTable", datasourceName, sql, success,
                    System.currentTimeMillis() - start, success ? null : "SQL_EXECUTE_ERROR", errorMsg);

            return success
                    ? McpResponse.ok("Table created successfully.", data, System.currentTimeMillis() - start)
                    : McpResponse.error("SQL_EXECUTE_ERROR", errorMsg, System.currentTimeMillis() - start);
        } catch (DatasourcePermissionException e) {
            String msg = e.getMessage() + " | Fix: " + e.getGrantHint();
            AuditLogger.log("createTable", datasourceName, sql, false,
                    System.currentTimeMillis() - start, e.getCode(), msg);
            return McpResponse.error(e.getCode(), msg, System.currentTimeMillis() - start);
        } catch (Exception e) {
            AuditLogger.log("createTable", datasourceName, sql, false,
                    System.currentTimeMillis() - start, "SQL_EXECUTE_ERROR", e.getMessage());
            return McpResponse.error("SQL_EXECUTE_ERROR", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /** 从 CREATE TABLE 语句中提取表名，用于 generatedObjects 字段 */
    private List<String> extractTableName(String sql) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"']?([\\w\\.]+)[`\"']?",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(sql);
            if (m.find()) return Collections.singletonList(m.group(1));
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }
}


