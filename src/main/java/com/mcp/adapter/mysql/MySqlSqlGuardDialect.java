package com.mcp.adapter.mysql;

import com.mcp.adapter.spi.SqlGuardDialectSupport;

/**
 * @author ouyanghang
 */
public class MySqlSqlGuardDialect implements SqlGuardDialectSupport {

    @Override
    public String dialectType() {
        return "mysql";
    }

    @Override
    public String checkDialectRules(String sql) {
        // MySQL 无额外方言级禁止规则（TDengine 才有 JOIN 禁止）
        return null;
    }
}

