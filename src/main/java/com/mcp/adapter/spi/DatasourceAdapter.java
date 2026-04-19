package com.mcp.adapter.spi;

/**
 * 数据库适配器聚合接口，每种数据库方言（MySQL、TDengine 等）提供一个实现。
 *
 * <p>该接口汇聚了数据库操作所需的全部子能力，上层代码仅依赖此接口，
 * 通过 {@link com.mcp.adapter.AdapterRegistry} 按 dialectType 获取实现，实现零 if-else 的方言切换。
 *
 * <p>新增数据库方言时，实现此接口并注册为 Spring Bean 即可，无需修改任何业务代码。
 *
 * @see com.mcp.adapter.mysql.MySqlAdapter
 * @see com.mcp.adapter.AdapterRegistry
 * @author ouyanghang
 */
public interface DatasourceAdapter {

    /**
     * 返回该适配器支持的方言标识，与配置文件中 {@code datasources[].type} 字段对应（大小写不敏感）。
     * 例如：{@code "mysql"}、{@code "tdengine"}。
     */
    String dialectType();

    /** 元数据查询能力：列表、描述、索引、视图。 */
    MetadataAdapter metadata();

    /** 只读查询执行能力：SELECT + EXPLAIN。 */
    QueryExecutor queryExecutor();

    /** 写入执行能力：INSERT / UPDATE / DELETE / DDL。 */
    WriteExecutor writeExecutor();

    /** 方言级 SQL 安全检查规则（可返回额外禁止逻辑）。 */
    SqlGuardDialectSupport sqlGuard();
}


