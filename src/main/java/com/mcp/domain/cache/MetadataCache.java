package com.mcp.domain.cache;

import com.mcp.adapter.AdapterRegistry;
import com.mcp.adapter.spi.*;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.DatasourceConfig;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元数据缓存：启动预热、定时刷新、手动刷新。
 * 刷新按数据源隔离，单个失败不影响其他。
 * @author ouyanghang
 */
@Component
public class MetadataCache {

    private static final Logger log = LoggerFactory.getLogger(MetadataCache.class);

    private final AppConfig appConfig;
    private final DataSourceManager dataSourceManager;
    private final AdapterRegistry adapterRegistry;

    private final Map<String, DatasourceMetadata> cache = new ConcurrentHashMap<>();

    public MetadataCache(AppConfig appConfig, DataSourceManager dataSourceManager, AdapterRegistry adapterRegistry) {
        this.appConfig = appConfig;
        this.dataSourceManager = dataSourceManager;
        this.adapterRegistry = adapterRegistry;
    }

    @PostConstruct
    public void preload() {
        if (!appConfig.getCache().isPreloadOnStartup()) {
            return;
        }
        log.info("Preloading metadata cache for {} datasources...", appConfig.getDatasources().size());
        List<String> failed = new ArrayList<>();
        for (DatasourceConfig ds : appConfig.getDatasources()) {
            try {
                refresh(ds.getName());
                log.info("Metadata preloaded for [{}]", ds.getName());
            } catch (Exception e) {
                log.error("Metadata preload FAILED for [{}]: {}", ds.getName(), e.getMessage());
                failed.add(ds.getName());
            }
        }
        // 任何一个数据源预热失败即中止启动，避免带着空缓存上线后返回误导性的空元数据。
        if (!failed.isEmpty()) {
            throw new IllegalStateException(
                    "Metadata preload failed for datasource(s): " + failed + ". Service startup aborted.");
        }
    }

    /** 定时刷新。fixedDelayString 用 SpEL 读取配置值（单位 ms），delay 而非 rate 确保上一次完成后才开始计时。 */
    @Scheduled(fixedDelayString = "#{@appConfig.cache.refreshIntervalSeconds * 1000}")
    public void scheduledRefresh() {
        log.debug("Scheduled metadata refresh triggered");
        for (DatasourceConfig ds : appConfig.getDatasources()) {
            try {
                refresh(ds.getName());
            } catch (Exception e) {
                log.warn("Scheduled metadata refresh failed for [{}]: {}", ds.getName(), e.getMessage());
                markStale(ds.getName(), e.getMessage());
            }
        }
    }

    public void refresh(String datasourceName) throws Exception {
        DataSourceWrapper wrapper = dataSourceManager.getDataSource(datasourceName);
        DatasourceAdapter adapter = adapterRegistry.getAdapter(wrapper.getConfig().getType());

        try (Connection conn = wrapper.getHikariDataSource().getConnection()) {
            List<TableInfo> tables = adapter.metadata().listTables(conn);
            List<IndexInfo> indexes = adapter.metadata().listIndexes(conn);
            List<ViewInfo> views = adapter.metadata().listViews(conn);

            DatasourceMetadata meta = DatasourceMetadata.builder()
                    .datasourceName(datasourceName)
                    .tables(tables)
                    .indexes(indexes)
                    .views(views)
                    .loadedAtMs(System.currentTimeMillis())
                    .stale(false)
                    .build();
            cache.put(datasourceName.toLowerCase(), meta);
        }
    }

    public DatasourceMetadata get(String datasourceName) {
        return cache.get(datasourceName.toLowerCase());
    }

    public Collection<DatasourceMetadata> getAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    private void markStale(String datasourceName, String reason) {
        DatasourceMetadata existing = cache.get(datasourceName.toLowerCase());
        // 刷新失败后保留旧缓存继续提供服务，并打上 stale 标记，
        // 调用方可在响应中向 AI 明确提示"元数据可能不是最新的"。
        if (existing != null) {
            existing.setStale(true);
            existing.setStaleReason(reason);
        }
    }
}


