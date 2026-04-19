package com.mcp.adapter.spi;

import lombok.Builder;
import lombok.Data;

/** 表或视图的基础信息，用于列表展示场景。
 * @author ouyanghang
 */
@Data
@Builder
public class TableInfo {
    /** 对象名称（表名或视图名）。 */
    private String name;

    /** 对象类型：TABLE、VIEW（MySQL）；SUPER_TABLE、CHILD_TABLE（TDengine）。 */
    private String tableType;
}


