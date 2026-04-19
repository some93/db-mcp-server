package com.mcp.application.cache;

import com.mcp.domain.cache.MetadataCache;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author ouyanghang
 */
@Service
public class RefreshMetadataCacheService {

    private final MetadataCache metadataCache;
    private final AppConfig appConfig;

    public RefreshMetadataCacheService(MetadataCache metadataCache, AppConfig appConfig) {
        this.metadataCache = metadataCache;
        this.appConfig = appConfig;
    }

    /**
     * @param datasourceNames null 或空 = 全量刷新
     * @param mode            SYNC（默认，同步等待完成）或 ASYNC（后台异步，立即返回）
     */
    public McpResponse execute(List<String> datasourceNames, String mode) {
        long start = System.currentTimeMillis();

        List<String> targets = (datasourceNames == null || datasourceNames.isEmpty())
                ? appConfig.getDatasources().stream()
                        .map(d -> d.getName())
                        .collect(Collectors.toList())
                : datasourceNames;

        boolean async = "ASYNC".equalsIgnoreCase(mode);

        if (async) {
            return executeAsync(targets, start);
        } else {
            return executeSync(targets, start);
        }
    }

    private McpResponse executeAsync(List<String> targets, long start) {
        for (String name : targets) {
            CompletableFuture.runAsync(() -> {
                try {
                    metadataCache.refresh(name);
                } catch (Exception ignored) {
                    // 后台刷新失败不传播，下次调用前缓存仍有效
                }
            });
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("triggered", true);
        data.put("targets", targets);
        data.put("mode", "ASYNC");
        return McpResponse.ok("Async metadata refresh triggered for " + targets.size() + " datasource(s).",
                data, System.currentTimeMillis() - start);
    }

    private McpResponse executeSync(List<String> targets, long start) {
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (String name : targets) {
            long dsStart = System.currentTimeMillis();
            try {
                metadataCache.refresh(name);
                successCount++;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("datasourceName", name);
                r.put("status", "SUCCESS");
                r.put("costMs", System.currentTimeMillis() - dsStart);
                r.put("message", null);
                results.add(r);
            } catch (Exception e) {
                failedCount++;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("datasourceName", name);
                r.put("status", "FAILED");
                r.put("costMs", System.currentTimeMillis() - dsStart);
                r.put("message", e.getMessage());
                results.add(r);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("successCount", successCount);
        data.put("failedCount", failedCount);
        data.put("totalCostMs", System.currentTimeMillis() - start);
        data.put("results", results);

        if (failedCount == 0) {
            return McpResponse.ok("Metadata cache refreshed successfully.", data, System.currentTimeMillis() - start);
        } else if (successCount == 0) {
            return McpResponse.error("CACHE_REFRESH_FAILED", "All datasource refreshes failed.", System.currentTimeMillis() - start);
        } else {
            McpResponse resp = McpResponse.partialSuccess(
                    successCount + " succeeded, " + failedCount + " failed.", System.currentTimeMillis() - start);
            resp.setData(data);
            return resp;
        }
    }
}

