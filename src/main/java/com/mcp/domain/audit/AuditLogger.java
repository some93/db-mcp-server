package com.mcp.domain.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志写入工具。使用独立 logger "audit"，可通过 logback appender 路由到独立文件或 ELK。
 * 所有异常内部吞掉，绝不传播到主链路。
 * @author ouyanghang
 */
public class AuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneId.systemDefault());

    private AuditLogger() {}

    public static void log(String tool, String datasource, String sql,
                           boolean success, long durationMs, String errorCode, String errorMsg) {
        try {
            String ts = FMT.format(Instant.now());
            // 手工拼 JSON，避免引入 Jackson 依赖（Jackson 已有但保持 domain 层轻量）
            StringBuilder sb = new StringBuilder(256);
            sb.append("{");
            appendStr(sb, "timestamp", ts);          sb.append(",");
            appendStr(sb, "tool", tool);             sb.append(",");
            appendStr(sb, "datasource", datasource); sb.append(",");
            appendStr(sb, "sql", abbreviate(sql));   sb.append(",");
            sb.append("\"success\":").append(success).append(",");
            sb.append("\"durationMs\":").append(durationMs).append(",");
            appendStr(sb, "errorCode", errorCode);   sb.append(",");
            appendStr(sb, "errorMsg", errorMsg);
            sb.append("}");
            AUDIT.info(sb.toString());
        } catch (Exception ignored) {
            // 审计失败不影响主链路
        }
    }

    /** 截断超长 SQL，避免日志行过大 */
    private static String abbreviate(String sql) {
        if (sql == null) return null;
        return sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
    }

    private static void appendStr(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            // 转义双引号和反斜杠
            sb.append("\"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
    }
}


