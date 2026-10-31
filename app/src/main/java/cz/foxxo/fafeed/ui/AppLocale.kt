package cz.foxxo.fafeed.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

const val LANG_SYSTEM = "system"

/**
 * Overrides the locale of everything inside [content], so the app can offer its own
 * language switch on every Android version (per-app languages are 13+ only).
 * [lang] == [LANG_SYSTEM] keeps the device locale.
 */
@Composable
fun AppLocale(lang: String, content: @Composable () -> Unit) {
    if (lang.isBlank() || lang == LANG_SYSTEM) {
        content()
        return
    }
    val context = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val locale = remember(lang) { Locale.forLanguageTag(lang) }
    val config = remember(lang, baseConfig) {
        Configuration(baseConfig).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localized = remember(lang, context) { context.createConfigurationContext(config) }
    // Only swap the resources, never LocalContext: replacing it would hide the Activity
    // from rememberLauncherForActivityResult / LocalLifecycleOwner and crash the settings sheet.
    CompositionLocalProvider(
        LocalConfiguration provides config,
        LocalResources provides localized.resources,
    ) {
        content()
    }
}