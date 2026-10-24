package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseDateParserTest {
    @Test fun supportsIsoAndEpochFormats() {
        assertEquals(1761350400000L, ReleaseDateParser.parse("2025-10-25T00:00:00Z"))
        assertEquals(1761350400000L, ReleaseDateParser.parse("1761350400"))
        assertEquals(1761350400000L, ReleaseDateParser.parse("1761350400000"))
    }

    @Test fun badDatesAreNotInvented() {
        assertNull(ReleaseDateParser.parse(null))
        assertNull(ReleaseDateParser.parse(""))
        assertNull(ReleaseDateParser.parse("unknown"))
        assertNull(ReleaseDateParser.parse("2025-30-99"))
        assertNull(ReleaseDateParser.parse("0"))
    }
}
