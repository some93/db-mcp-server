package com.mcp.domain.health;

/**
 * @author ouyanghang
 */
public class DatasourceUnavailableException extends RuntimeException {

    private final String datasourceName;

    public DatasourceUnavailableException(String datasourceName, String message) {
        super("Datasource [" + datasourceName + "] is unavailable: " + message);
        this.datasourceName = datasourceName;
    }

    public String getDatasourceName() {
        return datasourceName;
    }
}

