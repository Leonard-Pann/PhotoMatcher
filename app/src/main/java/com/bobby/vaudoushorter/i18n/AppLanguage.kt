package com.bobby.vaudoushorter.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    fun selected(context: Context): String = context.getSharedPreferences("language", Context.MODE_PRIVATE)
        .getString("choice", "system") ?: "system"

    fun select(context: Context, choice: String) {
        require(choice in listOf("system", "fr", "en"))
        context.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putString("choice", choice).apply()
    }

    fun wrap(context: Context): Context {
        val choice = selected(context)
        val system = Resources.getSystem().configuration.locales[0].language
        val language = resolve(choice, system)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language)))
        return context.createConfigurationContext(config)
    }

    internal fun resolve(choice: String, system: String): String = when (choice) {
        "fr", "en" -> choice
        else -> if (system == "fr") "fr" else "en"
    }
}

/** Captured once per composition; also usable by workers with application context. */
class UiStrings(context: Context) {
    private val resources = AppLanguage.wrap(context).resources
    val locale: Locale get() = resources.configuration.locales[0]
    operator fun invoke(id: Int, vararg args: Any): String = resources.getString(id, *args)
}

object LocalText {
    val current: UiStrings
        @androidx.compose.runtime.Composable get() {
            val context = androidx.compose.ui.platform.LocalContext.current
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            return androidx.compose.runtime.remember(context, configuration) { UiStrings(context) }
        }
}
