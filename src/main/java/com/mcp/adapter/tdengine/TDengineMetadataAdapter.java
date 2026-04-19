package com.mcp.adapter.tdengine;

import com.mcp.adapter.spi.ColumnInfo;
import com.mcp.adapter.spi.IndexInfo;
import com.mcp.adapter.spi.MetadataAdapter;
import com.mcp.adapter.spi.ObjectDetail;
import com.mcp.adapter.spi.TableInfo;
import com.mcp.adapter.spi.ViewInfo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TDengine 元数据适配器。
 * 使用 SHOW STABLES / SHOW TABLES / DESCRIBE 而非 information_schema（TDengine 不完全支持）。
 * @author ouyanghang
 */
public class TDengineMetadataAdapter implements MetadataAdapter {

    @Override
    public List<TableInfo> listTables(Connection conn) throws Exception {
        List<TableInfo> result = new ArrayList<>();

        // 超表（SUPER TABLE）
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW STABLES")) {
            while (rs.next()) {
                result.add(TableInfo.builder()
                        .name(rs.getString(1))
                        .tableType("SUPER_TABLE")
                        .build());
            }
        }

        // 普通表和子表（TDengine SHOW TABLES 同时包含两者，列 type 区分）
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                String name = rs.getString(1);
                // 第3列 stable_name：非空表示子表，空表示普通表
                String stableName = null;
                try { stableName = rs.getString(3); } catch (Exception ignored) {}
                String tableType = (stableName != null && !stableName.isEmpty()) ? "CHILD_TABLE" : "TABLE";
                result.add(TableInfo.builder()
                        .name(name)
                        .tableType(tableType)
                        .build());
            }
        }

        return result;
    }

    @Override
    public ObjectDetail describeObject(Connection conn, String objectName, String objectType) throws Exception {
        validateIdentifier(objectName);
        List<ColumnInfo> columns = new ArrayList<>();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("DESCRIBE `" + objectName + "`")) {
            while (rs.next()) {
                // DESCRIBE 返回列：Field, Type, Length, Note
                String colName  = rs.getString("Field");
                String colType  = rs.getString("Type");
                int    length   = 0;
                try { length = rs.getInt("Length"); } catch (Exception ignored) {}
                String note     = "";
                try { note = rs.getString("Note"); } catch (Exception ignored) {}

                // TDengine 用 Note 字段标注 TAG，主键为第一列（时间戳）
                boolean isPrimaryKey = "TIMESTAMP".equalsIgnoreCase(colType) && columns.isEmpty();

                columns.add(ColumnInfo.builder()
                        .name(colName)
                        .dataType(colType)
                        .length(length > 0 ? length : null)
                        .primaryKey(isPrimaryKey)
                        .nullable(!isPrimaryKey)
                        .comment(note)
                        .build());
            }
        }

        // 判断表类型：先试 STABLES，找不到则用传入的 objectType
        String resolvedType = resolveTableType(conn, objectName, objectType);

        return ObjectDetail.builder()
                .objectName(objectName)
                .objectType("TABLE")
                .tableType(resolvedType)
                .columns(columns)
                .build();
    }

    /** TDengine 不支持用户定义索引，返回空列表 */
    @Override
    public List<IndexInfo> listIndexes(Connection conn) throws Exception {
        return Collections.emptyList();
    }

    /** TDengine 不支持视图（3.x 流计算除外，JDBC 不暴露），返回空列表 */
    @Override
    public List<ViewInfo> listViews(Connection conn) throws Exception {
        return Collections.emptyList();
    }

    private String resolveTableType(Connection conn, String objectName, String objectType) {
        if (objectType != null && !objectType.isBlank()) return objectType.toUpperCase();
        // SHOW STABLES LIKE 不拼接用户输入，只做精确名称查找避免通配符注入
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW STABLES")) {
            while (rs.next()) {
                if (objectName.equalsIgnoreCase(rs.getString(1))) return "SUPER_TABLE";
            }
        } catch (Exception ignored) {}
        return "TABLE";
    }

    /** 仅允许合法标识符字符，防止通过 objectName 注入 SQL */
    private void validateIdentifier(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_\\.]+")) {
            throw new IllegalArgumentException("Invalid identifier: " + name);
        }
    }
}


