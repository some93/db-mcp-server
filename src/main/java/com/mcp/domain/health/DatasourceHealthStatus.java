package com.mcp.domain.health;

import lombok.Data;

/**
 * 单个数据源的健康状态快照，由 {@link HealthService} 维护。
 *
 * <p>状态值枚举：
 * <ul>
 *   <li>{@code UP} — 最近一次探测成功，连接可用。</li>
 *   <li>{@code DOWN} — 探测失败，连接不可用，{@link #message} 包含失败原因。</li>
 *   <li>{@code UNKNOWN} — 启动后尚未完成首次探测。</li>
 * </ul>
 * @author ouyanghang
 */
@Data
public class DatasourceHealthStatus {
    /** 数据源名称，与配置文件中 {@code datasources[].name} 一致。 */
    private String datasourceName;

    /** 健康状态：UP / DOWN / UNKNOWN。 */
    private String status;

    /** 失败原因；状态为 UP 或 UNKNOWN 时为 null。 */
    private String message;

    /** 最后一次探测完成的时间戳（epoch 毫秒）。 */
    private long lastCheckedAtMs;

    /** 创建一个 UP 状态快照，{@code lastCheckedAtMs} 设置为当前时间。 */
    public static DatasourceHealthStatus up(String name) {
        DatasourceHealthStatus s = new DatasourceHealthStatus();
        s.datasourceName = name;
        s.status = "UP";
        s.lastCheckedAtMs = System.currentTimeMillis();
        return s;
    }

    /** 创建一个 DOWN 状态快照，{@code lastCheckedAtMs} 设置为当前时间。 */
    public static DatasourceHealthStatus down(String name, String message) {
        DatasourceHealthStatus s = new DatasourceHealthStatus();
        s.datasourceName = name;
        s.status = "DOWN";
        s.message = message;
        s.lastCheckedAtMs = System.currentTimeMillis();
        return s;
    }
}


