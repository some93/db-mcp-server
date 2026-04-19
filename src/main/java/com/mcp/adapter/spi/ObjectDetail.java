package com.mcp.adapter.spi;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 表或视图的完整结构描述，由 {@link MetadataAdapter#describeObject} 返回。
 * @author ouyanghang
 */
@Data
@Builder
public class ObjectDetail {
    /** 对象名称（表名或视图名）。 */
    private String objectName;

    /** 对象大类：TABLE 或 VIEW。 */
    private String objectType;

    /**
     * 存储引擎/物理类型细化。
     * MySQL：与 objectType 相同，通常为 TABLE 或 VIEW。
     * TDengine：SUPER_TABLE、CHILD_TABLE 或 NORMAL_TABLE。
     */
    private String tableType;

    /** 列信息列表，顺序与 DDL 中的列声明顺序一致。 */
    private List<ColumnInfo> columns;
}


