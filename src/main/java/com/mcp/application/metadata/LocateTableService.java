package com.mcp.application.metadata;

import com.mcp.adapter.spi.TableInfo;
import com.mcp.domain.audit.AuditLogger;
import com.mcp.domain.cache.DatasourceMetadata;
import com.mcp.domain.cache.MetadataCache;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 工具 {@code locateTable} 的应用服务，在所有数据源中跨库模糊查找表名。
 *
 * <p>匹配策略（优先级从高到低）：
 * <ol>
 *   <li>精确匹配（大小写不敏感）— 返回 {@code matchStatus=EXACT_ONE} 或 {@code EXACT_MULTI}。</li>
 *   <li>前缀/包含匹配 — 得分 80~100，{@code matchReason=prefix_match/contains_match}。</li>
 *   <li>编辑距离（Levenshtein）— 距离超过较长串 40% 时排除，剩余按得分降序，最多返回 5 条。</li>
 * </ol>
 *
 * <p>无任何匹配时返回 {@code matchStatus=NOT_FOUND}，并附上所有数据源名称列表，
 * 供 AI 客户端引导用户确认目标数据源。
 * @author ouyanghang
 */
@Service
public class LocateTableService {

    private static final int MAX_SIMILAR = 5;

    private final MetadataCache metadataCache;
    private final AppConfig appConfig;

    public LocateTableService(MetadataCache metadataCache, AppConfig appConfig) {
        this.metadataCache = metadataCache;
        this.appConfig = appConfig;
    }

    public McpResponse execute(String tableName) {
        long start = System.currentTimeMillis();
        if (tableName == null || tableName.isBlank()) {
            return McpResponse.error("PARAM_MISSING", "tableName is required.", 0);
        }

        String needle = tableName.toLowerCase();
        List<String> allDatasources = appConfig.getDatasources().stream()
                .map(d -> d.getName())
                .collect(Collectors.toList());

        // 单数据源场景：AI 应跳过此接口直接使用，此处仍正常返回供调试
        List<Map<String, Object>> matchedDatasources = new ArrayList<>();
        List<Map<String, Object>> similarTables = new ArrayList<>();

        for (String dsName : allDatasources) {
            DatasourceMetadata meta = metadataCache.get(dsName);
            if (meta == null || meta.getTables() == null) continue;

            for (TableInfo table : meta.getTables()) {
                String candidate = table.getName().toLowerCase();

                // 精确匹配（大小写不敏感）
                if (candidate.equals(needle)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("datasourceName", dsName);
                    m.put("tableName", table.getName());
                    m.put("tableType", table.getTableType());
                    matchedDatasources.add(m);
                }
            }
        }

        // 无精确匹配时，计算相似度推荐
        if (matchedDatasources.isEmpty()) {
            List<SimilarCandidate> candidates = new ArrayList<>();

            for (String dsName : allDatasources) {
                DatasourceMetadata meta = metadataCache.get(dsName);
                if (meta == null || meta.getTables() == null) continue;

                for (TableInfo table : meta.getTables()) {
                    String candidate = table.getName().toLowerCase();
                    int score = similarityScore(needle, candidate);
                    if (score > 0) {
                        candidates.add(new SimilarCandidate(dsName, table.getName(), table.getTableType(), score));
                    }
                }
            }

            // 按评分降序，取前 MAX_SIMILAR 条
            candidates.stream()
                    .sorted(Comparator.comparingInt((SimilarCandidate c) -> c.score).reversed())
                    .limit(MAX_SIMILAR)
                    .forEach(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("datasourceName", c.datasourceName);
                        m.put("tableName", c.tableName);
                        m.put("tableType", c.tableType);
                        m.put("matchReason", c.matchReason(needle));
                        similarTables.add(m);
                    });
        }

        String matchStatus = matchedDatasources.isEmpty()
                ? (similarTables.isEmpty() ? "NOT_FOUND" : "SIMILAR_ONLY")
                : (matchedDatasources.size() == 1 ? "EXACT_ONE" : "EXACT_MULTI");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchStatus", matchStatus);
        data.put("matchedDatasources", matchedDatasources);
        data.put("similarTables", similarTables);
        // NOT_FOUND 时附上所有数据源列表，供 AI 引导用户
        if ("NOT_FOUND".equals(matchStatus)) {
            data.put("allDatasources", allDatasources);
        }

        AuditLogger.log("locateTable", null, tableName, true, System.currentTimeMillis() - start, null, null);
        return McpResponse.ok(data, System.currentTimeMillis() - start);
    }

    /**
     * 综合相似度评分：前缀匹配 > 包含匹配 > 编辑距离。
     * 返回 0 表示不相似，不应列入推荐。
     */
    private int similarityScore(String needle, String candidate) {
        if (candidate.startsWith(needle) || needle.startsWith(candidate)) return 100;
        if (candidate.contains(needle) || needle.contains(candidate)) return 80;
        int ed = editDistance(needle, candidate);
        // 编辑距离超过较长字符串长度的 40% 则视为不相关
        int maxLen = Math.max(needle.length(), candidate.length());
        if (ed > maxLen * 0.4) return 0;
        return Math.max(1, 60 - ed * 10);
    }

    /** 标准 Levenshtein 编辑距离，O(m*n) 空间优化版 */
    private int editDistance(String a, String b) {
        int m = a.length(), n = b.length();
        int[] dp = new int[n + 1];
        for (int j = 0; j <= n; j++) dp[j] = j;
        for (int i = 1; i <= m; i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= n; j++) {
                int temp = dp[j];
                dp[j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? prev
                        : 1 + Math.min(prev, Math.min(dp[j], dp[j - 1]));
                prev = temp;
            }
        }
        return dp[n];
    }

    private static class SimilarCandidate {
        final String datasourceName;
        final String tableName;
        final String tableType;
        final int score;

        SimilarCandidate(String datasourceName, String tableName, String tableType, int score) {
            this.datasourceName = datasourceName;
            this.tableName = tableName;
            this.tableType = tableType;
            this.score = score;
        }

        String matchReason(String needle) {
            String c = tableName.toLowerCase();
            if (c.startsWith(needle) || needle.startsWith(c)) return "prefix_match";
            if (c.contains(needle) || needle.contains(c)) return "contains_match";
            return "edit_distance";
        }
    }
}


