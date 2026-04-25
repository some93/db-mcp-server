package com.mcp.transport.mcp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.infrastructure.support.McpResponse;
import com.mcp.transport.http.sse.SseSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 协议核心服务，统一处理 initialize / tools/list / tools/call 的 JSON-RPC 请求。
 * @author ouyanghang
 */
@Component
public class McpProtocolService {

    private static final Logger log = LoggerFactory.getLogger(McpProtocolService.class);

    private final McpInitializeService initializeService;
    private final McpToolCatalog toolCatalog;
    private final McpToolDispatcher toolDispatcher;
    private final SseSessionManager sseSessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpProtocolService(McpInitializeService initializeService,
                              McpToolCatalog toolCatalog,
                              McpToolDispatcher toolDispatcher,
                              SseSessionManager sseSessionManager) {
        this.initializeService = initializeService;
        this.toolCatalog = toolCatalog;
        this.toolDispatcher = toolDispatcher;
        this.sseSessionManager = sseSessionManager;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> req) {
        return handleRequest(req, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> req, String sessionId) {
        if (req == null) {
            return errorResponse(null, -32600, "Invalid Request");
        }

        Object id = req.get("id");
        String method = req.get("method") instanceof String ? (String) req.get("method") : null;
        Map<String, Object> params = req.get("params") instanceof Map
                ? (Map<String, Object>) req.get("params")
                : Collections.emptyMap();

        if (method == null || method.isBlank()) {
            return errorResponse(id, -32600, "Invalid Request");
        }

        try {
            switch (method) {
                case "initialize":
                    return resultResponse(id, initializeService.buildInitializeResult());
                case "tools/list":
                    return resultResponse(id, toolCatalog.buildToolsList());
                case "tools/call":
                    return resultResponse(id, handleToolCall(params, sessionId));
                default:
                    return errorResponse(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            log.error("MCP protocol error: {}", e.getMessage(), e);
            return errorResponse(id, -32603, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Map<String, Object> params, String sessionId) throws Exception {
        String toolName = params.get("name") instanceof String ? (String) params.get("name") : null;
        if (toolName == null || toolName.isBlank()) {
            return protocolErrorToolResult("PROTOCOL_INVALID_PARAMS", "tools/call requires a tool name.");
        }

        Map<String, Object> args = params.get("arguments") instanceof Map
                ? (Map<String, Object>) params.get("arguments")
                : Collections.emptyMap();

        boolean async = Boolean.TRUE.equals(params.get("async"));
        if (async) {
            return handleAsyncToolCall(toolName, args, sessionId);
        }

        McpResponse result;
        try {
            result = toolDispatcher.dispatchTool(toolName, args);
        } catch (Exception e) {
            log.error("Tool call error [{}]: {}", toolName, e.getMessage(), e);
            result = McpResponse.error("SYSTEM_ERROR", e.getMessage(), 0);
        }

        String text = objectMapper.writeValueAsString(result);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", text);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", Collections.singletonList(content));
        response.put("isError", !result.isSuccess());
        return response;
    }

    private Map<String, Object> handleAsyncToolCall(String toolName,
                                                    Map<String, Object> args,
                                                    String sessionId) throws Exception {
        if (sessionId == null || sessionId.isBlank()) {
            return protocolErrorToolResult("PROTOCOL_ASYNC_SESSION_REQUIRED",
                    "Async tools/call requires a valid X-Mcp-Session-Id bound to /mcp/sse.");
        }
        if (!sseSessionManager.exists(sessionId)) {
            return protocolErrorToolResult("PROTOCOL_INVALID_SESSION",
                    "Async tools/call requires an active SSE session.");
        }

        String requestId = UUID.randomUUID().toString();
        sendToolProgress(sessionId, requestId, toolName, "ACCEPTED", "Async tool call accepted.");

        CompletableFuture.runAsync(() -> {
            sendToolProgress(sessionId, requestId, toolName, "RUNNING", "Async tool call started.");
            McpResponse result;
            try {
                result = toolDispatcher.dispatchTool(toolName, args);
            } catch (Exception e) {
                log.error("Async tool call error [{}]: {}", toolName, e.getMessage(), e);
                result = McpResponse.error("SYSTEM_ERROR", e.getMessage(), 0);
            }
            sendToolResult(sessionId, requestId, toolName, result);
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("sessionId", sessionId);
        data.put("toolName", toolName);
        data.put("delivery", "SSE");

        McpResponse accepted = McpResponse.ok("Async tool call accepted; result will be delivered by SSE.",
                data, 0);
        return wrapToolResponse(accepted);
    }

    private void sendToolProgress(String sessionId, String requestId, String toolName,
                                  String status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("toolName", toolName);
        payload.put("status", status);
        payload.put("message", message);
        sseSessionManager.sendEvent(sessionId, "tool_progress", payload);
    }

    private void sendToolResult(String sessionId, String requestId, String toolName, McpResponse result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("toolName", toolName);
        payload.put("success", result.isSuccess());
        payload.put("result", result);
        sseSessionManager.sendEvent(sessionId, "tool_result", payload);
    }

    private Map<String, Object> protocolErrorToolResult(String code, String message) {
        McpResponse error = McpResponse.error(code, message, 0);
        return wrapToolResponse(error);
    }

    private Map<String, Object> wrapToolResponse(McpResponse responseBody) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        try {
            content.put("text", objectMapper.writeValueAsString(responseBody));
        } catch (Exception e) {
            content.put("text", "{\"success\":false,\"code\":\"SYSTEM_ERROR\",\"message\":\"Failed to encode tool error.\"}");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", Collections.singletonList(content));
        response.put("isError", !responseBody.isSuccess());
        return response;
    }

    private Map<String, Object> resultResponse(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> errorResponse(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return response;
    }
}
