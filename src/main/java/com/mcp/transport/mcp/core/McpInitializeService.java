package com.mcp.transport.mcp.core;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一生成 MCP initialize 响应，供 stdio 与 HTTP transport 复用。
 * @author ouyanghang
 */
@Component
public class McpInitializeService {

    public Map<String, Object> buildInitializeResult() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Collections.singletonMap("listChanged", false));

        Map<String, Object> transport = new LinkedHashMap<>();
        transport.put("stdio", true);
        transport.put("http", true);
        transport.put("sse", true);
        capabilities.put("transport", transport);

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "db-mcp-server");
        serverInfo.put("version", "1.0.0");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return result;
    }
}
