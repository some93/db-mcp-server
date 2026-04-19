package com.mcp.domain.cache;

import com.mcp.adapter.spi.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 单个数据源的元数据快照
 * @author ouyanghang
 */
@Data
@Builder
public class DatasourceMetadata {
    private String datasourceName;
    private List<TableInfo> tables;
    private List<IndexInfo> indexes;
    private List<ViewInfo> views;
    private long loadedAtMs;
    private boolean stale;
    private String staleReason;
}


