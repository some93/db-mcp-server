package com.mcp.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP 传输配置，兼容旧版 `exitOnDisconnect` 字段，并支持按 transport 细分配置。
 * @author ouyanghang
 */
@Data
public class McpConfig {

    /** 兼容旧配置：stdio 客户端断开后进程自动退出。 */
    @JsonProperty("exitOnDisconnect")
    @Getter(AccessLevel.NONE)
    @Setter
    private boolean legacyExitOnDisconnect = true;

    /** stdio transport 配置。 */
    private StdioConfig stdio = new StdioConfig();

    /** HTTP MCP transport 配置。 */
    private HttpTransportConfig http = new HttpTransportConfig();

    /**
     * 统一解析 stdio 断开退出配置：
     * 优先读取新配置 `mcp.stdio.exitOnDisconnect`，未配置时回退到旧字段。
     */
    public boolean isExitOnDisconnect() {
        return stdio.getExitOnDisconnect() != null
                ? stdio.getExitOnDisconnect()
                : legacyExitOnDisconnect;
    }

    @Data
    public static class StdioConfig {
        /** 是否启用 stdio MCP transport。 */
        private boolean enabled = true;

        /** 是否在 stdio 客户端断开后自动退出；未配置时回退到旧字段。 */
        private Boolean exitOnDisconnect;
    }

    @Data
    public static class HttpTransportConfig {
        private static final String DEFAULT_PATH = "/mcp";
        private static final String DEFAULT_SSE_PATH = "/mcp/sse";

        /** 是否启用 MCP over HTTP。 */
        private boolean enabled = false;

        /** MCP HTTP 请求入口。 */
        private String path = DEFAULT_PATH;

        /** SSE 事件流入口。 */
        private String ssePath = DEFAULT_SSE_PATH;

        /** 会话超时（秒）。 */
        private int sessionTimeoutSeconds = 1800;

        /** SSE 心跳间隔（秒）。 */
        private int heartbeatSeconds = 15;

        public String getPath() {
            return normalizePath(path, DEFAULT_PATH);
        }

        public String getSsePath() {
            return normalizePath(ssePath, DEFAULT_SSE_PATH);
        }

        private String normalizePath(String value, String defaultValue) {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            String normalized = value.trim();
            return normalized.startsWith("/") ? normalized : "/" + normalized;
        }
    }
}
