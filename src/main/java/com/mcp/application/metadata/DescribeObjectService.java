package com.mcp.application.metadata;

import com.mcp.adapter.AdapterRegistry;
import com.mcp.adapter.spi.DatasourceAdapter;
import com.mcp.adapter.spi.ObjectDetail;
import com.mcp.domain.audit.AuditLogger;
import com.mcp.domain.cache.DatasourceMetadata;
import com.mcp.domain.cache.MetadataCache;
import com.mcp.domain.health.HealthService;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import com.mcp.infrastructure.datasource.DatasourcePermissionException;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 工具 {@code describeObject} 的应用服务，返回指定表或视图的完整列结构。
 *
 * <p>当对象不存在时，从元数据缓存中搜索相似名称（前缀/包含匹配），
 * 在错误响应的 {@code data.similarObjects} 中返回推荐列表，帮助 AI 客户端纠正拼写错误。
 * @author ouyanghang
 */
@Service
public class DescribeObjectService {

    private final DataSourceManager dataSourceManager;
    private final AdapterRegistry adapterRegistry;
    private final HealthService healthService;
    private final MetadataCache metadataCache;

    public DescribeObjectService(DataSourceManager dataSourceManager,
                                 AdapterRegistry adapterRegistry,
                                 HealthService healthService,
                                 MetadataCache metadataCache) {
        this.dataSourceManager = dataSourceManager;
        this.adapterRegistry = adapterRegistry;
        this.healthService = healthService;
        this.metadataCache = metadataCache;
    }

    public McpResponse execute(String datasourceName, String objectName, String objectType) {
        long start = System.currentTimeMillis();
        healthService.quickCheck(datasourceName);

        DataSourceWrapper wrapper = dataSourceManager.getDataSource(datasourceName);
        DatasourceAdapter adapter = adapterRegistry.getAdapter(wrapper.getConfig().getType());

        try (Connection conn = wrapper.getHikariDataSource().getConnection()) {
            ObjectDetail detail = adapter.metadata().describeObject(conn, objectName, objectType);

            if (detail == null) {
                List<String> similar = findSimilarObjects(datasourceName, objectName);
                AuditLogger.log("describeObject", datasourceName, null, false,
                        System.currentTimeMillis() - start, "BUSINESS_OBJECT_NOT_FOUND",
                        "Object '" + objectName + "' not found.");
                Map<String, Object> errData = new LinkedHashMap<>();
                errData.put("objectName", objectName);
                errData.put("similarObjects", similar);
                McpResponse resp = McpResponse.error("BUSINESS_OBJECT_NOT_FOUND",
                        "Object '" + objectName + "' not found in datasource '" + datasourceName + "'.",
                        System.currentTimeMillis() - start);
                resp.setData(errData);
                return resp;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("objectName", detail.getObjectName());
            data.put("objectType", detail.getObjectType());
            data.put("tableType", detail.getTableType());
            data.put("columns", detail.getColumns().stream().map(col -> {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("name", col.getName());
                c.put("dataType", col.getDataType());
                c.put("length", col.getLength());
                c.put("precision", col.getPrecision());
                c.put("primaryKey", col.isPrimaryKey());
                c.put("nullable", col.isNullable());
                c.put("defaultValue", col.getDefaultValue());
                c.put("comment", col.getComment());
                return c;
            }).collect(Collectors.toList()));

            AuditLogger.log("describeObject", datasourceName, null, true, System.currentTimeMillis() - start, null, null);
            return McpResponse.ok(data, System.currentTimeMillis() - start);
        } catch (DatasourcePermissionException e) {
            String msg = e.getMessage() + " | Fix: " + e.getGrantHint();
            AuditLogger.log("describeObject", datasourceName, null, false,
                    System.currentTimeMillis() - start, e.getCode(), msg);
            return McpResponse.error(e.getCode(), msg, System.currentTimeMillis() - start);
        } catch (Exception e) {
            AuditLogger.log("describeObject", datasourceName, null, false,
                    System.currentTimeMillis() - start, "SQL_EXECUTE_ERROR", e.getMessage());
            return McpResponse.error("SQL_EXECUTE_ERROR", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /** 从元数据缓存中找与 objectName 相似的对象名（最多 5 条，按前缀/包含优先） */
    private List<String> findSimilarObjects(String datasourceName, String objectName) {
        DatasourceMetadata meta = metadataCache.get(datasourceName);
        if (meta == null || meta.getTables() == null) return Collections.emptyList();
        String needle = objectName.toLowerCase();
        List<String> result = new ArrayList<>();
        for (com.mcp.adapter.spi.TableInfo t : meta.getTables()) {
            String candidate = t.getName().toLowerCase();
            if (candidate.startsWith(needle) || needle.startsWith(candidate)
                    || candidate.contains(needle) || needle.contains(candidate)) {
                result.add(t.getName());
                if (result.size() >= 5) break;
            }
        }
        return result;
    }
}


