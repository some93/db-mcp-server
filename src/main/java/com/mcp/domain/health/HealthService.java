package com.mcp.domain.health;

import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.DatasourceConfig;
import com.mcp.infrastructure.datasource.DataSourceManager;
import com.mcp.infrastructure.datasource.DataSourceWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源健康状态管理服务，负责定时探测和调用前快速校验。
 *
 * <p>工作模式：
 * <ol>
 *   <li><b>定时探测</b>（{@link #scheduledCheck}）：按配置间隔轮询所有数据源，
 *       使用 {@link Connection#isValid} 做轻量存活检查，结果写入 {@link #statusMap}。</li>
 *   <li><b>调用前快速校验</b>（{@link #quickCheck}）：在 Service 层执行 SQL 之前调用。
 *       若当前状态为 UP 则直接放行（零额外 I/O）；若非 UP 则立即做一次实时探测作为最终确认，
 *       探测仍失败时抛出 {@link DatasourceUnavailableException}，阻止后续 SQL 执行。</li>
 * </ol>
 *
 * <p>状态存储使用 {@link ConcurrentHashMap}，key 统一小写，多线程读写安全。
 * @author ouyanghang
 */
@Component
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final AppConfig appConfig;
    private final DataSourceManager dataSourceManager;

    /** 以数据源名称（小写）为 key 的健康状态映射，线程安全。 */
    private final Map<String, DatasourceHealthStatus> statusMap = new ConcurrentHashMap<>();

    public HealthService(AppConfig appConfig, DataSourceManager dataSourceManager) {
        this.appConfig = appConfig;
        this.dataSourceManager = dataSourceManager;
    }

    /** 启动时将所有数据源状态初始化为 UNKNOWN，等待首次定时探测。 */
    @PostConstruct
    public void init() {
        for (DatasourceConfig ds : appConfig.getDatasources()) {
            DatasourceHealthStatus s = new DatasourceHealthStatus();
            s.setDatasourceName(ds.getName());
            s.setStatus("UNKNOWN");
            s.setLastCheckedAtMs(System.currentTimeMillis());
            statusMap.put(ds.getName().toLowerCase(), s);
        }
    }

    /** 按配置的 {@code health.checkIntervalSeconds} 间隔定时探测所有数据源。 */
    @Scheduled(fixedDelayString = "#{@appConfig.health.checkIntervalSeconds * 1000}")
    public void scheduledCheck() {
        for (DatasourceConfig ds : appConfig.getDatasources()) {
            probe(ds.getName());
        }
    }

    /**
     * 执行 SQL 前的快速健康校验。
     *
     * <p>已知 UP 则直接放行（零额外开销）；非 UP 则立即发起一次实时探测作为最终确认，
     * 避免定时探测间隙内的误拦截，同时确保数据库真正宕机时不盲目放行。
     *
     * @param datasourceName 目标数据源名称
     * @throws DatasourceUnavailableException 实时探测失败时抛出
     */
    public void quickCheck(String datasourceName) {
        DatasourceHealthStatus current = getStatus(datasourceName);
        if ("UP".equals(current.getStatus())) return;

        // 非 UP 状态做一次最终确认，防止定时检查结果过期导致误拦截
        boolean ok = probe(datasourceName);
        if (!ok) {
            throw new DatasourceUnavailableException(datasourceName,
                    getStatus(datasourceName).getMessage());
        }
    }

    /**
     * 获取指定数据源的最新健康状态快照。
     *
     * @param datasourceName 数据源名称（大小写不敏感）
     * @return 状态快照；数据源不存在时返回 UNKNOWN 状态
     */
    public DatasourceHealthStatus getStatus(String datasourceName) {
        DatasourceHealthStatus s = statusMap.get(datasourceName.toLowerCase());
        if (s == null) {
            DatasourceHealthStatus unknown = new DatasourceHealthStatus();
            unknown.setDatasourceName(datasourceName);
            unknown.setStatus("UNKNOWN");
            return unknown;
        }
        return s;
    }

    /** 返回所有数据源的健康状态集合（只读视图）。 */
    public Collection<DatasourceHealthStatus> getAllStatuses() {
        return Collections.unmodifiableCollection(statusMap.values());
    }

    /** 返回是否所有数据源均处于 UP 状态（用于 /ready 接口）。 */
    public boolean allUp() {
        return statusMap.values().stream().allMatch(s -> "UP".equals(s.getStatus()));
    }

    /**
     * 主动探测数据源连通性并更新 statusMap。
     *
     * <p>使用 {@link Connection#isValid(int)} 而非发送业务 SQL，超时 3 秒，
     * 既能检测连接存活，又不产生无意义的查询日志。
     * JDBC 规范规定：{@code isValid} 返回 {@code false} 是合法的失败信号，必须检查返回值。
     *
     * @param datasourceName 数据源名称
     * @return {@code true} 探测成功；{@code false} 探测失败（statusMap 已更新）
     */
    private boolean probe(String datasourceName) {
        try {
            DataSourceWrapper wrapper = dataSourceManager.getDataSource(datasourceName);
            try (Connection conn = wrapper.getHikariDataSource().getConnection()) {
                if (!conn.isValid(3)) {
                    log.warn("Health probe failed for [{}]: isValid returned false", datasourceName);
                    statusMap.put(datasourceName.toLowerCase(),
                            DatasourceHealthStatus.down(datasourceName, "Connection isValid() returned false"));
                    return false;
                }
            }
            statusMap.put(datasourceName.toLowerCase(), DatasourceHealthStatus.up(datasourceName));
            return true;
        } catch (Exception e) {
            log.warn("Health probe failed for [{}]: {}", datasourceName, e.getMessage());
            statusMap.put(datasourceName.toLowerCase(),
                    DatasourceHealthStatus.down(datasourceName, e.getMessage()));
            return false;
        }
    }
}


