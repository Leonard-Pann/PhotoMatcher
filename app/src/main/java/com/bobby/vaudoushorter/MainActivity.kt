package com.bobby.vaudoushorter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobby.vaudoushorter.data.GalleryAccess
import com.bobby.vaudoushorter.ui.GalleryScreen
import com.bobby.vaudoushorter.ui.theme.VaudouShorterTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[GalleryViewModel::class.java] }
    private var externalTransition = false
    private var userLeaving = false
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        onBackPressedDispatcher.addCallback(this) { applyAndClose() }
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                externalTransition = false
                model.refresh()
            }
            val deleteConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                externalTransition = false
                model.onConsentResult(it.resultCode == Activity.RESULT_OK)
            }
            LaunchedEffect(state.consent?.id) {
                state.consent?.let { request ->
                    if (model.markConsentLaunched(request.id)) {
                        try {
                            externalTransition = true
                            deleteConsent.launch(IntentSenderRequest.Builder(request.sender).build())
                        }
                        catch (_: Exception) {
                            model.onConsentResult(false)
                            model.notice("La confirmation Android n’a pas pu être ouverte. Réessayez.")
                        }
                    }
                }
            }
            VaudouShorterTheme {
                GalleryScreen(state, model,
                    requestAccess = { externalTransition = true; permissions.launch(GalleryAccess.permissions()) },
                    openSettings = {
                        externalTransition = true
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")))
                    }, onExit = ::applyAndClose)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        externalTransition = false
        userLeaving = false
        model.onForeground()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeaving = !externalTransition && !closing && model.state.value.consent == null
    }

    override fun onStop() {
        super.onStop()
        model.onBackground()
        if (userLeaving && !isChangingConfigurations && !closing) model.onBackgroundExit()
        userLeaving = false
    }

    private fun applyAndClose() {
        if (closing) return
        closing = true
        lifecycleScope.launch {
            try { model.applyAndExit() }
            catch (_: Exception) { model.notice("Les choix sont conservés pour la prochaine sortie.") }
            finally { finish() }
        }
    }
}
