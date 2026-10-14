package br.com.phs.sqliterealtimecore

import br.com.phs.sqlrt.QueryEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class QueryEngineTest {

    @Test
    fun splitsOnSemicolonOutsideQuotesAndComments() {
        val sql = "select 'a;b' as x; -- ignore; this\nselect \"c;d\" /* e;f */ ; ;"
        val parts = QueryEngine.splitStatements(sql)
        assertEquals(2, parts.size)
        assertEquals("select 'a;b' as x", parts[0])
        assertEquals("-- ignore; this\nselect \"c;d\" /* e;f */", parts[1])
    }

    @Test
    fun handlesEscapedQuotes() {
        val parts = QueryEngine.splitStatements("select 'it''s;ok'; select 2")
        assertEquals(listOf("select 'it''s;ok'", "select 2"), parts)
    }

    @Test
    fun dropsCommentOnlyStatements() {
        assertEquals(emptyList<String>(), QueryEngine.splitStatements("-- nothing\n; /* x */"))
    }

    @Test
    fun firstKeywordSkipsComments() {
        assertEquals("SELECT", QueryEngine.firstKeyword("  -- c\n /* d */ select 1"))
        assertEquals("", QueryEngine.firstKeyword("-- comment only"))
    }

}
