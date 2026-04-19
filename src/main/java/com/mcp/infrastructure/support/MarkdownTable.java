package com.mcp.infrastructure.support;

import java.util.List;
import java.util.Map;

/**
 * 将行集合渲染为 Markdown 表格字符串。
 * @author ouyanghang
 */
public class MarkdownTable {

    public static String render(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return "_No rows returned._";

        List<String> headers = new java.util.ArrayList<>(rows.get(0).keySet());
        StringBuilder sb = new StringBuilder();

        // header row
        sb.append("| ");
        for (String h : headers) sb.append(h).append(" | ");
        sb.append("\n");

        // separator
        sb.append("| ");
        for (int i = 0; i < headers.size(); i++) sb.append("--- | ");
        sb.append("\n");

        // data rows
        for (Map<String, Object> row : rows) {
            sb.append("| ");
            for (String h : headers) {
                Object val = row.get(h);
                sb.append(val == null ? "NULL" : val.toString().replace("|", "\\|")).append(" | ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}


