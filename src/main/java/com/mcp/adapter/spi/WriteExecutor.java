package com.mcp.adapter.spi;

import java.util.List;

/**
 * 写入执行 SPI，负责执行 INSERT / UPDATE / DELETE / CREATE TABLE 等 DML/DDL 语句。
 *
 * <p>实现要求：
 * <ul>
 *   <li>支持事务时（MySQL），所有语句在同一个 Connection 内以事务执行，任意失败时回滚并设置 {@code rolledBack=true}。</li>
 *   <li>不支持事务时（TDengine），逐条执行，失败后将剩余语句标记为 SKIPPED，并设置 {@code rolledBack=false}。</li>
 *   <li>实现类需线程安全，不得在实例上保存连接或语句状态。</li>
 * </ul>
 *
 * @see com.mcp.adapter.mysql.MySqlWriteExecutor
 * @see com.mcp.adapter.tdengine.TDengineWriteExecutor
 * @author ouyanghang
 */
public interface WriteExecutor {

    /**
     * 执行一批 DML/DDL 语句。
     *
     * @param conn           已就绪的数据库连接，生命周期由调用方管理
     * @param statements     经过安全检查、注释清洗后的 SQL 语句列表，每项对应一条完整语句
     * @param timeoutSeconds 单条语句的执行超时（秒），传递给 {@code Statement.setQueryTimeout}
     * @return 执行结果，包含各语句明细、总影响行数、是否已回滚
     * @throws Exception 非权限类错误向上传播；权限错误须封装为 {@link com.mcp.infrastructure.datasource.DatasourcePermissionException}
     */
    WriteResult executeWrite(java.sql.Connection conn, List<String> statements, int timeoutSeconds) throws Exception;

    /**
     * 写入执行的聚合结果。
     */
    @lombok.Data
    @lombok.Builder
    class WriteResult {
        /** 本次执行所有成功语句的累计影响行数。 */
        private int totalAffectedRows;

        /** 每条语句的执行明细，顺序与入参 statements 一致。 */
        private List<StatementResult> details;

        /** 整批语句的数据库侧总耗时（毫秒）。 */
        private long dbCostMs;

        /**
         * 事务是否已回滚。
         * <ul>
         *   <li>{@code true}：支持事务的数据库（MySQL）在失败时完整回滚，数据无残留。</li>
         *   <li>{@code false}：不支持跨语句事务的数据库（TDengine），失败前已执行的语句不可撤销。</li>
         * </ul>
         * 默认值 {@code true} 兼容旧 executor，新的无事务实现须在 build 时显式设置为 {@code false}。
         */
        @lombok.Builder.Default
        private boolean rolledBack = true;

        /**
         * 单条语句的执行结果。
         */
        @lombok.Data
        @lombok.Builder
        public static class StatementResult {
            /** 语句在入参列表中的下标（0-based）。 */
            private int statementIndex;

            /** 语句类型：INSERT、UPDATE、DELETE、CREATE、OTHER。 */
            private String statementType;

            /** 本条语句影响的行数；失败或跳过时为 0。 */
            private int affectedRows;

            /** 执行状态：SUCCESS / FAILED / SKIPPED（前序失败导致跳过）。 */
            private String status;

            /** 失败原因或跳过说明；成功时为 null。 */
            private String message;

            /** 本条语句的数据库侧耗时（毫秒）。 */
            private long dbCostMs;
        }
    }
}


