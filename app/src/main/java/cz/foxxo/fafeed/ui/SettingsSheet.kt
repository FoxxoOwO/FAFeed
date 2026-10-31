package cz.foxxo.fafeed.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cz.foxxo.fafeed.R
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.util.Locale
import cz.foxxo.fafeed.FaApp
import cz.foxxo.fafeed.data.FaParser
import cz.foxxo.fafeed.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: FeedState,
    onSafeOnly: (Boolean) -> Unit,
    onBlurNsfw: (Boolean) -> Unit,
    onSyncAll: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    lang: String = LANG_SYSTEM,
    onSetLang: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var folderName by remember { mutableStateOf(prefs.downloadTreeName) }

    val selectedFolderLabel = stringResource(R.string.selected_folder)
    val app = context.applicationContext as? FaApp
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf(-1L) }
    var cacheRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(cacheRefresh) {
        cacheBytes = withContext(Dispatchers.IO) { app?.imageDiskCache?.size ?: 0L }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: selectedFolderLabel
            prefs.downloadTreeUri = uri.toString()
            prefs.downloadTreeName = name
            folderName = name
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (state.user.isNotBlank()) stringResource(R.string.signed_in_as, state.user)
                else stringResource(R.string.not_signed_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))

            SettingSwitch(
                title = stringResource(R.string.hide_nsfw),
                subtitle = stringResource(R.string.hide_nsfw_subtitle),
                checked = state.safeOnly,
                onChange = onSafeOnly,
            )
            SettingSwitch(
                title = stringResource(R.string.blur_nsfw),
                subtitle = stringResource(R.string.blur_nsfw_subtitle),
                checked = state.blurNsfw,
                onChange = onBlurNsfw,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    LANG_SYSTEM to stringResource(R.string.lang_system),
                    "cs" to stringResource(R.string.lang_czech),
                    "en" to stringResource(R.string.lang_english),
                ).forEach { (tag, label) ->
                    FilterChip(
                        selected = lang == tag,
                        onClick = { onSetLang(tag) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            Text(stringResource(R.string.watchlist), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val syncedSuffix = if (state.lastSyncAt > 0) {
                stringResource(
                    R.string.synced_at,
                    java.time.Instant.ofEpochMilli(state.lastSyncAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("d. M. HH:mm")),
                )
            } else ""
            Text(
                pluralStringResource(
                    R.plurals.followed_count,
                    state.followedCount,
                    state.followedCount,
                ) + (if (syncedSuffix.isEmpty()) "" else " $syncedSuffix"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.refreshing) {
                Text(
                    stringResource(R.string.syncing_progress, state.progressDone, state.progressTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onSyncAll, enabled = !state.refreshing) {
                Text(stringResource(R.string.sync_all))
            }
            Text(
                stringResource(R.string.sync_all_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            Text(stringResource(R.string.download_folder), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                folderName.ifBlank { stringResource(R.string.default_folder) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.choose_folder))
                }
                if (prefs.downloadTreeUri.isNotBlank()) {
                    TextButton(onClick = {
                        prefs.downloadTreeUri = ""
                        prefs.downloadTreeName = ""
                        folderName = ""
                    }) {
                        Text(stringResource(R.string.default_option))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            Text(stringResource(R.string.thumb_cache), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (cacheBytes < 0) stringResource(R.string.cache_counting)
                else stringResource(
                    R.string.cache_usage,
                    formatBytes(cacheBytes),
                    formatBytes(FaApp.DISK_CACHE_MAX_BYTES),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { app?.imageDiskCache?.clear() }
                        cacheRefresh++
                    }
                },
            ) {
                Text(stringResource(R.string.clear_cache))
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        openInBrowser(context, FaParser.BASE + "/msg/submissions/")
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.open_feed_on_fa), style = MaterialTheme.typography.bodyLarge)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onLogout()
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.log_out),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
    bytes >= 1_000L -> "${bytes / 1_000} kB"
    else -> "$bytes B"
}
