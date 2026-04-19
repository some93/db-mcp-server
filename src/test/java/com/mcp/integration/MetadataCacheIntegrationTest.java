package com.mcp.integration;

import com.mcp.adapter.AdapterRegistry;
import com.mcp.adapter.spi.DatasourceAdapter;
import com.mcp.adapter.spi.IndexInfo;
import com.mcp.adapter.spi.MetadataAdapter;
import com.mcp.adapter.spi.ObjectDetail;
import com.mcp.adapter.spi.QueryExecutor;
import com.mcp.adapter.spi.SqlGuardDialectSupport;
import com.mcp.adapter.spi.TableInfo;
import com.mcp.adapter.spi.ViewInfo;
import com.mcp.adapter.spi.WriteExecutor;
import com.mcp.application.metadata.ListTablesService;
import com.mcp.domain.cache.DatasourceMetadata;
import com.mcp.domain.cache.MetadataCache;
import com.mcp.domain.health.HealthService;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.DatasourceConfig;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import com.mcp.infrastructure.support.McpResponse;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataCacheIntegrationTest {

    @Test
    void concurrentRefreshAndRead_keepsCacheReadable() throws Exception {
        try (HikariDataSource dataSource = createH2DataSource("cache_concurrency")) {
            AppConfig appConfig = appConfigWithDatasource("demo");
            DataSourceManager dataSourceManager = mock(DataSourceManager.class);
            DataSourceWrapper wrapper = new DataSourceWrapper(appConfig.getDatasources().get(0), dataSource);
            when(dataSourceManager.getDataSource("demo")).thenReturn(wrapper);

            AdapterRegistry adapterRegistry = mock(AdapterRegistry.class);
            when(adapterRegistry.getAdapter("mysql")).thenReturn(new StaticMetadataAdapterDatasource());

            MetadataCache metadataCache = new MetadataCache(appConfig, dataSourceManager, adapterRegistry);
            metadataCache.refresh("demo");

            HealthService healthService = new HealthService(appConfig, dataSourceManager);
            healthService.init();
            ListTablesService listTablesService = new ListTablesService(metadataCache, healthService);

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(8);
            List<Callable<Object>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tasks.add(() -> {
                    start.await(3, TimeUnit.SECONDS);
                    metadataCache.refresh("demo");
                    return metadataCache.get("demo");
                });
                tasks.add(() -> {
                    start.await(3, TimeUnit.SECONDS);
                    return listTablesService.execute("demo");
                });
            }

            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(task));
            }
            start.countDown();

            for (Future<Object> future : futures) {
                Object result = future.get(5, TimeUnit.SECONDS);
                assertNotNull(result);
                if (result instanceof McpResponse) {
                    McpResponse response = (McpResponse) result;
                    assertTrue(response.isSuccess());
                }
            }

            pool.shutdownNow();

            DatasourceMetadata finalCache = metadataCache.get("demo");
            assertNotNull(finalCache);
            assertEquals(1, finalCache.getTables().size());
            assertEquals("TABLE", finalCache.getTables().get(0).getTableType());
        }
    }

    private AppConfig appConfigWithDatasource(String name) {
        AppConfig appConfig = new AppConfig();
        appConfig.getCache().setPreloadOnStartup(false);
        DatasourceConfig datasourceConfig = new DatasourceConfig();
        datasourceConfig.setName(name);
        datasourceConfig.setType("mysql");
        datasourceConfig.setUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        datasourceConfig.setUsername("sa");
        datasourceConfig.setPassword("");
        appConfig.getDatasources().add(datasourceConfig);
        return appConfig;
    }

    private HikariDataSource createH2DataSource(String name) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(4);
        return new HikariDataSource(config);
    }

    private static class StaticMetadataAdapterDatasource implements DatasourceAdapter {

        private final MetadataAdapter metadataAdapter = new StaticMetadataAdapter();

        @Override
        public String dialectType() {
            return "mysql";
        }

        @Override
        public MetadataAdapter metadata() {
            return metadataAdapter;
        }

        @Override
        public QueryExecutor queryExecutor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WriteExecutor writeExecutor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SqlGuardDialectSupport sqlGuard() {
            return new SqlGuardDialectSupport() {
                @Override
                public String dialectType() {
                    return "mysql";
                }

                @Override
                public String checkDialectRules(String sql) {
                    return null;
                }
            };
        }
    }

    private static class StaticMetadataAdapter implements MetadataAdapter {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public List<TableInfo> listTables(Connection conn) throws Exception {
            Thread.sleep(30);
            return Collections.singletonList(TableInfo.builder()
                    .name("orders_" + sequence.incrementAndGet())
                    .tableType("TABLE")
                    .build());
        }

        @Override
        public ObjectDetail describeObject(Connection conn, String objectName, String objectType) {
            return null;
        }

        @Override
        public List<IndexInfo> listIndexes(Connection conn) {
            return Collections.emptyList();
        }

        @Override
        public List<ViewInfo> listViews(Connection conn) {
            return Collections.emptyList();
        }
    }
}
