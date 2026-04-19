package com.mcp.adapter.spi;

import java.util.List;

/**
 * 元数据查询 SPI，提供表列表、对象结构、索引、视图等数据库元信息的读取能力。
 *
 * <p>各数据库方言各自实现，上层服务层通过 {@link com.mcp.adapter.AdapterRegistry} 获取实现，无需 if-else 分支。
 *
 * <p>实现要求：
 * <ul>
 *   <li>所有方法只读，不产生任何写入操作。</li>
 *   <li>当连接账号缺少 information_schema 或系统表的读取权限时，
 *       须将 {@link java.sql.SQLException} 包装为
 *       {@link com.mcp.infrastructure.datasource.DatasourcePermissionException} 向上抛出。</li>
 *   <li>实现类需线程安全，不得在实例上保存连接状态。</li>
 * </ul>
 *
 * @see com.mcp.adapter.mysql.MySqlMetadataAdapter
 * @author ouyanghang
 */
public interface MetadataAdapter {

    /**
     * 列出当前数据库中所有表和视图的基础信息。
     *
     * @param conn 已就绪的数据库连接，生命周期由调用方管理
     * @return 表信息列表；空库时返回空列表，不返回 null
     * @throws Exception 权限错误封装为 DatasourcePermissionException
     */
    List<TableInfo> listTables(java.sql.Connection conn) throws Exception;

    /**
     * 查询指定对象（表或视图）的结构详情，包含字段列表。
     *
     * @param conn        已就绪的数据库连接
     * @param objectName  目标对象名（大小写取决于数据库 collation）
     * @param objectType  对象类型：TABLE 或 VIEW
     * @return 对象详情，含字段列表
     * @throws Exception 对象不存在时行为由实现类决定（通常返回空 columns）
     */
    ObjectDetail describeObject(java.sql.Connection conn, String objectName, String objectType) throws Exception;

    /**
     * 列出当前数据库所有索引（不含主键隐式索引）。
     *
     * @param conn 已就绪的数据库连接
     * @return 索引信息列表；无索引时返回空列表
     * @throws Exception 权限错误封装为 DatasourcePermissionException
     */
    List<IndexInfo> listIndexes(java.sql.Connection conn) throws Exception;

    /**
     * 列出当前数据库所有视图的基础信息。
     *
     * @param conn 已就绪的数据库连接
     * @return 视图信息列表；无视图时返回空列表
     * @throws Exception 权限错误封装为 DatasourcePermissionException
     */
    List<ViewInfo> listViews(java.sql.Connection conn) throws Exception;
}


