package com.mcp.adapter.spi;

import lombok.Builder;
import lombok.Data;

/** 索引信息，由 {@link MetadataAdapter#listIndexes} 返回。
 * @author ouyanghang
 */
@Data
@Builder
public class IndexInfo {
    /** 索引名称。 */
    private String indexName;

    /** 索引所属表名。 */
    private String tableName;

    /** 是否为唯一索引（UNIQUE INDEX）。 */
    private boolean unique;

    /** 索引包含的列名，多列时以逗号分隔，顺序与索引定义一致。 */
    private String columns;
}


