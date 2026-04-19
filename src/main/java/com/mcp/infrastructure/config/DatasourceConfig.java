package com.mcp.infrastructure.config;

import lombok.Data;

/**
 * 单个数据源的连接与连接池配置，对应 {@link AppConfig#getDatasources()} 列表中的一个元素。
 * @author ouyanghang
 */
@Data
public class DatasourceConfig {

    /** 数据源唯一标识名，在 MCP 工具调用时作为 {@code datasourceName} 参数引用，大小写不敏感。 */
    private String name;

    /** 数据库类型：{@code mysql} 或 {@code tdengine}（大小写不敏感），决定使用哪个适配器实现。 */
    private String type;

    /** JDBC 连接 URL，例如 {@code jdbc:mysql://host:3306/dbname?...}。 */
    private String url;

    /** 数据库登录用户名，启动时用于权限探测和运行时 GRANT hint 生成。 */
    private String username;

    /** 数据库登录密码。 */
    private String password;

    /** HikariCP 连接池参数，若不配置则使用内嵌的默认值。 */
    private PoolConfig pool = new PoolConfig();

    /**
     * HikariCP 连接池细粒度参数。
     * 各字段的含义与 HikariCP 官方文档一致，此处只记录与默认值不同之处。
     */
    @Data
    public static class PoolConfig {
        /** 连接池最小空闲连接数，默认 2；减小可降低空闲资源消耗。 */
        private int minimumIdle = 2;

        /** 连接池最大连接数，默认 10；应根据数据库 max_connections 和并发量调整。 */
        private int maximumPoolSize = 10;

        /** 获取连接的超时时间（毫秒），超时抛出 SQLTimeoutException，默认 30s。 */
        private long connectionTimeoutMs = 30000;

        /** 连接在池中的最大空闲时间（毫秒），超时被驱逐，默认 10min。 */
        private long idleTimeoutMs = 600000;

        /** 连接的最大生命周期（毫秒），到期强制替换，默认 30min，应小于数据库的 wait_timeout。 */
        private long maxLifetimeMs = 1800000;
    }
}


