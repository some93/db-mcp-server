package com.mcp.infrastructure.datasource;

/**
 * @author ouyanghang
 */
public enum DataSourceType {
    MYSQL,
    TDENGINE;

    public static DataSourceType fromString(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Datasource type must not be null");
        }
        switch (type.toLowerCase()) {
            case "mysql": return MYSQL;
            case "tdengine": return TDENGINE;
            default: throw new IllegalArgumentException("Unsupported datasource type: " + type);
        }
    }
}

