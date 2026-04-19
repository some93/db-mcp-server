package com.mcp.adapter.mysql;

import com.mcp.infrastructure.datasource.DatasourcePermissionException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MySqlMetadataAdapterTest {

    private final MySqlMetadataAdapter adapter = new MySqlMetadataAdapter();

    @Test
    void listTables_permissionDenied_wrapsException() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        SQLException sqlException = new SQLException(
                "SELECT command denied to user 'reader'@'%' for table 'TABLES'",
                "28000",
                1142
        );

        when(conn.getCatalog()).thenReturn("demo_db");
        when(conn.getMetaData()).thenReturn(metaData);
        when(metaData.getUserName()).thenReturn("reader");
        when(conn.prepareStatement(anyString())).thenThrow(sqlException);

        DatasourcePermissionException ex = assertThrows(DatasourcePermissionException.class,
                () -> adapter.listTables(conn));

        assertEquals("DATASOURCE_PERMISSION_DENIED", ex.getCode());
        assertEquals("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON demo_db.* TO 'reader'@'%'; FLUSH PRIVILEGES;",
                ex.getGrantHint());
    }

    @Test
    void listViews_permissionDenied_wrapsException() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        SQLException sqlException = new SQLException(
                "SELECT command denied to user 'reader'@'%' for table 'VIEWS'",
                "28000",
                1142
        );

        when(conn.getCatalog()).thenReturn("demo_db");
        when(conn.getMetaData()).thenReturn(metaData);
        when(metaData.getUserName()).thenReturn("reader");
        when(conn.prepareStatement(anyString())).thenThrow(sqlException);

        DatasourcePermissionException ex = assertThrows(DatasourcePermissionException.class,
                () -> adapter.listViews(conn));

        assertEquals("DATASOURCE_PERMISSION_DENIED", ex.getCode());
        assertEquals("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON demo_db.* TO 'reader'@'%'; FLUSH PRIVILEGES;",
                ex.getGrantHint());
    }
}
