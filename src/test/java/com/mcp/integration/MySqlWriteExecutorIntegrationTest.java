package com.mcp.integration;

import com.mcp.adapter.spi.WriteExecutor;
import com.mcp.adapter.mysql.MySqlWriteExecutor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlWriteExecutorIntegrationTest {

    @Test
    void executeWrite_failureRollsBackCommittedWork() throws Exception {
        try (HikariDataSource dataSource = createH2DataSource("tx_integration")) {
            prepareSchema(dataSource);

            WriteExecutor.WriteResult result;
            try (Connection conn = dataSource.getConnection()) {
                result = new MySqlWriteExecutor().executeWrite(
                        conn,
                        Arrays.asList(
                                "INSERT INTO orders(id, status) VALUES (1, 'NEW')",
                                "INSERT INTO orders(id, status) VALUES (1, 'DUPLICATE')"
                        ),
                        5
                );
            }

            assertTrue(result.isRolledBack());
            assertEquals(0, result.getTotalAffectedRows());
            assertEquals(2, result.getDetails().size());
            assertEquals("SUCCESS", result.getDetails().get(0).getStatus());
            assertEquals("FAILED", result.getDetails().get(1).getStatus());

            try (Connection verifyConn = dataSource.getConnection();
                 Statement stmt = verifyConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM orders")) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    private void prepareSchema(HikariDataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE orders (id INT PRIMARY KEY, status VARCHAR(32))");
        }
    }

    private HikariDataSource createH2DataSource(String name) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }
}
