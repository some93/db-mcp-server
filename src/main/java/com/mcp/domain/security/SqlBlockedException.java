package com.mcp.domain.security;

/**
 * SQL 安全拦截异常，由 {@link SqlGuard} 在检测到违规时抛出。
 *
 * <p>携带语义错误码（{@link #getCode()}），上层服务直接将其映射到 MCP 响应的 {@code code} 字段，
 * 使 AI 客户端能区分不同的拦截原因并给出针对性提示。
 *
 * <p>常见错误码：
 * <ul>
 *   <li>{@code PARAM_SQL_EMPTY} — SQL 为空</li>
 *   <li>{@code PARAM_SQL_TOO_LONG} — SQL 超过长度限制</li>
 *   <li>{@code SQL_BLOCK_KEYWORD} — 包含禁止关键字（DROP、ALTER 等）</li>
 *   <li>{@code SQL_BLOCK_TYPE} — 语句类型与调用场景不符</li>
 *   <li>{@code SQL_BLOCK_NO_WHERE} — UPDATE/DELETE 缺少 WHERE 条件</li>
 *   <li>{@code SQL_BLOCK_PSEUDO_WHERE} — WHERE 条件为伪过滤（1=1、TRUE 等）</li>
 *   <li>{@code SQL_BLOCK_INSERT_SELECT} — 禁止 INSERT ... SELECT</li>
 *   <li>{@code SQL_BLOCK_BATCH_TOO_LARGE} — 批量 INSERT 行数超限</li>
 *   <li>{@code SQL_BLOCK_CREATE_AS_SELECT} — 禁止 CREATE TABLE ... AS SELECT</li>
 *   <li>{@code SQL_BLOCK_PARSE} — SQL 解析失败（语法错误）</li>
 * </ul>
 * @author ouyanghang
 */
public class SqlBlockedException extends RuntimeException {

    /** 语义错误码，直接映射到 MCP 响应的 {@code code} 字段。 */
    private final String code;

    /**
     * @param code    语义错误码，见类文档中的常见错误码列表
     * @param message 人类可读的拦截原因，会透传给 AI 客户端
     */
    public SqlBlockedException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** 返回语义错误码。 */
    public String getCode() {
        return code;
    }
}


