package com.mcp.adapter.spi;

import lombok.Builder;
import lombok.Data;

/** 视图基础信息，由 {@link MetadataAdapter#listViews} 返回。
 * @author ouyanghang
 */
@Data
@Builder
public class ViewInfo {
    /** 视图名称。 */
    private String viewName;

    /** 视图的 SELECT 定义语句；部分数据库限制账号可能无法读取，此时为 null。 */
    private String definition;
}


