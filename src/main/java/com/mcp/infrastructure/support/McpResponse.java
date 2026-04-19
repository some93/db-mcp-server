package com.mcp.infrastructure.support;

import lombok.Data;

/**
 * MCP 工具调用和 HTTP 调试接口的统一响应结构。
 *
 * <p>所有 Service 层操作通过此结构向上返回结果，McpStdioServer 和 HTTP Controller 均直接序列化此对象。
 *
 * <p>成功响应：{@code success=true}，{@code code="SUCCESS"}，{@code data} 包含业务数据。
 * 失败响应：{@code success=false}，{@code code} 为语义错误码，{@code message} 为人类可读原因，{@code data=null}。
 * @author ouyanghang
 */
@Data
public class McpResponse {

    /** 操作是否成功；false 时 AI 客户端应读取 message 了解失败原因。 */
    private boolean success;

    /**
     * 语义错误码；成功时为 {@code SUCCESS}，失败时为具体错误码（如 {@code SQL_BLOCK_NO_WHERE}）。
     * AI 客户端可根据此字段给出针对性建议或自动重试。
     */
    private String code;

    /** 人类可读的结果描述；失败时为拦截原因或错误消息。 */
    private String message;

    /** 业务数据负载；失败时为 null。数据类型因接口而异，通常为 {@link java.util.Map} 或列表。 */
    private Object data;

    /** 从调用开始到返回的总耗时（毫秒），包含安全检查、连接获取和数据库执行的全链路时间。 */
    private long costMs;

    /** 创建成功响应（无消息文本）。 */
    public static McpResponse ok(Object data, long costMs) {
        McpResponse r = new McpResponse();
        r.success = true;
        r.code = "SUCCESS";
        r.message = "OK";
        r.data = data;
        r.costMs = costMs;
        return r;
    }

    /** 创建带自定义消息的成功响应。 */
    public static McpResponse ok(String message, Object data, long costMs) {
        McpResponse r = new McpResponse();
        r.success = true;
        r.code = "SUCCESS";
        r.message = message;
        r.data = data;
        r.costMs = costMs;
        return r;
    }

    /** 创建失败响应。 */
    public static McpResponse error(String code, String message, long costMs) {
        McpResponse r = new McpResponse();
        r.success = false;
        r.code = code;
        r.message = message;
        r.data = null;
        r.costMs = costMs;
        return r;
    }

    /** 创建部分成功响应（如刷新缓存时部分数据源失败）。 */
    public static McpResponse partialSuccess(String message, long costMs) {
        McpResponse r = new McpResponse();
        r.success = true;
        r.code = "PARTIAL_SUCCESS";
        r.message = message;
        r.data = null;
        r.costMs = costMs;
        return r;
    }
}


