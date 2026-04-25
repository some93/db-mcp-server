package com.mcp.transport.http;

import com.mcp.infrastructure.config.AppConfig;
import com.mcp.transport.http.sse.SseSessionManager;
import com.mcp.transport.mcp.core.McpProtocolService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP over HTTP 传输控制器，路由由 `mcp.http.path` / `mcp.http.ssePath` 配置驱动。
 * @author ouyanghang
 */
@RestController
public class McpTransportController {

    private final AppConfig appConfig;
    private final McpProtocolService protocolService;
    private final SseSessionManager sseSessionManager;

    public McpTransportController(AppConfig appConfig,
                                  McpProtocolService protocolService,
                                  SseSessionManager sseSessionManager) {
        this.appConfig = appConfig;
        this.protocolService = protocolService;
        this.sseSessionManager = sseSessionManager;
    }

    @PostMapping("${mcp.http.path:/mcp}")
    public ResponseEntity<Map<String, Object>> handleMcpRequest(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Mcp-Session-Id", required = false) String sessionId) {
        if (!appConfig.getMcp().getHttp().isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        // 第一阶段 session 只是轻会话，不强制要求 tools/call 必须携带 sessionId。
        // 若传入则仅校验格式化存在性，避免客户端误以为自己仍持有有效会话。
        if (sessionId != null && !sessionId.isBlank() && !sseSessionManager.exists(sessionId)) {
            return ResponseEntity.ok(protocolError(null, -32602, "Invalid sessionId: " + sessionId));
        }
        return ResponseEntity.ok(protocolService.handleRequest(body, sessionId));
    }

    @GetMapping(path = "${mcp.http.ssePath:/mcp/sse}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter openSse() {
        return sseSessionManager.createSession();
    }

    private Map<String, Object> protocolError(Object id, int code, String message) {
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
