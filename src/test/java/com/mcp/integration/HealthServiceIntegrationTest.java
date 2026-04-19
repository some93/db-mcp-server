package com.mcp.integration;

import com.mcp.domain.health.DatasourceUnavailableException;
import com.mcp.domain.health.HealthService;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.DatasourceConfig;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceIntegrationTest {

    @Test
    void quickCheck_recoversFromDownWhenDatasourceBecomesReachable() throws Exception {
        try (HikariDataSource dataSource = createH2DataSource("health_recovery")) {
            AppConfig appConfig = appConfigWithDatasource("demo");
            DataSourceManager dataSourceManager = mock(DataSourceManager.class);
            DataSourceWrapper wrapper = new DataSourceWrapper(appConfig.getDatasources().get(0), dataSource);

            when(dataSourceManager.getDataSource("demo")).thenReturn(wrapper);

            HealthService service = new HealthService(appConfig, dataSourceManager);
            service.init();
            service.getStatus("demo").setStatus("DOWN");

            assertDoesNotThrow(() -> service.quickCheck("demo"));
            assertEquals("UP", service.getStatus("demo").getStatus());
        }
    }

    @Test
    void quickCheck_marksDatasourceDownWhenPoolIsClosed() throws Exception {
        HikariDataSource dataSource = createH2DataSource("health_failure");
        AppConfig appConfig = appConfigWithDatasource("demo");
        DataSourceManager dataSourceManager = mock(DataSourceManager.class);
        DataSourceWrapper wrapper = new DataSourceWrapper(appConfig.getDatasources().get(0), dataSource);

        when(dataSourceManager.getDataSource("demo")).thenReturn(wrapper);

        HealthService service = new HealthService(appConfig, dataSourceManager);
        service.init();
        service.getStatus("demo").setStatus("DOWN");

        dataSource.close();

        assertThrows(DatasourceUnavailableException.class, () -> service.quickCheck("demo"));
        assertEquals("DOWN", service.getStatus("demo").getStatus());
    }

    private AppConfig appConfigWithDatasource(String name) {
        AppConfig appConfig = new AppConfig();
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
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }
}
