package cz.foxxo.fafeed.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.foxxo.fafeed.R
import cz.foxxo.fafeed.data.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: String,
    onBack: () -> Unit,
    onOpenSubmission: (Long) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val vm: ProfileViewModel = viewModel()
    val state = vm.state

    LaunchedEffect(user) { vm.load(user) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = {
                    Column {
                        Text("@$user", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        if (state.items.isNotEmpty()) {
                            Text(
                                stringResource(R.string.profile_count, state.items.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { openInBrowser(context, ProfileViewModel.galleryUrl(user)) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.open_on_fa_short),
                        )
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = when {
                state.loading -> "loading"
                state.items.isEmpty() -> "error"
                else -> "content"
            },
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "profileState",
        ) { phase ->
            when (phase) {
                "loading" -> Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                "error" -> Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.error ?: stringResource(R.string.profile_empty),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { vm.retry() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        FeedCard(
                            item = item,
                            blurNsfw = prefs.blurNsfw,
                            onOpen = { onOpenSubmission(item.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "footer") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                state.moreLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.loading_more),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }

                                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        state.error.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                    )
                                    TextButton(onClick = { vm.retry() }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }

                                state.nextUrl != null -> TextButton(onClick = { vm.loadMore() }) {
                                    Text(stringResource(R.string.load_more))
                                }

                                else -> Text(
                                    stringResource(R.string.profile_end),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
