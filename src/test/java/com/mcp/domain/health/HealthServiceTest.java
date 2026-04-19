package com.mcp.domain.health;

import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.DatasourceConfig;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    @Test
    void quickCheck_throwsWhenProbeStillFails() throws Exception {
        AppConfig appConfig = appConfigWithDatasource("demo");
        DataSourceManager dataSourceManager = mock(DataSourceManager.class);
        HikariDataSource hikariDataSource = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);
        DataSourceWrapper wrapper = new DataSourceWrapper(appConfig.getDatasources().get(0), hikariDataSource);

        when(dataSourceManager.getDataSource("demo")).thenReturn(wrapper);
        when(hikariDataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(3)).thenReturn(false);

        HealthService service = new HealthService(appConfig, dataSourceManager);
        service.init();

        DatasourceUnavailableException ex = assertThrows(DatasourceUnavailableException.class,
                () -> service.quickCheck("demo"));

        assertEquals("DOWN", service.getStatus("demo").getStatus());
        assertEquals("demo", ex.getDatasourceName());
    }

    @Test
    void quickCheck_recoversStatusWhenProbeSucceeds() throws Exception {
        AppConfig appConfig = appConfigWithDatasource("demo");
        DataSourceManager dataSourceManager = mock(DataSourceManager.class);
        HikariDataSource hikariDataSource = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);
        DataSourceWrapper wrapper = new DataSourceWrapper(appConfig.getDatasources().get(0), hikariDataSource);

        when(dataSourceManager.getDataSource("demo")).thenReturn(wrapper);
        when(hikariDataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(3)).thenReturn(true);

        HealthService service = new HealthService(appConfig, dataSourceManager);
        service.init();

        assertDoesNotThrow(() -> service.quickCheck("demo"));
        assertEquals("UP", service.getStatus("demo").getStatus());
    }

    private AppConfig appConfigWithDatasource(String name) {
        AppConfig appConfig = new AppConfig();
        DatasourceConfig datasourceConfig = new DatasourceConfig();
        datasourceConfig.setName(name);
        datasourceConfig.setType("mysql");
        appConfig.getDatasources().add(datasourceConfig);
        return appConfig;
    }
}
