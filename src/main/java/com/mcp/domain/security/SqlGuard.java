package com.mcp.domain.security;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 全局 SQL 安全守卫，提供独立于数据库方言的通用安全拦截能力。
 *
 * <p>在每条 SQL 到达数据库驱动之前，依次执行以下检查：
 * <ol>
 *   <li><b>长度检查</b>：超过配置上限时拒绝，防止超大 SQL 造成解析器 OOM。</li>
 *   <li><b>注释清洗</b>（{@link #stripComments}）：去除单行注释（{@code --}）和块注释（{@code /* … *}{@code /}），
 *       防止通过注释绕过关键字检测。清洗结果<b>只用于检测</b>，不替换实际执行 SQL。</li>
 *   <li><b>关键字黑名单</b>：基于字符串包含的快速门卫，拦截 DROP / TRUNCATE / ALTER 等危险操作。</li>
 *   <li><b>语法级分类</b>：通过 JSqlParser 解析 AST，确认语句类型符合调用场景（仅 SELECT / 仅 DML 等）。</li>
 *   <li><b>语义安全规则</b>：
 *     <ul>
 *       <li>UPDATE / DELETE 必须有 WHERE 条件。</li>
 *       <li>WHERE 条件不得为伪过滤（{@code WHERE 1=1} / {@code WHERE TRUE} / {@code WHERE col IS NOT NULL}）。</li>
 *       <li>禁止 INSERT ... SELECT（数据批量复制）。</li>
 *       <li>批量 INSERT VALUES 行数不超过配置上限。</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>方言差异由 {@link com.mcp.adapter.spi.SqlGuardDialectSupport} 扩展，本类只处理通用规则。
 *
 * <p>所有公开方法均为静态、无状态，线程安全。
 * @author ouyanghang
 */
public class SqlGuard {

    /**
     * 禁止通过 MCP 执行的危险 DDL/权限管理关键字。
     * 文本级快速匹配，后续由 JSqlParser 语法解析进行精确分类，两道防线互补。
     */
    private static final Set<String> BLOCKED_KEYWORDS = new HashSet<>(Arrays.asList(
            "DROP", "TRUNCATE", "ALTER", "GRANT", "REVOKE",
            "LOCK TABLES", "UNLOCK TABLES", "FLUSH", "SET GLOBAL", "SHUTDOWN"
    ));

    private static final Pattern COMMENT_LINE = Pattern.compile("--[^\r\n]*", Pattern.MULTILINE);
    private static final Pattern COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern CREATE_AS_SELECT = Pattern.compile(
            "\\bAS\\s*(?:\\(\\s*)?SELECT\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PSEUDO_WHERE_TRUE = Pattern.compile(
            "\\bWHERE\\s+(1\\s*=\\s*1|TRUE|1\\s*=\\s*'1')\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 清洗 SQL 中的单行注释（{@code --}）和块注释（{@code /* … *}{@code /}），返回净化后的文本。
     *
     * <p><b>重要：</b>净化结果只用于安全检测和 AST 解析，不得替换实际执行的 SQL。
     * 若将净化后的 SQL 发送给数据库，可能破坏字符串字面量中与注释格式相同的合法内容。
     *
     * @param sql 原始 SQL
     * @return 去掉注释并 trim 后的文本
     */
    public static String stripComments(String sql) {
        String s = COMMENT_BLOCK.matcher(sql).replaceAll(" ");
        s = COMMENT_LINE.matcher(s).replaceAll(" ");
        return s.trim();
    }

    /**
     * 对 SELECT 查询场景进行安全校验：仅允许单条 SELECT 语句。
     *
     * @param sql       原始 SQL
     * @param maxLength 最大允许 SQL 长度（字符数），来自配置
     * @throws SqlBlockedException 违反任意规则时抛出，携带语义错误码
     */
    public static void checkQuery(String sql, int maxLength) {
        checkLength(sql, maxLength);
        String clean = stripComments(sql);
        checkBlockedKeywords(clean);
        Statement stmt = parse(clean);
        if (!(stmt instanceof Select)) {
            throw new SqlBlockedException("SQL_BLOCK_TYPE",
                    "executeQuery only allows a single SELECT statement.");
        }
    }

    /**
     * 对单条写入语句（INSERT / UPDATE / DELETE）进行安全校验。
     *
     * <p>调用方（{@code ExecuteWriteService}）负责按分号将多语句拆分后逐条传入，
     * 从而使每条语句都能被 JSqlParser 独立解析，避免多语句解析的兼容性问题。
     *
     * @param sql               单条 DML 语句
     * @param maxLength         最大允许 SQL 长度
     * @param batchInsertMaxRows 批量 INSERT 的最大行数上限
     * @throws SqlBlockedException 违反任意规则时抛出，携带语义错误码
     */
    public static void checkWrite(String sql, int maxLength, int batchInsertMaxRows) {
        checkLength(sql, maxLength);
        String clean = stripComments(sql);
        checkBlockedKeywords(clean);
        Statement stmt = parse(clean);
        if (stmt instanceof Insert) {
            checkInsert((Insert) stmt);
            // 批量 INSERT 行数上限检查（文本级，不依赖 JSqlParser 内部 API）
            int rowCount = countBatchInsertRows(clean);
            if (rowCount > batchInsertMaxRows) {
                throw new SqlBlockedException("SQL_BLOCK_BATCH_TOO_LARGE",
                        "Batch INSERT has " + rowCount + " rows, exceeds limit " + batchInsertMaxRows
                        + ". Please split into smaller batches of <= " + batchInsertMaxRows + " rows.");
            }
        } else if (stmt instanceof Update) {
            checkUpdate((Update) stmt, clean);
        } else if (stmt instanceof Delete) {
            checkDelete((Delete) stmt, clean);
        } else {
            throw new SqlBlockedException("SQL_BLOCK_TYPE",
                    "executeWrite only allows INSERT, UPDATE, DELETE statements.");
        }
    }

    /**
     * 对 CREATE TABLE 语句进行安全校验：仅允许单条 CREATE TABLE，禁止 CREATE TABLE ... AS SELECT。
     *
     * @param sql       CREATE TABLE 语句
     * @param maxLength 最大允许 SQL 长度
     * @throws SqlBlockedException 违反任意规则时抛出
     */
    public static void checkCreateTable(String sql, int maxLength) {
        checkLength(sql, maxLength);
        String clean = stripComments(sql);
        checkBlockedKeywords(clean);
        Statement stmt = parse(clean);
        if (!(stmt instanceof net.sf.jsqlparser.statement.create.table.CreateTable)) {
            throw new SqlBlockedException("SQL_BLOCK_TYPE",
                    "createTable only allows a single CREATE TABLE statement.");
        }
        // 禁止 CREATE TABLE ... AS SELECT
        if (CREATE_AS_SELECT.matcher(clean).find()) {
            throw new SqlBlockedException("SQL_BLOCK_CREATE_AS_SELECT",
                    "CREATE TABLE ... AS SELECT is not allowed.");
        }
    }

    /**
     * 对 EXPLAIN 分析场景进行安全校验：仅允许单条 SELECT 语句。
     *
     * @param sql       待分析的 SELECT 语句
     * @param maxLength 最大允许 SQL 长度
     * @throws SqlBlockedException 违反规则时抛出
     */
    public static void checkExplain(String sql, int maxLength) {
        checkLength(sql, maxLength);
        String clean = stripComments(sql);
        Statement stmt = parse(clean);
        if (!(stmt instanceof Select)) {
            throw new SqlBlockedException("SQL_BLOCK_TYPE",
                    "explainQuery only allows a single SELECT statement.");
        }
    }

    // ---- private helpers ----

    private static void checkLength(String sql, int maxLength) {
        if (sql == null || sql.isBlank()) {
            throw new SqlBlockedException("PARAM_SQL_EMPTY", "SQL must not be empty.");
        }
        if (sql.length() > maxLength) {
            throw new SqlBlockedException("PARAM_SQL_TOO_LONG",
                    "SQL length " + sql.length() + " exceeds limit " + maxLength + ".");
        }
    }

    private static void checkBlockedKeywords(String sql) {
        String upper = sql.toUpperCase();
        for (String kw : BLOCKED_KEYWORDS) {
            // 字符串包含检测作为第一道快速门卫，拦截明显违规。
            // 精确的语法级分类由后续 JSqlParser 解析保证，两道防线互补。
            if (upper.contains(kw)) {
                throw new SqlBlockedException("SQL_BLOCK_KEYWORD",
                        "SQL contains forbidden keyword: " + kw);
            }
        }
    }

    private static Statement parse(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            throw new SqlBlockedException("SQL_BLOCK_PARSE",
                    "SQL parse failed: " + e.getMessage());
        }
    }

    private static void checkInsert(Insert insert) {
        // JSqlParser 4.7 对多行 INSERT VALUES (r1),(r2),... 会在内部用 PlainSelect 包装，
        // 导致 getSelect() != null。真正的 INSERT...SELECT 特征是 PlainSelect 带有 FromItem。
        if (insert.getSelect() != null) {
            boolean isInsertSelect = false;
            if (insert.getSelect().getSelectBody() instanceof PlainSelect) {
                PlainSelect ps = (PlainSelect) insert.getSelect().getSelectBody();
                isInsertSelect = (ps.getFromItem() != null);
            }
            if (isInsertSelect) {
                throw new SqlBlockedException("SQL_BLOCK_INSERT_SELECT",
                        "INSERT ... SELECT is not allowed.");
            }
        }
    }

    /**
     * 检查批量 INSERT 的 VALUES 行数，通过 SQL 文本解析计算，不依赖 JSqlParser 内部 API。
     * 原理：批量 INSERT 格式为 VALUES (row1), (row2), ... 统计顶层括号组数即行数。
     */
    static int countBatchInsertRows(String cleanSql) {
        // 找到 VALUES 关键字后的部分
        int valuesIdx = cleanSql.toUpperCase().indexOf("VALUES");
        if (valuesIdx < 0) return 1;
        String afterValues = cleanSql.substring(valuesIdx + 6).trim();

        // 统计顶层括号组数（每个 (…) 是一行）
        int rows = 0;
        int depth = 0;
        for (char ch : afterValues.toCharArray()) {
            if (ch == '(') {
                if (depth == 0) rows++;
                depth++;
            } else if (ch == ')') {
                depth--;
            }
        }
        return Math.max(rows, 1);
    }

    private static void checkUpdate(Update update, String cleanSql) {
        if (update.getWhere() == null) {
            throw new SqlBlockedException("SQL_BLOCK_NO_WHERE",
                    "UPDATE must have a WHERE condition.");
        }
        checkPseudoWhere(update.getWhere(), cleanSql);
    }

    private static void checkDelete(Delete delete, String cleanSql) {
        if (delete.getWhere() == null) {
            throw new SqlBlockedException("SQL_BLOCK_NO_WHERE",
                    "DELETE must have a WHERE condition.");
        }
        checkPseudoWhere(delete.getWhere(), cleanSql);
    }

    private static void checkPseudoWhere(Expression where, String cleanSql) {
        // 正则覆盖最常见的伪过滤：WHERE 1=1 / WHERE TRUE / WHERE 1='1'。
        // 用净化后的 SQL 做正则匹配，防止注释干扰判断。
        if (PSEUDO_WHERE_TRUE.matcher(cleanSql).find()) {
            throw new SqlBlockedException("SQL_BLOCK_PSEUDO_WHERE",
                    "Pseudo-filter condition detected (e.g. WHERE 1=1, WHERE TRUE). This is unsafe.");
        }
        // WHERE id IS NOT NULL 单独处理：IS NOT NULL 作为唯一过滤条件时，
        // 在实际数据中几乎等价于无过滤，可静态判定为高风险。
        if (where instanceof IsNullExpression) {
            IsNullExpression isNull = (IsNullExpression) where;
            if (isNull.isNot()) {
                throw new SqlBlockedException("SQL_BLOCK_PSEUDO_WHERE",
                        "WHERE <col> IS NOT NULL is considered a pseudo-filter and is not allowed.");
            }
        }
    }
}


