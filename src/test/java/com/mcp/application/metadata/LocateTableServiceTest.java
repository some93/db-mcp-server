package com.mcp.application.metadata;

import com.mcp.adapter.spi.TableInfo;
import com.mcp.domain.cache.DatasourceMetadata;
import com.mcp.domain.cache.MetadataCache;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.DatasourceConfig;
import com.mcp.infrastructure.support.McpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocateTableServiceTest {

    @Mock
    private MetadataCache metadataCache;

    @Mock
    private AppConfig appConfig;

    private LocateTableService service;

    private DatasourceConfig ds1;

    @BeforeEach
    void setUp() {
        ds1 = new DatasourceConfig();
        ds1.setName("main_db");
        when(appConfig.getDatasources()).thenReturn(Collections.singletonList(ds1));

        List<TableInfo> tables = Arrays.asList(
                TableInfo.builder().name("orders").tableType("TABLE").build(),
                TableInfo.builder().name("order_items").tableType("TABLE").build(),
                TableInfo.builder().name("users").tableType("TABLE").build(),
                TableInfo.builder().name("products").tableType("TABLE").build()
        );
        DatasourceMetadata meta = DatasourceMetadata.builder()
                .datasourceName("main_db")
                .tables(tables)
                .build();
        when(metadataCache.get("main_db")).thenReturn(meta);

        service = new LocateTableService(metadataCache, appConfig);
    }

    @Test
    void execute_exactMatch_returnsExactOne() {
        McpResponse resp = service.execute("orders");
        assertTrue(resp.isSuccess());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("EXACT_ONE", data.get("matchStatus"));
        List<?> matched = (List<?>) data.get("matchedDatasources");
        assertEquals(1, matched.size());
    }

    @Test
    void execute_exactMatch_caseInsensitive() {
        McpResponse resp = service.execute("ORDERS");
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("EXACT_ONE", data.get("matchStatus"));
    }

    @Test
    void execute_prefixMatch_returnsSimilar() {
        McpResponse resp = service.execute("order");
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("SIMILAR_ONLY", data.get("matchStatus"));
        List<?> similar = (List<?>) data.get("similarTables");
        assertFalse(similar.isEmpty());
        assertTrue(similar.size() >= 2);
    }

    @Test
    void execute_containsMatch_returnsSimilar() {
        McpResponse resp = service.execute("user");
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("SIMILAR_ONLY", data.get("matchStatus"));
        List<Map<String, Object>> similar = (List<Map<String, Object>>) data.get("similarTables");
        assertTrue(similar.stream().anyMatch(m -> "users".equals(m.get("tableName"))));
    }

    @Test
    void execute_noMatch_returnsNotFound() {
        McpResponse resp = service.execute("xyz_completely_unrelated");
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("NOT_FOUND", data.get("matchStatus"));
        assertNotNull(data.get("allDatasources"));
    }

    @Test
    void execute_blankTableName_returnsError() {
        McpResponse resp = service.execute("  ");
        assertFalse(resp.isSuccess());
        assertEquals("PARAM_MISSING", resp.getCode());
    }

    @Test
    void execute_nullTableName_returnsError() {
        McpResponse resp = service.execute(null);
        assertFalse(resp.isSuccess());
        assertEquals("PARAM_MISSING", resp.getCode());
    }

    @Test
    void execute_exactMultiMatch_returnsExactMulti() {
        DatasourceConfig ds2 = new DatasourceConfig();
        ds2.setName("secondary_db");
        when(appConfig.getDatasources()).thenReturn(Arrays.asList(ds1, ds2));

        DatasourceMetadata meta2 = DatasourceMetadata.builder()
                .datasourceName("secondary_db")
                .tables(Collections.singletonList(
                        TableInfo.builder().name("orders").tableType("TABLE").build()))
                .build();
        when(metadataCache.get("secondary_db")).thenReturn(meta2);

        McpResponse resp = service.execute("orders");
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("EXACT_MULTI", data.get("matchStatus"));
        List<?> matched = (List<?>) data.get("matchedDatasources");
        assertEquals(2, matched.size());
    }

    @Test
    void execute_similarResultsCappedAtFive() {
        List<TableInfo> manyTables = Arrays.asList(
                TableInfo.builder().name("test1").tableType("TABLE").build(),
                TableInfo.builder().name("test2").tableType("TABLE").build(),
                TableInfo.builder().name("test3").tableType("TABLE").build(),
                TableInfo.builder().name("test4").tableType("TABLE").build(),
                TableInfo.builder().name("test5").tableType("TABLE").build(),
                TableInfo.builder().name("test6").tableType("TABLE").build(),
                TableInfo.builder().name("test7").tableType("TABLE").build()
        );
        DatasourceMetadata meta = DatasourceMetadata.builder()
                .datasourceName("main_db")
                .tables(manyTables)
                .build();
        when(metadataCache.get("main_db")).thenReturn(meta);

        McpResponse resp = service.execute("test");
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        List<?> similar = (List<?>) data.get("similarTables");
        assertTrue(similar.size() <= 5);
    }
}
