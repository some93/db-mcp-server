package com.mcp.infrastructure.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用全局配置根节点，对应 YAML 配置文件的顶层结构，由 {@link ConfigLoader} 加载并注册为 Spring Bean。
 *
 * <p>所有子配置对象均有合理默认值，最小化配置时只需填写 {@link #datasources}。
 * @author ouyanghang
 */
@Data
public class AppConfig {

    /** 数据源列表，至少需要配置一个；名称不得重复（大小写不敏感）。 */
    private List<DatasourceConfig> datasources = new ArrayList<>();

    /** SQL 安全与执行参数（行数上限、超时、长度限制等）。 */
    private SqlConfig sql = new SqlConfig();

    /** 元数据缓存参数（预热开关、刷新间隔）。 */
    private CacheConfig cache = new CacheConfig();

    /** MCP 传输层行为参数（stdio 断开处理等）。 */
    private McpConfig mcp = new McpConfig();

    /** HTTP 调试服务参数（启用开关、监听地址/端口）。 */
    private HttpConfig http = new HttpConfig();

    /** 数据源健康检测参数（检测间隔）。 */
    private HealthConfig health = new HealthConfig();

    /** JVM 时区，用于日期类型格式化输出，默认 Asia/Shanghai。 */
    private String timezone = "Asia/Shanghai";
}


