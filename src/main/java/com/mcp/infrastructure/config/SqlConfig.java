package com.mcp.infrastructure.config;

import lombok.Data;

/**
 * SQL 执行的安全与性能参数，对应配置文件中的 {@code sql} 节点。
 * @author ouyanghang
 */
@Data
public class SqlConfig {

    /** SELECT 查询最大返回行数；超出时截断并在响应中标记 {@code truncated=true}，默认 500。 */
    private int maxRows = 500;

    /** 单条 SQL 执行超时（秒），通过 {@code Statement.setQueryTimeout} 传递给驱动，默认 5s。 */
    private int timeoutSeconds = 5;

    /** SQL 字符串的最大允许长度（字符数），超出时在安全链中拒绝，防止超大 SQL 导致解析器 OOM，默认 10000。 */
    private int maxLength = 10000;

    /**
     * 批量 INSERT 的最大允许行数（Values 组数），超出时在安全链中拒绝并提示拆分，默认 1000。
     * 该限制在文本层面通过括号计数实现，不依赖 JSqlParser 内部 API。
     */
    private int batchInsertMaxRows = 1000;
}


