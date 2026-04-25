package com.mcp.transport.mcp.core;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具目录，统一输出 tools/list 的工具清单与参数 schema。
 * @author ouyanghang
 */
@Component
public class McpToolCatalog {

    public Map<String, Object> buildToolsList() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(tool("listDatasources",
                "List all configured datasources and their health status (UP/DOWN). " +
                        "Call this first if you don't know which datasources are available.",
                noParams()));

        tools.add(tool("locateTable",
                "Search for a table by name across all datasources. " +
                        "Use this when you don't know which datasource contains the target table. " +
                        "Returns matchStatus: EXACT_ONE, EXACT_MULTI, SIMILAR_ONLY, or NOT_FOUND.",
                params("tableName", "string", "Table name to search for (case-insensitive)", true)));

        tools.add(tool("getServiceStatus",
                "Get server version, uptime, datasource health, cached table counts and config summary.",
                noParams()));

        tools.add(tool("listTables",
                "List all tables and views in the specified datasource.",
                params("datasourceName", "string", "Datasource name (from listDatasources)", true)));

        tools.add(tool("listIndexesAndViews",
                "List all indexes and views in the specified datasource.",
                params("datasourceName", "string", "Datasource name (from listDatasources)", true)));

        tools.add(tool("describeObject",
                "Describe the column structure of a table or view.",
                params("datasourceName", "string", "Datasource name (from listDatasources)", true,
                        "objectName", "string", "Table or view name to describe", true,
                        "objectType", "string", "Optional hint: TABLE or VIEW", false)));

        tools.add(tool("executeQuery",
                "Execute a single read-only SELECT statement.",
                params("datasourceName", "string", "Datasource name", true,
                        "sql", "string", "A single SELECT statement", true)));

        tools.add(tool("executeWrite",
                "Execute one or more INSERT/UPDATE/DELETE statements in a single transaction.",
                params("datasourceName", "string", "Datasource name", true,
                        "sql", "string", "One or more write statements separated by semicolons", true)));

        tools.add(tool("createTable",
                "Execute a CREATE TABLE statement and refresh metadata cache on success.",
                params("datasourceName", "string", "Datasource name", true,
                        "sql", "string", "CREATE TABLE statement", true)));

        tools.add(tool("explainQuery",
                "Analyze the execution plan of a SELECT statement.",
                params("datasourceName", "string", "Datasource name", true,
                        "sql", "string", "SELECT statement to analyze", true)));

        tools.add(tool("refreshMetadataCache",
                "Refresh metadata cache for specified datasources or all datasources.",
                params("datasourceNames", "array", "Datasource names to refresh; empty or omitted = refresh all", false,
                        "mode", "string", "SYNC (default) or ASYNC", false)));

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

    /** Varargs: name, type, description, required, ... */
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
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
