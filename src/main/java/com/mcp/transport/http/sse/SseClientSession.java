package com.mcp.transport.http.sse;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 单个 HTTP SSE 客户端会话。
 * @author ouyanghang
 */
@Data
@Builder
public class SseClientSession {
    private String sessionId;
    private long createdAtMs;
    private long lastSeenAtMs;
    private SseEmitter emitter;
}
