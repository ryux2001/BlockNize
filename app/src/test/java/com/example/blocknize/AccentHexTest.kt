package com.blocknize.app

import com.blocknize.app.data.parseAccentHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentHexTest {
    @Test fun parsesHexWithOrWithoutHashAndForcesOpaqueAlpha() {
        assertEquals(0xFFA1B2C3.toInt(), parseAccentHex("#a1b2c3"))
        assertEquals(0xFFA1B2C3.toInt(), parseAccentHex("A1B2C3"))
    }

    @Test fun rejectsInvalidOrTransparentHex() {
        assertNull(parseAccentHex("#abc"))
        assertNull(parseAccentHex("#FFAABBCC"))
        assertNull(parseAccentHex("GG0000"))
    }
}
