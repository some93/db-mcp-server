package com.mcp.transport.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.application.cache.RefreshMetadataCacheService;
import com.mcp.application.execution.*;
import com.mcp.application.metadata.*;
import com.mcp.application.status.GetServiceStatusService;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.support.McpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 手写 MCP stdio JSON-RPC 2.0 协议层。
 * stdout 只输出 MCP 协议消息，stderr 输出所有日志，严格隔离。
 * @author ouyanghang
 */
@Component
public class McpStdioServer {

    private static final Logger log = LoggerFactory.getLogger(McpStdioServer.class);

    private final ObjectMapper mapper;
    private final AppConfig appConfig;
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

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;
    private PrintStream mcpOut;

    public McpStdioServer(AppConfig appConfig,
                          ListDatasourcesService listDatasourcesService,
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
        this.appConfig = appConfig;
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
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @PostConstruct
    public void start() {
        // 独占 stdout，所有日志必须走 stderr（System.err），绝不能混入 stdout。
        // BufferedOutputStream 提升吞吐，false 禁用 autoFlush（手动 flush 保证消息完整性）。
        mcpOut = new PrintStream(new BufferedOutputStream(System.out), false, StandardCharsets.UTF_8);
        running.set(true);
        readerThread = new Thread(this::readLoop, "mcp-stdio-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        log.info("MCP stdio server started.");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (readerThread != null) readerThread.interrupt();
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    handleMessage(line);
                } catch (Exception e) {
                    log.error("Error handling MCP message: {}", e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            if (running.get()) log.warn("MCP stdin closed: {}", e.getMessage());
        }

        // stdin EOF = Claude Desktop 已关闭连接。按设计退出进程，避免孤儿 JVM 常驻。
        if (appConfig.getMcp().isExitOnDisconnect()) {
            log.info("MCP client disconnected, exiting as configured.");
            System.exit(0);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String json) throws Exception {
        Map<String, Object> req = mapper.readValue(json, Map.class);
        Object id = req.get("id");
        String method = (String) req.get("method");
        Map<String, Object> params = req.containsKey("params")
                ? (Map<String, Object>) req.get("params") : Collections.emptyMap();

        log.debug("MCP request: method={} id={}", method, id);

        switch (method) {
            case "initialize":
                sendResult(id, buildInitializeResult());
                break;
            case "tools/list":
                sendResult(id, buildToolsList());
                break;
            case "tools/call":
                handleToolCall(id, params);
                break;
            default:
                sendError(id, -32601, "Method not found: " + method);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleToolCall(Object id, Map<String, Object> params) throws Exception {
        String toolName = (String) params.get("name");
        Map<String, Object> args = params.containsKey("arguments")
                ? (Map<String, Object>) params.get("arguments") : Collections.emptyMap();

        McpResponse result;
        try {
            result = dispatchTool(toolName, args);
        } catch (Exception e) {
            log.error("Tool call error [{}]: {}", toolName, e.getMessage(), e);
            result = McpResponse.error("SYSTEM_ERROR", e.getMessage(), 0);
        }

        // MCP tools/call 响应格式：结果放在 content[].text 中，isError 标记工具是否出错。
        // 即使业务失败（success=false），JSON-RPC 层面也返回 result 而非 error，
        // 这样 Claude 可以读取错误详情并给出有意义的回复，而不是收到协议级错误。
        String text = mapper.writeValueAsString(result);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", text);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", Collections.singletonList(content));
        response.put("isError", !result.isSuccess());
        sendResult(id, response);
    }

    @SuppressWarnings("unchecked")
    private McpResponse dispatchTool(String toolName, Map<String, Object> args) {
        String ds = (String) args.get("datasourceName");
        String sql = (String) args.get("sql");

        switch (toolName) {
            case "listDatasources":
                return listDatasourcesService.execute();
            case "locateTable":
                return locateTableService.execute((String) args.get("tableName"));
            case "getServiceStatus":
                return getServiceStatusService.execute();
            case "listTables":
                return listTablesService.execute(ds);
            case "listIndexesAndViews":
                return listIndexesAndViewsService.execute(ds);
            case "describeObject":
                return describeObjectService.execute(ds, (String) args.get("objectName"),
                        (String) args.get("objectType"));
            case "executeQuery":
                return executeQueryService.execute(ds, sql);
            case "executeWrite":
                return executeWriteService.execute(ds, sql);
            case "createTable":
                return createTableService.execute(ds, sql);
            case "explainQuery":
                return explainQueryService.execute(ds, sql);
            case "refreshMetadataCache":
                List<String> names = (List<String>) args.get("datasourceNames");
                String mode = (String) args.getOrDefault("mode", "SYNC");
                return refreshMetadataCacheService.execute(names, mode);
            default:
                return McpResponse.error("PROTOCOL_UNKNOWN_TOOL", "Unknown tool: " + toolName, 0);
        }
    }

    private void sendResult(Object id, Object result) throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        writeLine(mapper.writeValueAsString(response));
    }

    private void sendError(Object id, int code, String message) throws Exception {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        writeLine(mapper.writeValueAsString(response));
    }

    private synchronized void writeLine(String line) {
        mcpOut.println(line);
        mcpOut.flush();
    }

    private Map<String, Object> buildInitializeResult() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Collections.singletonMap("listChanged", false));
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "db-mcp-server");
        serverInfo.put("version", "1.0.0");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return result;
    }

