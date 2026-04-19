package com.mcp.infrastructure.datasource;

/**
 * 数据源权限不足异常，当数据库操作因账号权限不足而失败时由适配器层抛出。
 *
 * <p>携带以下信息供上层服务层处理：
 * <ul>
 *   <li>{@link #datasourceName} — 发生权限错误的数据源名称。</li>
 *   <li>{@link #grantHint} — 可直接执行的 GRANT SQL 修复建议，格式如：
 *       {@code GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON db.* TO 'user'@'%'; FLUSH PRIVILEGES;}</li>
 *   <li>{@link #getCode()} — 固定返回 {@code DATASOURCE_PERMISSION_DENIED}，
 *       用于 MCP 响应的 {@code code} 字段，使 AI 客户端能区分权限错误与其他 SQL 执行错误。</li>
 * </ul>
 *
 * <p>触发场景（MySQL 错误码）：
 * <ul>
 *   <li>1044 — {@code Access denied for user to database}</li>
 *   <li>1045 — {@code Access denied for user (using password)}</li>
 *   <li>1142 — {@code command denied to user for table}</li>
 *   <li>1143 — {@code column command denied}</li>
 *   <li>SQLState 28000 — 通用认证失败</li>
 * </ul>
 *
 * @see com.mcp.adapter.mysql.MySqlPermissionErrors
 * @author ouyanghang
 */
public class DatasourcePermissionException extends RuntimeException {

    /** 发生权限错误的数据源名称。 */
    private final String datasourceName;

    /** 可执行的 GRANT SQL 修复建议，由 {@link com.mcp.adapter.mysql.MySqlPermissionErrors#buildGrantHint} 生成。 */
    private final String grantHint;

    /**
     * @param datasourceName 数据源名称
     * @param message        原始错误消息（来自 {@link java.sql.SQLException#getMessage()}）
     * @param grantHint      GRANT 修复建议 SQL
     */
    public DatasourcePermissionException(String datasourceName, String message, String grantHint) {
        super(message);
        this.datasourceName = datasourceName;
        this.grantHint = grantHint;
    }

    public String getDatasourceName() { return datasourceName; }

    public String getGrantHint() { return grantHint; }

    /** 固定返回 {@code DATASOURCE_PERMISSION_DENIED}，映射到 MCP 响应的错误码字段。 */
    public String getCode() { return "DATASOURCE_PERMISSION_DENIED"; }
}


