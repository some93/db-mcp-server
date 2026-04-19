package com.mcp.adapter.mysql;

import com.mcp.adapter.spi.*;

/**
 * MySQL 数据库适配器聚合实现，整合 MySQL 的元数据、查询、写入、安全检查四个子能力。
 *
 * <p>各子能力实例为单例，共享在此适配器内，线程安全（均为无状态实现）。
 * 通过 {@link com.mcp.adapter.AdapterRegistry} 以 {@code "mysql"} 为键注册后，
 * 所有 type=mysql 的数据源均复用此适配器实例。
 * @author ouyanghang
 */
public class MySqlAdapter implements DatasourceAdapter {

    private final MySqlMetadataAdapter metadataAdapter = new MySqlMetadataAdapter();
    private final MySqlQueryExecutor queryExecutor = new MySqlQueryExecutor();
    private final MySqlWriteExecutor writeExecutor = new MySqlWriteExecutor();
    private final MySqlSqlGuardDialect sqlGuardDialect = new MySqlSqlGuardDialect();

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
        return queryExecutor;
    }

    @Override
    public WriteExecutor writeExecutor() {
        return writeExecutor;
    }

    @Override
    public SqlGuardDialectSupport sqlGuard() {
        return sqlGuardDialect;
    }
}