    private Map<String, Object> buildToolsList() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(tool("listDatasources",
                "List all configured datasources and their health status (UP/DOWN). " +
                "Call this first if you don't know which datasources are available.",
                noParams()));

        tools.add(tool("locateTable",
                "Search for a table by name across all datasources. " +
                "Use this when you don't know which datasource contains the target table. " +
                "Returns matchStatus: EXACT_ONE (use directly), EXACT_MULTI (ask user to choose), " +
                "SIMILAR_ONLY (no exact match — pick the best candidate from similarTables and confirm with user), " +
                "or NOT_FOUND. Do NOT use this if you already know the datasourceName.",
                params("tableName", "string", "Table name to search for (case-insensitive)", true)));

        tools.add(tool("getServiceStatus",
                "Get server version, uptime, datasource health, cached table counts and config summary. " +
                "Useful for diagnosing connectivity or cache issues.",
                noParams()));

        tools.add(tool("listTables",
                "List all tables and views in the specified datasource. " +
                "Use this to explore available tables when you know the datasource. " +
                "If you don't know the datasource, use locateTable instead.",
                params("datasourceName", "string", "Datasource name (from listDatasources)", true)));

        tools.add(tool("listIndexesAndViews",
                "List all indexes and views in the specified datasource. " +
                "Use this to understand query optimization opportunities or available views.",
                params("datasourceName", "string", "Datasource name (from listDatasources)", true)));

        tools.add(tool("describeObject",
                "Describe the column structure of a table or view: name, type, length, nullable, primary key, default, comment. " +
                "Always call this before writing SQL to understand the schema. " +
                "If the object is not found, the response includes similarObjects — retry with one of those names. " +
                "Prerequisite: confirm the table exists via locateTable or listTables first.",
                params("datasourceName", "string", "Datasource name (from listDatasources)", true,
                       "objectName", "string", "Table or view name to describe", true,
                       "objectType", "string", "Optional hint: TABLE or VIEW", false)));

        tools.add(tool("executeQuery",
                "Execute a single read-only SELECT statement. Returns up to 500 rows; " +
                "if truncated=true there are more rows — add LIMIT/WHERE to narrow results. " +
                "SQL comments (-- and /* */) are stripped before execution. " +
                "Forbidden: DROP, TRUNCATE, ALTER, INSERT, UPDATE, DELETE, WHERE 1=1.",
                params("datasourceName", "string", "Datasource name", true,
                       "sql", "string", "A single SELECT statement", true)));

        tools.add(tool("executeWrite",
                "Execute one or more INSERT/UPDATE/DELETE statements in a single transaction — " +
                "all succeed or all roll back. Separate multiple statements with semicolons (;). " +
                "Rules: UPDATE and DELETE must have a WHERE clause; WHERE 1=1 / WHERE TRUE is rejected; " +
                "batch INSERT is limited to 1000 rows per call (split into multiple calls if needed). " +
                "On partial failure the response details which statement failed and the transaction is rolled back.",
                params("datasourceName", "string", "Datasource name", true,
                       "sql", "string", "One or more write statements separated by semicolons", true)));

        tools.add(tool("createTable",
                "Execute a CREATE TABLE statement. On success, metadata cache is automatically refreshed " +
                "so the new table is immediately visible in subsequent listTables/describeObject calls. " +
                "CREATE TABLE ... AS SELECT is not allowed.",
                params("datasourceName", "string", "Datasource name", true,
                       "sql", "string", "CREATE TABLE statement", true)));

        tools.add(tool("explainQuery",
                "Analyze the execution plan of a SELECT statement. Returns index usage, estimated rows and full plan. " +
                "Use this to verify a query will use the expected index before running it on large tables. " +
                "If indexesUsed is empty, consider adding an index or rewriting the query.",
                params("datasourceName", "string", "Datasource name", true,
                       "sql", "string", "SELECT statement to analyze", true)));

        tools.add(tool("refreshMetadataCache",
                "Manually refresh the metadata cache (table/column/index lists). " +
                "Use SYNC mode (default) when you need the refreshed data immediately in the next call. " +
                "Use ASYNC mode when you want to trigger a background refresh without waiting — " +
                "the tool returns immediately with triggered=true and cache update happens in the background. " +
                "Leave datasourceNames empty to refresh all datasources.",
                params("datasourceNames", "array", "Datasource names to refresh; empty or omitted = refresh all", false,
                       "mode", "string", "SYNC (wait for completion, default) or ASYNC (fire-and-forget)", false)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);
        return result;
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", description);
        t.put("inputSchema", inputSchema);
        return t;
    }

    private Map<String, Object> noParams() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());
        return schema;
    }

    /** Varargs: name, type, description, required, name, type, description, required, ... */
    private Map<String, Object> params(Object... defs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (int i = 0; i < defs.length; i += 4) {
            String pName = (String) defs[i];
            String pType = (String) defs[i + 1];
            String pDesc = (String) defs[i + 2];
            boolean pRequired = (boolean) defs[i + 3];
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", pType);
            prop.put("description", pDesc);
            properties.put(pName, prop);
            if (pRequired) required.add(pName);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }
}


