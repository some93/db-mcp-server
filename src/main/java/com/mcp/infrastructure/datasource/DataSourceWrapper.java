package com.mcp.infrastructure.datasource;

import com.mcp.infrastructure.config.DatasourceConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;

/**
 * 数据源的运行时包装对象，聚合配置信息和 HikariCP 连接池实例。
 *
 * <p>通过 {@link DataSourceManager#getDataSource} 获取，生命周期由 DataSourceManager 统一管理。
 * @author ouyanghang
 */
@Getter
public class DataSourceWrapper {

    /** 原始配置信息，包含 URL、账号、连接池参数等。 */
    private final DatasourceConfig config;

    /** HikariCP 连接池实例，用于获取 JDBC Connection。 */
    private final HikariDataSource hikariDataSource;

    /** 枚举类型的数据库方言，由 {@link DatasourceConfig#getType()} 解析而来。 */
    private final DataSourceType type;

    public DataSourceWrapper(DatasourceConfig config, HikariDataSource hikariDataSource) {
        this.config = config;
        this.hikariDataSource = hikariDataSource;
        this.type = DataSourceType.fromString(config.getType());
    }

    /** 快捷方法，返回数据源名称（等同于 {@code config.getName()}）。 */
    public String getName() {
        return config.getName();
    }

    /** 关闭 HikariCP 连接池；重复调用安全（幂等）。 */
    public void close() {
        if (hikariDataSource != null && !hikariDataSource.isClosed()) {
            hikariDataSource.close();
        }
    }
}


