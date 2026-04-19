package com.mcp.infrastructure.config;

import lombok.Data;

/**
 * @author ouyanghang
 */
@Data
public class McpConfig {

    /** stdio 客户端断开后进程自动退出 */
    private boolean exitOnDisconnect = true;
}

