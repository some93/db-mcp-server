package com.mcp.application.execution;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecuteWriteServiceTest {

    @SuppressWarnings("unchecked")
    private List<String> splitStatements(String sql) throws Exception {
        Method method = ExecuteWriteService.class.getDeclaredMethod("splitStatements", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(new ExecuteWriteService(null, null, null, null), sql);
    }

    @Test
    void splitStatements_keepsSemicolonInsideSingleQuotedString() throws Exception {
        List<String> statements = splitStatements(
                "INSERT INTO t(msg) VALUES('a;b');UPDATE t SET c=1 WHERE id=1");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t(msg) VALUES('a;b')", statements.get(0));
        assertEquals("UPDATE t SET c=1 WHERE id=1", statements.get(1));
    }

    @Test
    void splitStatements_keepsSemicolonInsideDoubleQuotedString() throws Exception {
        List<String> statements = splitStatements(
                "INSERT INTO t(msg) VALUES(\"a;b\");DELETE FROM t WHERE id=2");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t(msg) VALUES(\"a;b\")", statements.get(0));
        assertEquals("DELETE FROM t WHERE id=2", statements.get(1));
    }

    @Test
    void splitStatements_ignoresEmptySegments() throws Exception {
        List<String> statements = splitStatements(" ; INSERT INTO t VALUES (1); ; ");

        assertEquals(1, statements.size());
        assertEquals("INSERT INTO t VALUES (1)", statements.get(0));
    }

    @Test
    void splitStatements_keepsSemicolonInsideBackslashEscapedSingleQuote() throws Exception {
        List<String> statements = splitStatements(
                "INSERT INTO t(msg) VALUES ('it\\'s;ok');UPDATE t SET c=1 WHERE id=1");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t(msg) VALUES ('it\\'s;ok')", statements.get(0));
        assertEquals("UPDATE t SET c=1 WHERE id=1", statements.get(1));
    }

    @Test
    void splitStatements_keepsSemicolonInsideBackslashEscapedDoubleQuote() throws Exception {
        List<String> statements = splitStatements(
                "INSERT INTO t(msg) VALUES (\"a\\\";b\");DELETE FROM t WHERE id=2");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t(msg) VALUES (\"a\\\";b\")", statements.get(0));
        assertEquals("DELETE FROM t WHERE id=2", statements.get(1));
    }
}
