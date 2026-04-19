package com.mcp.adapter.spi;

/**
 * SQL 安全检查的方言扩展点 SPI，允许各数据库适配器在通用安全规则之外追加自己的禁止逻辑。
 *
 * <p>通用规则（注释清洗、关键字拦截、无 WHERE 检测、INSERT...SELECT 禁止）
 * 由 {@link com.mcp.domain.security.SqlGuard} 统一处理；
 * 本接口只负责各方言的差异化规则，例如 TDengine 禁止 JOIN。
 *
 * @see com.mcp.adapter.mysql.MySqlSqlGuardDialect
 * @see com.mcp.adapter.tdengine.TDengineSqlGuardDialect
 * @author ouyanghang
 */
public interface SqlGuardDialectSupport {

    /**
     * 返回该适配器支持的方言标识，与 {@link DatasourceAdapter#dialectType()} 保持一致。
     * 例如：{@code "mysql"}、{@code "tdengine"}。
     */
    String dialectType();

    /**
     * 执行方言级额外禁止规则检查。
     *
     * @param sql 原始 SQL（已完成注释清洗）
     * @return 被拦截时返回拦截原因描述字符串；允许执行时返回 {@code null}
     */
    String checkDialectRules(String sql);
}


