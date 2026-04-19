package com.mcp.adapter.spi;

import lombok.Builder;
import lombok.Data;

/** 数据库列的详细信息，作为 {@link ObjectDetail#getColumns()} 的元素。
 * @author ouyanghang
 */
@Data
@Builder
public class ColumnInfo {
    /** 列名。 */
    private String name;

    /** 数据类型字符串，如 {@code varchar}、{@code int}、{@code timestamp}。 */
    private String dataType;

    /** 字符类型的最大长度；非字符类型为 null。 */
    private Integer length;

    /** 数值类型的精度（小数位数）；非数值类型为 null。 */
    private Integer precision;

    /** 是否为主键列（联合主键时多列均为 true）。 */
    private boolean primaryKey;

    /** 是否允许 NULL 值。 */
    private boolean nullable;

    /** 列的默认值表达式字符串；未设置时为 null。 */
    private String defaultValue;

    /** DDL 中的列注释；未设置时为 null。 */
    private String comment;
}


