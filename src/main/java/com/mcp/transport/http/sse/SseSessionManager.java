package com.mcp.transport.http.sse;

import com.mcp.infrastructure.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 会话管理器，负责建立会话、心跳保活和断连清理。
 * @author ouyanghang
 */
@Component
public class SseSessionManager {

    private static final Logger log = LoggerFactory.getLogger(SseSessionManager.class);

    private final AppConfig appConfig;
    private final Map<String, SseClientSession> sessions = new ConcurrentHashMap<>();

    public SseSessionManager(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public SseEmitter createSession() {
        long timeoutMs = appConfig.getMcp().getHttp().getSessionTimeoutSeconds() * 1000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        String sessionId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        SseClientSession session = SseClientSession.builder()
                .sessionId(sessionId)
                .createdAtMs(now)
                .lastSeenAtMs(now)
                .emitter(emitter)
                .build();
        sessions.put(sessionId, session);

        emitter.onCompletion(() -> removeSession(sessionId));
        emitter.onTimeout(() -> removeSession(sessionId));
        emitter.onError(ex -> removeSession(sessionId));

        sendEvent(sessionId, "session", sessionPayload(sessionId));
        return emitter;
    }

    public void sendEvent(String sessionId, String eventName, Object payload) {
        SseClientSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.getEmitter().send(SseEmitter.event()
                    .name(eventName)
                    .data(payload));
            session.setLastSeenAtMs(System.currentTimeMillis());
        } catch (IOException e) {
            log.debug("SSE send failed for session [{}]: {}", sessionId, e.getMessage());
            removeSession(sessionId);
        }
    }

    public boolean exists(String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    public void removeSession(String sessionId) {
        SseClientSession removed = sessions.remove(sessionId);
        if (removed != null) {
            removed.getEmitter().complete();
        }
    }

    @Scheduled(fixedDelayString = "#{@appConfig.mcp.http.heartbeatSeconds * 1000}")
    public void heartbeat() {
        if (!appConfig.getMcp().getHttp().isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long timeoutMs = appConfig.getMcp().getHttp().getSessionTimeoutSeconds() * 1000L;
        for (SseClientSession session : sessions.values()) {
            if (now - session.getLastSeenAtMs() > timeoutMs) {
                removeSession(session.getSessionId());
                continue;
            }
            sendEvent(session.getSessionId(), "heartbeat", heartbeatPayload(now));
        }
    }

    private Map<String, Object> sessionPayload(String sessionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("server", "db-mcp-server");
        data.put("version", "1.0.0");
        return data;
    }

    private Map<String, Object> heartbeatPayload(long now) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", now);
        return data;
    }
}
