package com.mcp.infrastructure.config;

import lombok.Data;

/**
 * @author ouyanghang
 */
@Data
public class HealthConfig {

    /** 数据源健康检查间隔（秒） */
    private int checkIntervalSeconds = 30;
}

