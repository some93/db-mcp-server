package com.mcp.infrastructure.datasource;

import com.mcp.infrastructure.config.DatasourceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * @author ouyanghang
 */
public class DataSourceFactory {

    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String TDENGINE_DRIVER = "com.taosdata.jdbc.rs.RestfulDriver";

    public static HikariDataSource create(DatasourceConfig config) {
        DataSourceType type = DataSourceType.fromString(config.getType());
        HikariConfig hikari = new HikariConfig();

        hikari.setPoolName("hikari-" + config.getName());
        hikari.setJdbcUrl(config.getUrl());
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());

        DatasourceConfig.PoolConfig pool = config.getPool();
        hikari.setMinimumIdle(pool.getMinimumIdle());
        hikari.setConnectionTimeout(pool.getConnectionTimeoutMs());
        hikari.setIdleTimeout(pool.getIdleTimeoutMs());
        hikari.setMaxLifetime(pool.getMaxLifetimeMs());

        switch (type) {
            case MYSQL:
                hikari.setDriverClassName(MYSQL_DRIVER);
                hikari.setMaximumPoolSize(pool.getMaximumPoolSize());
                hikari.setConnectionTestQuery("SELECT 1");
                break;
            case TDENGINE:
                hikari.setDriverClassName(TDENGINE_DRIVER);
                // TDengine WebSocket 连接较重，限制最大连接数
                hikari.setMaximumPoolSize(Math.min(pool.getMaximumPoolSize(), 5));
                hikari.setConnectionTestQuery("SELECT SERVER_VERSION()");
                break;
            default:
                throw new IllegalArgumentException("Unsupported datasource type: " + config.getType());
        }

        return new HikariDataSource(hikari);
    }
}

