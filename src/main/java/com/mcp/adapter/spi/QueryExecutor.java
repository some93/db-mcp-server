package com.mcp.adapter.spi;

import java.util.List;
import java.util.Map;

/**
 * 查询执行 SPI，负责执行只读 SELECT 语句并返回结构化行集合。
 *
 * <p>实现要求：
 * <ul>
 *   <li>只允许执行 SELECT；DML/DDL 须在上层安全链（{@link com.mcp.domain.security.SqlGuard}）拦截，
 *       executor 不需要重复校验。</li>
 *   <li>通过 {@code maxRows} 在 JDBC 层截断结果，而非全量读取后裁剪，避免 OOM。</li>
 *   <li>实现类需线程安全，不得在实例上保存连接或结果集状态。</li>
 * </ul>
 *
 * @see com.mcp.adapter.mysql.MySqlQueryExecutor
 * @author ouyanghang
 */
public interface QueryExecutor {

    /**
     * 执行 SELECT 查询，返回结构化行集合。
     *
     * @param conn           已就绪的数据库连接，生命周期由调用方管理
     * @param sql            经过安全检查、注释清洗后的 SELECT 语句
     * @param maxRows        最大返回行数，超过时截断并在结果中标记 {@code truncated=true}
     * @param timeoutSeconds 查询超时（秒）
     * @return 行集合及元信息
     * @throws Exception 权限错误须封装为 {@link com.mcp.infrastructure.datasource.DatasourcePermissionException}
     */
    QueryResult executeQuery(java.sql.Connection conn, String sql, int maxRows, int timeoutSeconds) throws Exception;

    /**
     * 获取 SQL 的执行计划（EXPLAIN）。
     *
     * @param conn           已就绪的数据库连接
     * @param sql            待分析的 SELECT 语句
     * @param timeoutSeconds 超时（秒）
     * @return 执行计划，包含使用的索引和预估行数
     * @throws Exception 权限错误须封装为 {@link com.mcp.infrastructure.datasource.DatasourcePermissionException}
     */
    ExplainResult explainQuery(java.sql.Connection conn, String sql, int timeoutSeconds) throws Exception;

    /**
     * SELECT 查询的执行结果。
     */
    @lombok.Data
    @lombok.Builder
    class QueryResult {
        /** 返回的行列表，每行是字段名→值的有序映射（LinkedHashMap 保持列顺序）。 */
        private List<Map<String, Object>> rows;

        /** 实际返回的行数，等于 {@code rows.size()}。 */
        private int returnedRows;

        /** 数据库报告的总行数（部分数据库不支持，则为 null）。 */
        private Long totalRows;

        /** 结果是否被 maxRows 截断；为 true 时实际结果可能多于 returnedRows。 */
        private boolean truncated;

        /** 数据库侧执行耗时（毫秒）。 */
        private long dbCostMs;
    }

    /**
     * EXPLAIN 查询的执行结果。
     */
    @lombok.Data
    @lombok.Builder
    class ExplainResult {
        /** 原始执行计划文本，格式因数据库方言而异（MySQL 为单行摘要字符串）。 */
        private String plan;

        /** 本次查询使用的索引名列表；未使用索引时为空列表。 */
        private List<String> indexesUsed;

        /** 优化器预估的扫描行数；数据库不支持时为 null。 */
        private Long estimatedRows;

        /** EXPLAIN 语句本身的数据库侧耗时（毫秒）。 */
        private long dbCostMs;
    }
}


