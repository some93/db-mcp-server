package com.mcp.infrastructure.config;

import lombok.Data;

/**
 * 元数据缓存参数，对应配置文件中的 {@code cache} 节点。
 * 缓存由 {@link com.mcp.domain.cache.MetadataCache} 管理，基于 Caffeine 实现。
 * @author ouyanghang
 */
@Data
public class CacheConfig {

    /**
     * 是否在服务启动时立即预热所有数据源的元数据，默认 {@code true}。
     * 预热可避免首次请求的冷启动延迟，但会增加启动时间；
     * 测试环境或无真实数据库时可设置为 {@code false}。
     */
    private boolean preloadOnStartup = true;

    /** 元数据定时刷新间隔（秒），到期后异步刷新对应数据源的全量元数据，默认 5min（300s）。 */
    private int refreshIntervalSeconds = 300;
}


