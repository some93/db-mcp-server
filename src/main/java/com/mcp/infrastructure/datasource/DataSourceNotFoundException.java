package com.mcp.infrastructure.datasource;

/**
 * @author ouyanghang
 */
public class DataSourceNotFoundException extends RuntimeException {

    public DataSourceNotFoundException(String name) {
        super("Datasource not found: " + name);
    }
}

