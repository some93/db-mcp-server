package com.mcp.adapter.mysql;

import com.mcp.adapter.spi.WriteExecutor;
import com.mcp.infrastructure.datasource.DatasourcePermissionException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MySqlWriteExecutorTest {

    private final MySqlWriteExecutor executor = new MySqlWriteExecutor();

    @Test
    void executeWrite_permissionDenied_rollsBackAndRestoresAutoCommit() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        SQLException sqlException = new SQLException(
                "INSERT command denied to user 'writer'@'%' for table 'orders'",
                "28000",
                1142
        );

        when(conn.getAutoCommit()).thenReturn(true);
        when(conn.createStatement()).thenReturn(stmt);
        when(conn.getCatalog()).thenReturn("demo_db");
        when(conn.getMetaData()).thenReturn(metaData);
        when(metaData.getUserName()).thenReturn("writer");
        when(stmt.executeUpdate("INSERT INTO orders VALUES (1)")).thenThrow(sqlException);

        DatasourcePermissionException ex = assertThrows(DatasourcePermissionException.class,
                () -> executor.executeWrite(conn, Collections.singletonList("INSERT INTO orders VALUES (1)"), 5));

        assertEquals("DATASOURCE_PERMISSION_DENIED", ex.getCode());
        verify(conn).rollback();
        verify(conn).setAutoCommit(false);
        verify(conn).setAutoCommit(true);
    }

    @Test
    void executeWrite_generalFailure_returnsFailedDetailAndRestoresAutoCommit() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        SQLException sqlException = new SQLException("Duplicate entry", "23000", 1062);

        when(conn.getAutoCommit()).thenReturn(true);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeUpdate("UPDATE orders SET status='DONE' WHERE id=1")).thenThrow(sqlException);

        WriteExecutor.WriteResult result = executor.executeWrite(
                conn,
                Collections.singletonList("UPDATE orders SET status='DONE' WHERE id=1"),
                5
        );

        assertEquals(0, result.getTotalAffectedRows());
        assertEquals(1, result.getDetails().size());
        assertEquals("FAILED", result.getDetails().get(0).getStatus());
        assertFalse(result.getDetails().get(0).getMessage().isEmpty());
        verify(conn).rollback();
        verify(conn).setAutoCommit(false);
        verify(conn).setAutoCommit(true);
    }
}
