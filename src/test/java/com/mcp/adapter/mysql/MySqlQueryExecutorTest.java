package com.mcp.adapter.mysql;

import com.mcp.infrastructure.datasource.DatasourcePermissionException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MySqlQueryExecutorTest {

    private final MySqlQueryExecutor executor = new MySqlQueryExecutor();

    @Test
    void executeQuery_permissionDenied_wrapsException() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        SQLException sqlException = new SQLException(
                "SELECT command denied to user 'tester'@'%' for table 'orders'",
                "28000",
                1142
        );

        when(conn.createStatement()).thenReturn(stmt);
        when(conn.getCatalog()).thenReturn("demo_db");
        when(conn.getMetaData()).thenReturn(metaData);
        when(metaData.getUserName()).thenReturn("tester");
        when(stmt.executeQuery(anyString())).thenThrow(sqlException);

        DatasourcePermissionException ex = assertThrows(DatasourcePermissionException.class,
                () -> executor.executeQuery(conn, "SELECT * FROM orders", 10, 5));

        assertEquals("DATASOURCE_PERMISSION_DENIED", ex.getCode());
        assertEquals("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON demo_db.* TO 'tester'@'%'; FLUSH PRIVILEGES;",
                ex.getGrantHint());
    }

    @Test
    void explainQuery_permissionDenied_wrapsException() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        SQLException sqlException = new SQLException(
                "EXPLAIN command denied to user 'reader'@'%' for table 'orders'",
                "28000",
                1142
        );

        when(conn.createStatement()).thenReturn(stmt);
        when(conn.getCatalog()).thenReturn("demo_db");
        when(conn.getMetaData()).thenReturn(metaData);
        when(metaData.getUserName()).thenReturn("reader");
        when(stmt.executeQuery(anyString())).thenThrow(sqlException);

        DatasourcePermissionException ex = assertThrows(DatasourcePermissionException.class,
                () -> executor.explainQuery(conn, "SELECT * FROM orders", 5));

        assertEquals("DATASOURCE_PERMISSION_DENIED", ex.getCode());
        assertEquals("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON demo_db.* TO 'reader'@'%'; FLUSH PRIVILEGES;",
                ex.getGrantHint());
    }
}
