package com.blocknize.app.ui.focus

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blocknize.app.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusActivityRecreationTest {
    @Test fun activityRecreationKeepsTheTaskSelectionWithoutAnIndependentTimer() {
        var original: FocusViewModel? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[FocusViewModel::class.java]
                original = model
                model.selectTask("selected-task")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = ViewModelProvider(activity)[FocusViewModel::class.java]
                assertSame(original, restored)
                assertEquals("selected-task", restored.selectedOccurrenceKey.value)
            }
        }
    }
}
