package com.mcp.infrastructure.datasource;

import com.mcp.adapter.mysql.MySqlPermissionErrors;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.DatasourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源生命周期管理器，负责启动初始化、运行时查找、以及关闭时资源释放。
 *
 * <p>功能：
 * <ul>
 *   <li>启动时（{@link #init}）按配置顺序依次创建 HikariCP 连接池，并对 MySQL 数据源执行权限探测。</li>
 *   <li>权限探测（{@link #probePermissions}）：用 {@code information_schema} 查询验证账号是否具有基础读取权限，
 *       权限不足时打印 WARN + GRANT 修复建议，<b>不中断启动</b>，允许服务在受限环境下降级运行。</li>
 *   <li>运行时通过 {@link #getDataSource} 按名称（大小写不敏感）获取 {@link DataSourceWrapper}。</li>
 *   <li>关闭时（{@link #destroy}）依次关闭所有连接池，释放数据库连接。</li>
 * </ul>
 *
 * <p>数据源名称在整个应用中唯一（大小写不敏感），重复时启动抛出 {@link IllegalStateException}。
 * @author ouyanghang
 */
@Component
public class DataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);

    private final AppConfig appConfig;

    /** 以数据源名称（小写）为 key 的有序映射，保持配置文件中的声明顺序。 */
    private final Map<String, DataSourceWrapper> dataSourceMap = new LinkedHashMap<>();

    public DataSourceManager(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /** 依次初始化所有数据源连接池并执行权限探测。 */
    @PostConstruct
    public void init() {
        for (DatasourceConfig config : appConfig.getDatasources()) {
            String key = config.getName().toLowerCase();
            if (dataSourceMap.containsKey(key)) {
                throw new IllegalStateException("Duplicate datasource name: " + config.getName());
            }
            log.info("Initializing datasource [{}] type={} url={}", config.getName(), config.getType(), config.getUrl());
            DataSourceWrapper wrapper = new DataSourceWrapper(config, DataSourceFactory.create(config));
            dataSourceMap.put(key, wrapper);
            log.info("Datasource [{}] initialized, pool size={}", config.getName(),
                    wrapper.getHikariDataSource().getMaximumPoolSize());
            probePermissions(wrapper);
        }
        log.info("Total datasources loaded: {}", dataSourceMap.size());
    }

    /**
     * 启动时权限探测：仅对 MySQL 数据源执行，通过查询 {@code information_schema.TABLES}
     * 验证账号是否具有最基础的元数据读取权限。
     *
     * <p>探测结果：
     * <ul>
     *   <li>成功 → INFO 日志，继续启动。</li>
     *   <li>权限不足 → WARN 日志 + GRANT 修复建议，<b>不中断启动</b>。</li>
     *   <li>其他错误（网络、驱动等）→ WARN 日志，<b>不中断启动</b>。</li>
     * </ul>
     *
     * <p>TDengine 不支持 {@code information_schema}，跳过探测。
     */
    private void probePermissions(DataSourceWrapper wrapper) {
        if (!"mysql".equalsIgnoreCase(wrapper.getConfig().getType())) return;
        try (Connection conn = wrapper.getHikariDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() LIMIT 1")) {
            ps.executeQuery();
            log.info("Datasource [{}] permission probe passed.", wrapper.getName());
        } catch (SQLException e) {
            if (MySqlPermissionErrors.isPermissionDenied(e)) {
                String hint = MySqlPermissionErrors.buildGrantHint(
                        wrapper.getName(),
                        wrapper.getConfig().getUsername(),
                        MySqlPermissionErrors.extractDatabase(wrapper.getConfig().getUrl()));
                log.warn("Datasource [{}] permission probe FAILED (error {}). Fix: {}",
                        wrapper.getName(), e.getErrorCode(), hint);
            } else {
                log.warn("Datasource [{}] permission probe non-permission error: {}",
                        wrapper.getName(), e.getMessage());
            }
        }
    }

    /**
     * 按名称获取数据源包装器（大小写不敏感）。
     *
     * @param name 数据源名称
     * @return 对应的 {@link DataSourceWrapper}
     * @throws DataSourceNotFoundException 数据源不存在时抛出
     */
    public DataSourceWrapper getDataSource(String name) {
        DataSourceWrapper wrapper = dataSourceMap.get(name.toLowerCase());
        if (wrapper == null) {
            throw new DataSourceNotFoundException(name);
        }
        return wrapper;
    }

    /** 返回所有数据源包装器的只读集合，顺序与配置文件中的声明顺序一致。 */
    public Collection<DataSourceWrapper> getAllDataSources() {
        return Collections.unmodifiableCollection(dataSourceMap.values());
    }

    /** 判断指定名称的数据源是否已注册（大小写不敏感）。 */
    public boolean exists(String name) {
        return dataSourceMap.containsKey(name.toLowerCase());
    }

    /** 应用关闭时依次关闭所有 HikariCP 连接池，释放数据库连接。 */
    @PreDestroy
    public void destroy() {
        dataSourceMap.values().forEach(wrapper -> {
            log.info("Closing datasource [{}]", wrapper.getName());
            wrapper.close();
        });
    }
}


