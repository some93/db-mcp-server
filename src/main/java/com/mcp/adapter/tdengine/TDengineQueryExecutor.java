package com.mcp.adapter.tdengine;

import com.mcp.adapter.spi.QueryExecutor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TDengine 查询执行器。
 * setQueryTimeout 在 TDengine RESTful 驱动中可能不支持，降级处理。
 * EXPLAIN 输出格式为文本，直接拼接返回。
 * @author ouyanghang
 */
public class TDengineQueryExecutor implements QueryExecutor {

    @Override
    public QueryResult executeQuery(Connection conn, String sql, int maxRows, int timeoutSeconds) throws Exception {
        long start = System.currentTimeMillis();

        try (Statement st = conn.createStatement()) {
            // TDengine RESTful 驱动 setQueryTimeout 可能抛 SQLFeatureNotSupportedException
            try { st.setQueryTimeout(timeoutSeconds); } catch (Exception ignored) {}
            // 多取 1 行以检测截断，与 MySQL 实现策略一致
            st.setMaxRows(maxRows + 1);

            try (ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<String> cols = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    cols.add(meta.getColumnLabel(i));
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(cols.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                    if (rows.size() > maxRows) break; // 已超出，截断标记
                }

                boolean truncated = rows.size() > maxRows;
                if (truncated) rows.remove(rows.size() - 1);

                return QueryResult.builder()
                        .rows(rows)
                        .returnedRows(rows.size())
                        .truncated(truncated)
                        .dbCostMs(System.currentTimeMillis() - start)
                        .build();
            }
        }
    }

    @Override
    public ExplainResult explainQuery(Connection conn, String sql, int timeoutSeconds) throws Exception {
        long start = System.currentTimeMillis();
        StringBuilder plan = new StringBuilder();

        try (Statement st = conn.createStatement()) {
            try { st.setQueryTimeout(timeoutSeconds); } catch (Exception ignored) {}
            try (ResultSet rs = st.executeQuery("EXPLAIN " + sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) plan.append(" | ");
                        plan.append(rs.getString(i));
                    }
                    plan.append("\n");
                }
            }
        }

        return ExplainResult.builder()
                .plan(plan.toString().trim())
                .dbCostMs(System.currentTimeMillis() - start)
                .build();
    }
}


