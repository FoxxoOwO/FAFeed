package cz.foxxo.fafeed.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.foxxo.fafeed.BuildConfig
import cz.foxxo.fafeed.R
import cz.foxxo.fafeed.data.Prefs
import cz.foxxo.fafeed.data.ReleaseInfo
import cz.foxxo.fafeed.data.UpdateChecker
import cz.foxxo.fafeed.data.UpdateRepository
import kotlinx.coroutines.launch
import java.io.File

data class UpdateState(
    val checking: Boolean = false,
    val release: ReleaseInfo? = null,
    /** Dialog is shown automatically only for a newer, not-skipped release. */
    val prompt: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val downloaded: File? = null,
    val error: String? = null,
    /** String resource shown after a manual check. */
    val notice: Int? = null,
)

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = UpdateRepository(app)

    var state by mutableStateOf(UpdateState())
        private set

    /** Called on app start; quiet (no dialog on failure, at most once per day). */
    fun autoCheckIfDue() {
        if (!prefs.autoUpdate) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastUpdateCheck < CHECK_INTERVAL_MS) return
        prefs.lastUpdateCheck = now
        check(manual = false)
    }

    /** Called from Settings; always hits the network and reports the result. */
    fun checkNow() {
        if (state.checking || state.downloading) return
        prefs.lastUpdateCheck = System.currentTimeMillis()
        check(manual = true)
    }

    private fun check(manual: Boolean) {
        if (state.checking || state.downloading) return
        state = state.copy(checking = true, error = null, notice = null)
        viewModelScope.launch {
            val release = runCatching { UpdateChecker.latest() }.getOrNull()
            val newer = release != null && UpdateChecker.isNewer(release, BuildConfig.VERSION_NAME)
            if (!newer) {
                state = state.copy(
                    checking = false,
                    release = null,
                    prompt = false,
                    downloaded = null,
                    notice = if (manual) {
                        if (release == null) R.string.update_check_failed else R.string.update_up_to_date
                    } else null,
                )
                return@launch
            }
            state = state.copy(
                checking = false,
                release = release,
                prompt = prefs.skippedUpdateVersion != release.tag,
                notice = if (manual) R.string.update_available_short else null,
                downloaded = null,
            )
        }
    }

    /** Re-open the dialog for a release previously dismissed with "Later". */
    fun showPrompt() {
        if (state.release != null) {
            prefs.skippedUpdateVersion = ""
            state = state.copy(prompt = true, error = null)
        }
    }

    fun download() {
        val release = state.release ?: return
        if (state.downloading) return
        state = state.copy(downloading = true, progress = 0, error = null)
        viewModelScope.launch {
            try {
                val file = repo.download(release) { progress -> state = state.copy(progress = progress) }
                state = state.copy(downloading = false, downloaded = file, progress = 100)
            } catch (t: Throwable) {
                // A cancelled download is not an error worth showing.
                if (t is java.io.IOException && "canceled".equals(t.message, ignoreCase = true)) {
                    state = state.copy(downloading = false, progress = 0)
                } else {
                    state = state.copy(
                        downloading = false,
                        progress = 0,
                        error = getApplication<Application>().getString(R.string.update_download_failed),
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        repo.cancel()
        state = state.copy(downloading = false, progress = 0)
    }

    fun dismiss() {
        state.release?.let { prefs.skippedUpdateVersion = it.tag }
        state = state.copy(prompt = false, error = null)
    }

    fun clearNotice() {
        state = state.copy(notice = null)
    }

    /** Hands the APK to the system installer; Android always asks the user to confirm. */
    fun install() {
        val file = state.downloaded ?: return
        val context = getApplication<Application>()
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            state = state.copy(error = context.getString(R.string.update_allow_install))
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
        prefs.skippedUpdateVersion = ""
        state = state.copy(prompt = false, downloaded = null, release = null, error = null)
    }

    fun setAutoUpdate(value: Boolean) {
        prefs.autoUpdate = value
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
