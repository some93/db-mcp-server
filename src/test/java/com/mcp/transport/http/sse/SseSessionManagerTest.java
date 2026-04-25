package com.mcp.transport.http.sse;

import com.mcp.infrastructure.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseSessionManagerTest {

    @Test
    void createSession_registersAndRemovesSession() {
        SseSessionManager manager = new SseSessionManager(appConfig(true, 30, 15));

        manager.createSession();
        String sessionId = onlySessionId(manager);

        assertTrue(manager.exists(sessionId));

        manager.removeSession(sessionId);

        assertFalse(manager.exists(sessionId));
        assertEquals(0, sessionCount(manager));
    }

    @Test
    void heartbeat_removesExpiredSession() {
        SseSessionManager manager = new SseSessionManager(appConfig(true, 1, 15));

        manager.createSession();
        String sessionId = onlySessionId(manager);
        SseClientSession session = sessions(manager).get(sessionId);
        session.setLastSeenAtMs(System.currentTimeMillis() - 2_000L);

        manager.heartbeat();

        assertFalse(manager.exists(sessionId));
        assertEquals(0, sessionCount(manager));
    }

    private AppConfig appConfig(boolean enabled, int sessionTimeoutSeconds, int heartbeatSeconds) {
        AppConfig appConfig = new AppConfig();
        appConfig.getMcp().getHttp().setEnabled(enabled);
        appConfig.getMcp().getHttp().setSessionTimeoutSeconds(sessionTimeoutSeconds);
        appConfig.getMcp().getHttp().setHeartbeatSeconds(heartbeatSeconds);
        return appConfig;
    }

    @SuppressWarnings("unchecked")
    private Map<String, SseClientSession> sessions(SseSessionManager manager) {
        try {
            Field field = SseSessionManager.class.getDeclaredField("sessions");
            field.setAccessible(true);
            return (Map<String, SseClientSession>) field.get(manager);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to inspect sessions field", e);
        }
    }

    private int sessionCount(SseSessionManager manager) {
        return sessions(manager).size();
    }

    private String onlySessionId(SseSessionManager manager) {
        return sessions(manager).keySet().iterator().next();
    }
}
