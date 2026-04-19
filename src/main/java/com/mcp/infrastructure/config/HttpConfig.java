package com.mcp.infrastructure.config;

import lombok.Data;

/**
 * @author ouyanghang
 */
@Data
public class HttpConfig {

    /** 是否启用 HTTP 调试服务 */
    private boolean enabled = true;
    /** 监听地址，默认本地回环 */
    private String host = "127.0.0.1";
    /** 监听端口，0 表示随机可用端口 */
    private int port = 0;
}

