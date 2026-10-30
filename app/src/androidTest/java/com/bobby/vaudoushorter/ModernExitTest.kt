package com.bobby.vaudoushorter

import android.Manifest
import android.net.Uri
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bobby.vaudoushorter.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion = 30)
class ModernExitTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private suspend fun consent(allow: Boolean) {
        val id = if (allow) "android:id/button1" else "android:id/button2"
        withTimeout(15_000) {
            while (true) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val button = root?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull { it.isEnabled && it.isVisibleToUser }
                if (button != null && root.packageName?.toString() != context.packageName) {
                    check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    return@withTimeout
                }
                delay(50)
            }
        }
    }

    @Test fun exitRespectsSystemRefusalAndThenDeletesOnlyAfterSystemApproval() = runBlocking {
        val permissions = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissions.forEach { shell("pm grant ${context.packageName} $it") }
        shell("am start -n ${context.packageName}.test/${ForeignMediaActivity::class.java.name}")
        lateinit var item: GalleryMedia
        withTimeout(15_000) {
            while (true) {
                val fixture = GalleryRepository(context).loadMedia(GalleryAccess.read(context)).firstOrNull { it.folderName == "VaudouConsent" }
                if (fixture != null) { item = fixture; break }
                delay(50)
            }
        }
        fun exists() = context.contentResolver.query(Uri.parse(item.uri), arrayOf("_id"), null, null, null)!!.use { it.moveToFirst() }
        val first = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: GalleryViewModel
        first.onActivity { model = ViewModelProvider(it)[GalleryViewModel::class.java] }
        try {
            withTimeout(15_000) { model.state.first { !it.loading && it.error == null } }
            first.onActivity {
                model.selectFolder(item.folderKey)
                model.stageChoice(item.uri, Decision.TRASH)
                assertTrue(exists())
                it.onBackPressedDispatcher.onBackPressed()
            }
            consent(false)
            withTimeout(15_000) { while (first.state != Lifecycle.State.DESTROYED) delay(20) }
            assertTrue(exists())
            assertTrue(ReviewStore(context).read().choices.any { it.media.uri == item.uri && it.decision == Decision.TRASH })
        } finally { first.close() }
        val second = ActivityScenario.launch(MainActivity::class.java)
        second.onActivity { model = ViewModelProvider(it)[GalleryViewModel::class.java] }
        try {
            withTimeout(15_000) { model.state.first { !it.loading && it.error == null } }
            second.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            consent(true)
            withTimeout(20_000) {
                ExitDeletions.get(context).state.first { !it.running && it.history.any { entry -> entry.media.uri == item.uri } }
                while (second.state != Lifecycle.State.DESTROYED) delay(20)
            }
            assertFalse(exists())
        } finally { second.close() }
    }
}
