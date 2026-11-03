package com.blocknize.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetOpacityTest {
    @Test fun backgroundIsPureBlackWithConfigurableAlpha() {
        assertEquals(0x00000000, widgetBackgroundArgb(0))
        assertEquals(0x4D000000, widgetBackgroundArgb(30))
        assertEquals(0x80000000.toInt(), widgetBackgroundArgb(50))
        assertEquals(0xFF000000.toInt(), widgetBackgroundArgb(100))
        for (opacity in 0..100) {
            assertEquals(0, widgetBackgroundArgb(opacity) and 0x00FFFFFF)
        }
    }

    @Test fun backgroundClampsInvalidOpacity() {
        assertEquals(widgetBackgroundArgb(0), widgetBackgroundArgb(-1))
        assertEquals(widgetBackgroundArgb(100), widgetBackgroundArgb(101))
    }
}
