package com.mcp.transport.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.transport.mcp.core.McpProtocolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 手写 MCP stdio JSON-RPC 2.0 协议层。
 * stdout 只输出 MCP 协议消息，stderr 输出所有日志，严格隔离。
 * @author ouyanghang
 */
@Component
public class McpStdioServer {

    private static final Logger log = LoggerFactory.getLogger(McpStdioServer.class);

    private final ObjectMapper mapper;
    private final AppConfig appConfig;
    private final McpProtocolService protocolService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;
    private PrintStream mcpOut;

    public McpStdioServer(AppConfig appConfig,
                          McpProtocolService protocolService) {
        this.appConfig = appConfig;
        this.protocolService = protocolService;
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @PostConstruct
    public void start() {
        if (!appConfig.getMcp().getStdio().isEnabled()) {
            log.info("MCP stdio server disabled by configuration.");
            return;
        }
        // 独占 stdout，所有日志必须走 stderr（System.err），绝不能混入 stdout。
        // BufferedOutputStream 提升吞吐，false 禁用 autoFlush（手动 flush 保证消息完整性）。
        mcpOut = new PrintStream(new BufferedOutputStream(System.out), false, StandardCharsets.UTF_8);
        running.set(true);
        readerThread = new Thread(this::readLoop, "mcp-stdio-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        log.info("MCP stdio server started.");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (readerThread != null) readerThread.interrupt();
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    handleMessage(line);
                } catch (Exception e) {
                    log.error("Error handling MCP message: {}", e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            if (running.get()) log.warn("MCP stdin closed: {}", e.getMessage());
        }

        // stdin EOF = Claude Desktop 已关闭连接。按设计退出进程，避免孤儿 JVM 常驻。
        if (appConfig.getMcp().isExitOnDisconnect()) {
            log.info("MCP client disconnected, exiting as configured.");
            System.exit(0);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String json) throws Exception {
        Map<String, Object> req = mapper.readValue(json, Map.class);
        Object id = req.get("id");
        String method = (String) req.get("method");
        log.debug("MCP request: method={} id={}", method, id);
        Map<String, Object> response = protocolService.handleRequest(req);
        writeLine(mapper.writeValueAsString(response));
    }

    private synchronized void writeLine(String line) {
        mcpOut.println(line);
        mcpOut.flush();
    }
}


