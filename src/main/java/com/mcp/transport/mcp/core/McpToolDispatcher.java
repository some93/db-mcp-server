package com.mcp.transport.mcp.core;

import com.mcp.application.cache.RefreshMetadataCacheService;
import com.mcp.application.execution.CreateTableService;
import com.mcp.application.execution.ExecuteQueryService;
import com.mcp.application.execution.ExecuteWriteService;
import com.mcp.application.execution.ExplainQueryService;
import com.mcp.application.metadata.DescribeObjectService;
import com.mcp.application.metadata.ListDatasourcesService;
import com.mcp.application.metadata.ListIndexesAndViewsService;
import com.mcp.application.metadata.ListTablesService;
import com.mcp.application.metadata.LocateTableService;
import com.mcp.application.status.GetServiceStatusService;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具分发器，将 toolName 映射到现有 application service。
 * @author ouyanghang
 */
@Component
public class McpToolDispatcher {

    private final ListDatasourcesService listDatasourcesService;
    private final ListTablesService listTablesService;
    private final ListIndexesAndViewsService listIndexesAndViewsService;
    private final DescribeObjectService describeObjectService;
    private final ExecuteQueryService executeQueryService;
    private final ExecuteWriteService executeWriteService;
    private final CreateTableService createTableService;
    private final ExplainQueryService explainQueryService;
    private final RefreshMetadataCacheService refreshMetadataCacheService;
    private final LocateTableService locateTableService;
    private final GetServiceStatusService getServiceStatusService;

    public McpToolDispatcher(ListDatasourcesService listDatasourcesService,
                             ListTablesService listTablesService,
                             ListIndexesAndViewsService listIndexesAndViewsService,
                             DescribeObjectService describeObjectService,
                             ExecuteQueryService executeQueryService,
                             ExecuteWriteService executeWriteService,
                             CreateTableService createTableService,
                             ExplainQueryService explainQueryService,
                             RefreshMetadataCacheService refreshMetadataCacheService,
                             LocateTableService locateTableService,
                             GetServiceStatusService getServiceStatusService) {
        this.listDatasourcesService = listDatasourcesService;
        this.listTablesService = listTablesService;
        this.listIndexesAndViewsService = listIndexesAndViewsService;
        this.describeObjectService = describeObjectService;
        this.executeQueryService = executeQueryService;
        this.executeWriteService = executeWriteService;
        this.createTableService = createTableService;
        this.explainQueryService = explainQueryService;
        this.refreshMetadataCacheService = refreshMetadataCacheService;
        this.locateTableService = locateTableService;
        this.getServiceStatusService = getServiceStatusService;
    }

    @SuppressWarnings("unchecked")
    public McpResponse dispatchTool(String toolName, Map<String, Object> args) {
        Map<String, Object> safeArgs = args != null ? args : Collections.emptyMap();
        String ds = (String) safeArgs.get("datasourceName");
        String sql = (String) safeArgs.get("sql");

        switch (toolName) {
            case "listDatasources":
                return listDatasourcesService.execute();
            case "locateTable":
                return locateTableService.execute((String) safeArgs.get("tableName"));
            case "getServiceStatus":
                return getServiceStatusService.execute();
            case "listTables":
                return listTablesService.execute(ds);
            case "listIndexesAndViews":
                return listIndexesAndViewsService.execute(ds);
            case "describeObject":
                return describeObjectService.execute(ds, (String) safeArgs.get("objectName"),
                        (String) safeArgs.get("objectType"));
            case "executeQuery":
                return executeQueryService.execute(ds, sql);
            case "executeWrite":
                return executeWriteService.execute(ds, sql);
            case "createTable":
                return createTableService.execute(ds, sql);
            case "explainQuery":
                return explainQueryService.execute(ds, sql);
            case "refreshMetadataCache":
                List<String> names = (List<String>) safeArgs.get("datasourceNames");
                String mode = (String) safeArgs.getOrDefault("mode", "SYNC");
                return refreshMetadataCacheService.execute(names, mode);
            default:
                return McpResponse.error("PROTOCOL_UNKNOWN_TOOL", "Unknown tool: " + toolName, 0);
        }
    }
}
