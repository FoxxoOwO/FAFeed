package cz.foxxo.fafeed.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.foxxo.fafeed.R

/** One place for the automatic-update prompt, download progress and install hand-off. */
@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    val state = vm.state
    if (!state.prompt && !state.downloading && state.downloaded == null && state.error == null) return

    AlertDialog(
        onDismissRequest = { if (!state.downloading) vm.dismiss() },
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Column {
                val release = state.release
                if (release != null) {
                    Text(stringResource(R.string.update_message, release.version.toString()))
                    val notes = release.notes.trim()
                    if (!state.downloading && notes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            notes.take(500),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (state.downloading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.update_downloading, state.progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.downloaded != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.update_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        state.downloading -> vm.cancelDownload()
                        state.downloaded != null -> vm.install()
                        else -> vm.download()
                    }
                },
            ) {
                Text(
                    stringResource(
                        when {
                            state.downloading -> R.string.update_cancel
                            state.downloaded != null -> R.string.update_install
                            else -> R.string.update_download
                        },
                    ),
                )
            }
        },
        dismissButton = {
            if (!state.downloading) {
                TextButton(onClick = { vm.dismiss() }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        },
    )
}
