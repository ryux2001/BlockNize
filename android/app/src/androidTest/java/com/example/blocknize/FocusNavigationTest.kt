package com.blocknize.app.ui.focus

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.blocknize.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FocusNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun rotate(orientation: Int) {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(timeoutMillis = 10_000) { compose.activity.resources.configuration.orientation == orientation }
        compose.waitForIdle()
    }

    @Test fun focusTabIsRestoredAfterRecreatingTheActivity() {
        compose.onNodeWithContentDescription("Concentración").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("destination-FOCUS").assertIsSelected()
        compose.onNodeWithTag("app-top-bar").assertDoesNotExist()
        compose.onNodeWithText("Cronómetro").assertDoesNotExist()
        compose.onNodeWithText("Cuenta atrás").assertDoesNotExist()
    }

    @Test fun landscapeUsesAnIconOnlyRailOnTheRightAndPreservesNavigation() {
        rotate(Configuration.ORIENTATION_PORTRAIT)
        compose.onNodeWithContentDescription("Concentración").performClick()
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        try {
            compose.onNodeWithTag("navigation-bottom").assertDoesNotExist()
            val rail = compose.onNodeWithTag("navigation-right").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("Navigation must occupy the right edge", rail.left > root.center.x)
            compose.onNodeWithTag("destination-FOCUS").assertIsSelected()
            compose.onNodeWithTag("app-top-bar").assertDoesNotExist()
            compose.onNodeWithTag("focus-settings").assertIsDisplayed().performClick()
            compose.onNodeWithText("Color de acento").assertIsDisplayed()
            compose.onNodeWithContentDescription("Cerrar apariencia").performClick()
            compose.onNodeWithTag("destination-FOCUS").assertIsSelected()
            // Neither selected nor unselected destinations render labels inside the rail.
            compose.onNodeWithText("Inicio").assertDoesNotExist()
            compose.onNodeWithText("Calendario").assertDoesNotExist()
            compose.onNodeWithTag("destination-CALENDAR").performClick().assertIsSelected()
            compose.onNodeWithText("Semana").assertIsDisplayed()
            compose.onNodeWithTag("app-top-bar").assertIsDisplayed()
            compose.onNodeWithTag("focus-settings").assertDoesNotExist()
        } finally {
            rotate(Configuration.ORIENTATION_PORTRAIT)
        }
        compose.onNodeWithTag("navigation-right").assertDoesNotExist()
        compose.onNodeWithTag("navigation-bottom").assertIsDisplayed()
        compose.onNodeWithTag("destination-CALENDAR").assertIsSelected()
    }

    @Test fun portraitFocusHasSettingsAccessWithoutAHeader() {
        rotate(Configuration.ORIENTATION_PORTRAIT)
        compose.onNodeWithTag("destination-FOCUS").performClick()
        compose.onNodeWithTag("app-top-bar").assertDoesNotExist()
        compose.onNodeWithTag("navigation-bottom").assertIsDisplayed()
        compose.onNodeWithTag("focus-settings").assertIsDisplayed().performClick()
        compose.onNodeWithText("Color de acento").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cerrar apariencia").performClick()
        compose.onNodeWithTag("app-top-bar").assertDoesNotExist()
    }
}
