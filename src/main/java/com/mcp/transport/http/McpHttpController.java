package com.mcp.transport.http;

import com.mcp.application.metadata.*;
import com.mcp.application.execution.*;
import com.mcp.application.cache.RefreshMetadataCacheService;
import com.mcp.application.status.GetServiceStatusService;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP HTTP 控制器，本机回环，无鉴权，与 MCP 工具语义完全一致。
 * @author ouyanghang
 */
@RestController
@RequestMapping("/api")
public class McpHttpController {

    private final ListDatasourcesService listDatasourcesService;
    private final ListTablesService listTablesService;
    private final ListIndexesAndViewsService listIndexesAndViewsService;
    private final LocateTableService locateTableService;
    private final DescribeObjectService describeObjectService;
    private final ExecuteQueryService executeQueryService;
    private final ExecuteWriteService executeWriteService;
    private final CreateTableService createTableService;
    private final ExplainQueryService explainQueryService;
    private final RefreshMetadataCacheService refreshMetadataCacheService;
    private final GetServiceStatusService getServiceStatusService;

    public McpHttpController(ListDatasourcesService listDatasourcesService,
                             ListTablesService listTablesService,
                             ListIndexesAndViewsService listIndexesAndViewsService,
                             LocateTableService locateTableService,
                             DescribeObjectService describeObjectService,
                             ExecuteQueryService executeQueryService,
                             ExecuteWriteService executeWriteService,
                             CreateTableService createTableService,
                             ExplainQueryService explainQueryService,
                             RefreshMetadataCacheService refreshMetadataCacheService,
                             GetServiceStatusService getServiceStatusService) {
        this.listDatasourcesService = listDatasourcesService;
        this.listTablesService = listTablesService;
        this.listIndexesAndViewsService = listIndexesAndViewsService;
        this.locateTableService = locateTableService;
        this.describeObjectService = describeObjectService;
        this.executeQueryService = executeQueryService;
        this.executeWriteService = executeWriteService;
        this.createTableService = createTableService;
        this.explainQueryService = explainQueryService;
        this.refreshMetadataCacheService = refreshMetadataCacheService;
        this.getServiceStatusService = getServiceStatusService;
    }

    @GetMapping("/status")
    public McpResponse getServiceStatus() {
        return getServiceStatusService.execute();
    }

    @GetMapping("/datasources")
    public McpResponse listDatasources() {
        return listDatasourcesService.execute();
    }

    @GetMapping("/locate-table")
    public McpResponse locateTable(@RequestParam String tableName) {
        return locateTableService.execute(tableName);
    }

    @GetMapping("/tables")
    public McpResponse listTables(@RequestParam String datasourceName) {
        return listTablesService.execute(datasourceName);
    }

    @GetMapping("/indexes-and-views")
    public McpResponse listIndexesAndViews(@RequestParam String datasourceName) {
        return listIndexesAndViewsService.execute(datasourceName);
    }

    @GetMapping("/describe")
    public McpResponse describeObject(@RequestParam String datasourceName,
                                      @RequestParam String objectName,
                                      @RequestParam(required = false) String objectType) {
        return describeObjectService.execute(datasourceName, objectName, objectType);
    }

    @PostMapping("/query")
    public McpResponse executeQuery(@RequestBody Map<String, String> body) {
        McpResponse error = validateSqlBody(body);
        if (error != null) return error;
        return executeQueryService.execute(body.get("datasourceName"), body.get("sql"));
    }

    @PostMapping("/write")
    public McpResponse executeWrite(@RequestBody Map<String, String> body) {
        McpResponse error = validateSqlBody(body);
        if (error != null) return error;
        return executeWriteService.execute(body.get("datasourceName"), body.get("sql"));
    }

    @PostMapping("/create-table")
    public McpResponse createTable(@RequestBody Map<String, String> body) {
        McpResponse error = validateSqlBody(body);
        if (error != null) return error;
        return createTableService.execute(body.get("datasourceName"), body.get("sql"));
    }

    @PostMapping("/explain")
    public McpResponse explainQuery(@RequestBody Map<String, String> body) {
        McpResponse error = validateSqlBody(body);
        if (error != null) return error;
        return explainQueryService.execute(body.get("datasourceName"), body.get("sql"));
    }

    @PostMapping("/refresh-metadata")
    public McpResponse refreshMetadata(@RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> names = body != null ? (List<String>) body.get("datasourceNames") : null;
        String mode = body != null ? (String) body.getOrDefault("mode", "SYNC") : "SYNC";
        return refreshMetadataCacheService.execute(names, mode);
    }

    private McpResponse validateSqlBody(Map<String, String> body) {
        if (body == null) {
            return McpResponse.error("PARAM_MISSING", "Request body is required.", 0);
        }
        if (body.get("datasourceName") == null || body.get("datasourceName").isBlank()) {
            return McpResponse.error("PARAM_MISSING", "datasourceName is required.", 0);
        }
        if (body.get("sql") == null || body.get("sql").isBlank()) {
            return McpResponse.error("PARAM_MISSING", "sql is required.", 0);
        }
        return null;
    }
}


