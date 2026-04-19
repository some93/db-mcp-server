package com.mcp.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * @author ouyanghang
 */
@Component
public class HttpPortLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(HttpPortLogger.class);

    private volatile int port = -1;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
        log.info("HTTP debug server listening on http://127.0.0.1:{}", port);
        log.info("Health check: http://127.0.0.1:{}/health", port);
    }

    /** 返回实际监听端口，-1 表示尚未初始化 */
    public int getPort() {
        return port;
    }
}

