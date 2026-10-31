package com.overtimelog.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimpleHoursTest {
    @Test fun `parse accepts valid hours`() {
        assertEquals(150, SimpleHours.parseMinutes("2.5"))
        assertEquals(1440, SimpleHours.parseMinutes("24"))
        assertEquals(15, SimpleHours.parseMinutes("0.25"))
        assertEquals(180, SimpleHours.parseMinutes(" 3 "))
    }
    @Test fun `parse rejects invalid input`() {
        assertNull(SimpleHours.parseMinutes("0"))
        assertNull(SimpleHours.parseMinutes("25"))
        assertNull(SimpleHours.parseMinutes(""))
        assertNull(SimpleHours.parseMinutes("abc"))
    }
}
