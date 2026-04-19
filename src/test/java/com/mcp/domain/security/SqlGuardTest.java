package com.mcp.domain.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlGuardTest {

    // ---- stripComments ----

    @Test
    void stripComments_removesLineComment() {
        assertEquals("SELECT 1", SqlGuard.stripComments("SELECT 1 -- comment").trim());
    }

    @Test
    void stripComments_removesBlockComment() {
        assertEquals("SELECT 1", SqlGuard.stripComments("SELECT /* block */ 1").replaceAll("\\s+", " ").trim());
    }

    @Test
    void stripComments_removesNestedBlockAndLine() {
        String result = SqlGuard.stripComments("SELECT /* a */ 1 -- b\nFROM t");
        assertFalse(result.contains("--"));
        assertFalse(result.contains("/*"));
    }

    // ---- checkQuery ----

    @Test
    void checkQuery_allowsSelect() {
        assertDoesNotThrow(() -> SqlGuard.checkQuery("SELECT 1", 10000));
    }

    @Test
    void checkQuery_blocksInsert() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkQuery("INSERT INTO t VALUES (1)", 10000));
        assertEquals("SQL_BLOCK_TYPE", ex.getCode());
    }

    @Test
    void checkQuery_blocksDropKeyword() {
        assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkQuery("DROP TABLE t", 10000));
    }

    @Test
    void checkQuery_blocksSelectWithDrop() {
        assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkQuery("SELECT * FROM t WHERE DROP = 1", 10000));
    }

    @Test
    void checkQuery_tooLong() {
        String sql = "SELECT " + "a".repeat(100);
        assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkQuery(sql, 50));
    }

    // ---- checkWrite ----

    @Test
    void checkWrite_allowsInsert() {
        assertDoesNotThrow(() ->
                SqlGuard.checkWrite("INSERT INTO t (id) VALUES (1)", 10000, 1000));
    }

    @Test
    void checkWrite_blocksInsertSelect() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkWrite("INSERT INTO t SELECT * FROM s", 10000, 1000));
        assertEquals("SQL_BLOCK_INSERT_SELECT", ex.getCode());
    }

    @Test
    void checkWrite_allowsUpdateWithWhere() {
        assertDoesNotThrow(() ->
                SqlGuard.checkWrite("UPDATE t SET a=1 WHERE id=2", 10000, 1000));
    }

    @Test
    void checkWrite_blocksUpdateWithoutWhere() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkWrite("UPDATE t SET a=1", 10000, 1000));
        assertEquals("SQL_BLOCK_NO_WHERE", ex.getCode());
    }

    @Test
    void checkWrite_blocksUpdateWhereTrue() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkWrite("UPDATE t SET a=1 WHERE 1=1", 10000, 1000));
        assertEquals("SQL_BLOCK_PSEUDO_WHERE", ex.getCode());
    }

    @Test
    void checkWrite_blocksUpdateWhereTrueKeyword() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkWrite("UPDATE t SET a=1 WHERE TRUE", 10000, 1000));
        assertEquals("SQL_BLOCK_PSEUDO_WHERE", ex.getCode());
    }

    @Test
    void checkWrite_blocksDeleteWithoutWhere() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkWrite("DELETE FROM t", 10000, 1000));
        assertEquals("SQL_BLOCK_NO_WHERE", ex.getCode());
    }

    @Test
    void checkWrite_blocksDeleteWhereIsNotNull() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkWrite("DELETE FROM t WHERE id IS NOT NULL", 10000, 1000));
        assertEquals("SQL_BLOCK_PSEUDO_WHERE", ex.getCode());
    }

    @Test
    void checkWrite_blocksBatchTooLarge() {
        String sql = "INSERT INTO t VALUES (1),(2),(3)";
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkWrite(sql, 10000, 2));
        assertEquals("SQL_BLOCK_BATCH_TOO_LARGE", ex.getCode());
    }

    @Test
    void checkWrite_allowsBatchWithinLimit() {
        assertDoesNotThrow(() ->
                SqlGuard.checkWrite("INSERT INTO t VALUES (1),(2),(3)", 10000, 5));
    }

    // ---- checkCreateTable ----

    @Test
    void checkCreateTable_allowsCreate() {
        assertDoesNotThrow(() ->
                SqlGuard.checkCreateTable("CREATE TABLE t (id INT PRIMARY KEY)", 10000));
    }

    @Test
    void checkCreateTable_blocksCreateAsSelect() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkCreateTable("CREATE TABLE t AS SELECT * FROM s", 10000));
        assertEquals("SQL_BLOCK_CREATE_AS_SELECT", ex.getCode());
    }

    @Test
    void checkCreateTable_blocksCreateAsSelectWithNewline() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkCreateTable("CREATE TABLE t AS\nSELECT * FROM s", 10000));
        assertEquals("SQL_BLOCK_CREATE_AS_SELECT", ex.getCode());
    }

    @Test
    void checkCreateTable_blocksCreateAsSelectWithTab() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkCreateTable("CREATE TABLE t AS\tSELECT * FROM s", 10000));
        assertEquals("SQL_BLOCK_CREATE_AS_SELECT", ex.getCode());
    }

    @Test
    void checkCreateTable_blocksCreateAsSelectWithParenthesisAndSpaces() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkCreateTable("CREATE TABLE t AS   (  SELECT * FROM s)", 10000));
        assertEquals("SQL_BLOCK_CREATE_AS_SELECT", ex.getCode());
    }

    @Test
    void checkCreateTable_blocksSelect() {
        SqlBlockedException ex = assertThrows(SqlBlockedException.class,
                () -> SqlGuard.checkCreateTable("SELECT 1", 10000));
        assertEquals("SQL_BLOCK_TYPE", ex.getCode());
    }

    // ---- countBatchInsertRows ----

    @Test
    void countBatchInsertRows_singleRow() {
        assertEquals(1, SqlGuard.countBatchInsertRows("INSERT INTO t VALUES (1, 'a')"));
    }

    @Test
    void countBatchInsertRows_multipleRows() {
        assertEquals(3, SqlGuard.countBatchInsertRows("INSERT INTO t VALUES (1),(2),(3)"));
    }

    @Test
    void countBatchInsertRows_nestedParens() {
        // VALUES with nested function calls: (1, NOW()), (2, NOW())
        assertEquals(2, SqlGuard.countBatchInsertRows("INSERT INTO t VALUES (1, NOW()),(2, NOW())"));
    }

    @Test
    void countBatchInsertRows_noValues() {
        // INSERT ... SELECT has no VALUES keyword
        assertEquals(1, SqlGuard.countBatchInsertRows("INSERT INTO t SELECT id FROM s"));
    }

    @Test
    void countBatchInsertRows_fiveRows() {
        assertEquals(5, SqlGuard.countBatchInsertRows(
                "INSERT INTO t VALUES (1),(2),(3),(4),(5)"));
    }
}
