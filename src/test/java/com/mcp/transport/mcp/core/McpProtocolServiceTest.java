package com.mcp.transport.mcp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.infrastructure.support.McpResponse;
import com.mcp.transport.http.sse.SseSessionManager;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class McpProtocolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void handleInitialize_returnsProtocolResult() {
        McpProtocolService service = new McpProtocolService(
                new McpInitializeService(),
                new McpToolCatalog(),
                mock(McpToolDispatcher.class),
                mock(SseSessionManager.class)
        );

        Map<String, Object> response = service.handleRequest(Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize"
        ));

        assertEquals("2.0", response.get("jsonrpc"));
        assertEquals(1, response.get("id"));
        assertTrue(response.containsKey("result"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void handleToolsList_returnsTools() {
        McpProtocolService service = new McpProtocolService(
                new McpInitializeService(),
                new McpToolCatalog(),
                mock(McpToolDispatcher.class),
                mock(SseSessionManager.class)
        );

        Map<String, Object> response = service.handleRequest(Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/list"
        ));

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertNotNull(result);
        assertFalse(((java.util.List<?>) result.get("tools")).isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void handleToolsCall_wrapsToolResponse() throws Exception {
        McpToolDispatcher dispatcher = mock(McpToolDispatcher.class);
        when(dispatcher.dispatchTool("listDatasources", Collections.emptyMap()))
                .thenReturn(McpResponse.ok("OK", Map.of("datasources", Collections.emptyList()), 1));

        McpProtocolService service = new McpProtocolService(
                new McpInitializeService(),
                new McpToolCatalog(),
                dispatcher,
                mock(SseSessionManager.class)
        );

        Map<String, Object> response = service.handleRequest(Map.of(
                "jsonrpc", "2.0",
                "id", 3,
                "method", "tools/call",
                "params", Map.of(
                        "name", "listDatasources",
                        "arguments", Collections.emptyMap()
                )
        ));

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertEquals(false, result.get("isError"));
        java.util.List<Map<String, Object>> content = (java.util.List<Map<String, Object>>) result.get("content");
        Map<String, Object> payload = objectMapper.readValue((String) content.get(0).get("text"), Map.class);
        assertEquals("SUCCESS", payload.get("code"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void handleAsyncToolsCall_returnsAcceptedAndPushesSseEvents() throws Exception {
        McpToolDispatcher dispatcher = mock(McpToolDispatcher.class);
        when(dispatcher.dispatchTool("refreshMetadataCache", Collections.emptyMap()))
                .thenReturn(McpResponse.ok("triggered", Map.of("mode", "ASYNC"), 1));

        SseSessionManager sessionManager = mock(SseSessionManager.class);
        when(sessionManager.exists("session-1")).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(2);
        Map<String, Object> progressEvent = new LinkedHashMap<>();
        Map<String, Object> resultEvent = new LinkedHashMap<>();
        doAnswer(invocation -> {
            String eventName = invocation.getArgument(1);
            Object payload = invocation.getArgument(2);
            if ("tool_progress".equals(eventName) && progressEvent.isEmpty()) {
                progressEvent.put("payload", payload);
                latch.countDown();
            }
            if ("tool_result".equals(eventName) && resultEvent.isEmpty()) {
                resultEvent.put("payload", payload);
                latch.countDown();
            }
            return null;
        }).when(sessionManager).sendEvent(eq("session-1"), any(), any());

        McpProtocolService service = new McpProtocolService(
                new McpInitializeService(),
                new McpToolCatalog(),
                dispatcher,
                sessionManager
        );

        Map<String, Object> response = service.handleRequest(Map.of(
                "jsonrpc", "2.0",
                "id", 5,
                "method", "tools/call",
                "params", Map.of(
                        "name", "refreshMetadataCache",
                        "arguments", Collections.emptyMap(),
                        "async", true
                )
        ), "session-1");

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        java.util.List<Map<String, Object>> content = (java.util.List<Map<String, Object>>) result.get("content");
        Map<String, Object> payload = objectMapper.readValue((String) content.get(0).get("text"), Map.class);
        assertEquals("SUCCESS", payload.get("code"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        Map<String, Object> toolResultPayload = (Map<String, Object>) resultEvent.get("payload");
        assertEquals("refreshMetadataCache", toolResultPayload.get("toolName"));
        assertEquals(true, toolResultPayload.get("success"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void handleAsyncToolsCall_withoutSession_returnsToolError() throws Exception {
        McpProtocolService service = new McpProtocolService(
                new McpInitializeService(),
                new McpToolCatalog(),
                mock(McpToolDispatcher.class),
                mock(SseSessionManager.class)
        );

        Map<String, Object> response = service.handleRequest(Map.of(
                "jsonrpc", "2.0",
                "id", 6,
                "method", "tools/call",
                "params", Map.of(
                        "name", "refreshMetadataCache",
                        "arguments", Collections.emptyMap(),
                        "async", true
                )
        ));

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertEquals(true, result.get("isError"));
        java.util.List<Map<String, Object>> content = (java.util.List<Map<String, Object>>) result.get("content");
        Map<String, Object> payload = objectMapper.readValue((String) content.get(0).get("text"), Map.class);
        assertEquals("PROTOCOL_ASYNC_SESSION_REQUIRED", payload.get("code"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void handleUnknownMethod_returnsJsonRpcError() {
        McpProtocolService service = new McpProtocolService(
                new McpInitializeService(),
                new McpToolCatalog(),
                mock(McpToolDispatcher.class),
                mock(SseSessionManager.class)
        );

        Map<String, Object> response = service.handleRequest(Map.of(
                "jsonrpc", "2.0",
                "id", 4,
                "method", "unknown/method"
        ));

        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals(-32601, error.get("code"));
    }
}
