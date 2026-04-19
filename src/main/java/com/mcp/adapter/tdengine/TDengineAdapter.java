package com.mcp.adapter.tdengine;

import com.mcp.adapter.spi.DatasourceAdapter;
import com.mcp.adapter.spi.MetadataAdapter;
import com.mcp.adapter.spi.QueryExecutor;
import com.mcp.adapter.spi.SqlGuardDialectSupport;
import com.mcp.adapter.spi.WriteExecutor;

/**
 * TDengine 数据库适配器。聚合所有 TDengine SPI 实现，dialectType = "tdengine"。
 * @author ouyanghang
 */
public class TDengineAdapter implements DatasourceAdapter {

    private final TDengineMetadataAdapter metadataAdapter = new TDengineMetadataAdapter();
    private final TDengineQueryExecutor   queryExecutor   = new TDengineQueryExecutor();
    private final TDengineWriteExecutor   writeExecutor   = new TDengineWriteExecutor();
    private final TDengineSqlGuardDialect sqlGuardDialect = new TDengineSqlGuardDialect();

    @Override
    public String dialectType() {
        return "tdengine";
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


