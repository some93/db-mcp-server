package com.mcp.adapter.tdengine;

import com.mcp.adapter.spi.WriteExecutor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TDengineWriteExecutorTest {

    private final TDengineWriteExecutor executor = new TDengineWriteExecutor();

    @Test
    void executeWrite_marksRemainingStatementsSkippedWhenFailureOccurs() throws Exception {
        Connection conn = mock(Connection.class);
        Statement statement = mock(Statement.class);

        when(conn.createStatement()).thenReturn(statement);
        when(statement.executeUpdate("INSERT INTO meters VALUES (1)")).thenReturn(1);
        when(statement.executeUpdate("INSERT INTO meters VALUES (2)"))
                .thenThrow(new SQLException("write failed"));

        WriteExecutor.WriteResult result = executor.executeWrite(
                conn,
                Arrays.asList(
                        "INSERT INTO meters VALUES (1)",
                        "INSERT INTO meters VALUES (2)",
                        "INSERT INTO meters VALUES (3)"
                ),
                5
        );

        assertEquals(1, result.getTotalAffectedRows());
        assertFalse(result.isRolledBack());
        assertEquals(3, result.getDetails().size());
        assertEquals("SUCCESS", result.getDetails().get(0).getStatus());
        assertEquals("FAILED", result.getDetails().get(1).getStatus());
        assertEquals("SKIPPED", result.getDetails().get(2).getStatus());
    }
}
