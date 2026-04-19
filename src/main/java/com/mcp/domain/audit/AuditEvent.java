package com.mcp.domain.audit;

/**
 * 单次工具调用的审计快照。不可变，通过 Builder 构造。
 * @author ouyanghang
 */
public class AuditEvent {

    private final String timestamp;
    private final String tool;
    private final String datasource;
    private final String sql;
    private final boolean success;
    private final long durationMs;
    private final String errorCode;
    private final String errorMsg;

    private AuditEvent(Builder b) {
        this.timestamp = b.timestamp;
        this.tool = b.tool;
        this.datasource = b.datasource;
        this.sql = b.sql;
        this.success = b.success;
        this.durationMs = b.durationMs;
        this.errorCode = b.errorCode;
        this.errorMsg = b.errorMsg;
    }

    public String getTimestamp()  { return timestamp; }
    public String getTool()       { return tool; }
    public String getDatasource() { return datasource; }
    public String getSql()        { return sql; }
    public boolean isSuccess()    { return success; }
    public long getDurationMs()   { return durationMs; }
    public String getErrorCode()  { return errorCode; }
    public String getErrorMsg()   { return errorMsg; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String timestamp;
        private String tool;
        private String datasource;
        private String sql;
        private boolean success;
        private long durationMs;
        private String errorCode;
        private String errorMsg;

        public Builder timestamp(String v)  { this.timestamp = v; return this; }
        public Builder tool(String v)       { this.tool = v; return this; }
        public Builder datasource(String v) { this.datasource = v; return this; }
        public Builder sql(String v)        { this.sql = v; return this; }
        public Builder success(boolean v)   { this.success = v; return this; }
        public Builder durationMs(long v)   { this.durationMs = v; return this; }
        public Builder errorCode(String v)  { this.errorCode = v; return this; }
        public Builder errorMsg(String v)   { this.errorMsg = v; return this; }

        public AuditEvent build() { return new AuditEvent(this); }
    }
}


