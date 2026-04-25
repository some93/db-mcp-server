package com.mcp.transport.http;

import com.mcp.infrastructure.config.AppConfig;
import com.mcp.transport.http.sse.SseSessionManager;
import com.mcp.transport.mcp.core.McpProtocolService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpTransportControllerTest {

    @Test
    void handleMcpRequest_rejectsInvalidSession() {
        AppConfig appConfig = new AppConfig();
        appConfig.getMcp().getHttp().setEnabled(true);
        McpProtocolService protocolService = mock(McpProtocolService.class);
        SseSessionManager sseSessionManager = mock(SseSessionManager.class);
        when(sseSessionManager.exists("bad-session")).thenReturn(false);

        McpTransportController controller = new McpTransportController(appConfig, protocolService, sseSessionManager);
        ResponseEntity<Map<String, Object>> response = controller.handleMcpRequest(Map.of("method", "initialize"), "bad-session");

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("2.0", response.getBody().get("jsonrpc"));
    }

    @Test
    void handleMcpRequest_delegatesToProtocolService() {
        AppConfig appConfig = new AppConfig();
        appConfig.getMcp().getHttp().setEnabled(true);
        McpProtocolService protocolService = mock(McpProtocolService.class);
        SseSessionManager sseSessionManager = mock(SseSessionManager.class);
        Map<String, Object> protocolResponse = Map.of("jsonrpc", "2.0", "id", 1, "result", Map.of());
        when(protocolService.handleRequest(eq(Map.of("method", "initialize")), isNull())).thenReturn(protocolResponse);

        McpTransportController controller = new McpTransportController(appConfig, protocolService, sseSessionManager);
        ResponseEntity<Map<String, Object>> response = controller.handleMcpRequest(Map.of("method", "initialize"), null);

        assertSame(protocolResponse, response.getBody());
    }

    @Test
    void openSse_returnsEmitterFromManager() {
        AppConfig appConfig = new AppConfig();
        appConfig.getMcp().getHttp().setEnabled(true);
        McpProtocolService protocolService = mock(McpProtocolService.class);
        SseSessionManager sseSessionManager = mock(SseSessionManager.class);
        SseEmitter emitter = new SseEmitter();
        when(sseSessionManager.createSession()).thenReturn(emitter);

        McpTransportController controller = new McpTransportController(appConfig, protocolService, sseSessionManager);

        assertSame(emitter, controller.openSse());
    }
}
