package com.mcp.adapter.tdengine;

import com.mcp.adapter.spi.SqlGuardDialectSupport;

/**
 * TDengine 方言安全规则。
 * TDengine 对 JOIN 和部分聚合语法支持有限，在通用 SqlGuard 之上追加方言级检查。
 * @author ouyanghang
 */
public class TDengineSqlGuardDialect implements SqlGuardDialectSupport {

    @Override
    public String dialectType() {
        return "tdengine";
    }

    @Override
    public String checkDialectRules(String sql) {
        String upper = sql.toUpperCase();
        // TDengine 仅支持同一超表子表间的特定 JOIN，禁止通用 JOIN 以防误用
        if (containsWord(upper, "JOIN")) {
            return "JOIN is not supported in TDengine dialect. Use single-table queries instead.";
        }
        return null;
    }

    private boolean containsWord(String upper, String word) {
        int idx = upper.indexOf(word);
        if (idx < 0) return false;
        // 确认是完整单词边界，避免误判列名（如 JOINDATE）
        boolean beforeOk = idx == 0 || !Character.isLetterOrDigit(upper.charAt(idx - 1));
        boolean afterOk  = idx + word.length() >= upper.length()
                || !Character.isLetterOrDigit(upper.charAt(idx + word.length()));
        return beforeOk && afterOk;
    }
}


