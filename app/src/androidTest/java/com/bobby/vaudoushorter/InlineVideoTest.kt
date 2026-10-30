package com.bobby.vaudoushorter

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.bobby.vaudoushorter.data.GalleryMedia
import com.bobby.vaudoushorter.ui.InlineVideo
import com.bobby.vaudoushorter.ui.theme.VaudouShorterTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class InlineVideoTest {
    private fun label(id: Int): String = com.bobby.vaudoushorter.i18n.UiStrings(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)(id)

    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: File

    @Before fun createClip() {
        fixture = createVideoFixture(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    }

    @After fun removeClip() { fixture.delete() }

    @Test fun videoAutoplaysPausesForMenusAndBackgroundAndStopsWhenRemoved() {
        var active by mutableStateOf(true)
        var visible by mutableStateOf(true)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        val media = GalleryMedia(fixture.toURI().toString(), "Autoplay.mp4", "test", "Test", "Test", true, fixture.length(), 1)
        compose.setContent {
            VaudouShorterTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (visible) InlineVideo(media, active, Modifier.fillMaxSize())
                }
            }
        }
        fun awaitState(state: String) {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasContentDescription("Vidéo : Autoplay.mp4") and
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)).fetchSemanticsNodes().size == 1
            }
        }
        // A real decoder must be playing, without a tap on a play button.
        awaitState(label(R.string.playing))
        compose.runOnIdle { active = false }
        awaitState(label(R.string.paused))
        compose.runOnIdle { active = true }
        awaitState(label(R.string.playing))
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        awaitState(label(R.string.paused))
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        awaitState(label(R.string.paused))
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        awaitState(label(R.string.playing))
        compose.onNodeWithContentDescription("Vidéo : Autoplay.mp4").performClick()
        awaitState(label(R.string.paused))
        compose.onNodeWithContentDescription("Vidéo : Autoplay.mp4").performClick()
        awaitState(label(R.string.playing))
        compose.runOnIdle { visible = false }
        compose.onNodeWithContentDescription("Vidéo : Autoplay.mp4").assertDoesNotExist()
    }
}
