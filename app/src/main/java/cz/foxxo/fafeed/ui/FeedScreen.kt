package cz.foxxo.fafeed.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cz.foxxo.fafeed.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import cz.foxxo.fafeed.data.FaParser
import cz.foxxo.fafeed.data.FeedItem
import cz.foxxo.fafeed.ui.theme.ratingColor
import cz.foxxo.fafeed.ui.theme.ratingLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm: FeedViewModel,
    updateVm: UpdateViewModel,
    onOpenSubmission: (Long) -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogout: () -> Unit,
    lang: String = LANG_SYSTEM,
    onSetLang: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val state = vm.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Warm the disk cache a few cards ahead so scrolling never waits on the network.
    val appContext = LocalContext.current.applicationContext
    val prefetched = remember { mutableSetOf<String>() }
    LaunchedEffect(state.visibleItems.size, listState.firstVisibleItemIndex) {
        val loader = SingletonImageLoader.get(appContext)
        val from = listState.firstVisibleItemIndex.coerceAtLeast(0)
        state.visibleItems.drop(from).take(12).forEach { item ->
            val url = item.thumbUrl
            if (url.isNotBlank() && prefetched.add(url)) {
                runCatching {
                    loader.enqueue(
                        ImageRequest.Builder(appContext)
                            .data(url)
                            .size(360)
                            .build(),
                    )
                }
            }
        }
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val filtering = state.query.isNotBlank() || state.rating.isNotBlank()

    // Shows up once you scroll back up after reading further down the feed.
    val showJumpToTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 && listState.lastScrolledBackward
        }
    }

    LaunchedEffect(Unit) { vm.start() }

    if (settingsOpen) {
        SettingsSheet(
            updateVm = updateVm,
            lang = lang,
            onSetLang = onSetLang,
            state = state,
            onSafeOnly = vm::setSafeOnly,
            onBlurNsfw = vm::setBlurNsfw,
            onSyncAll = vm::syncAll,
            onLogout = onLogout,
            onDismiss = { settingsOpen = false },
        )
    }

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = showJumpToTop,
                enter = scaleIn(initialScale = 0.5f, animationSpec = tween(220)) + fadeIn(tween(180)),
                exit = scaleOut(targetScale = 0.5f, animationSpec = tween(160)) + fadeOut(tween(140)),
            ) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.jump_to_top))
                }
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    if (searchOpen) {
                        TextField(
                            value = state.query,
                            onValueChange = vm::setQuery,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { vm.setQuery("") }) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.clear_search),
                                        )
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Column {
                            Text(
                                "FA Feed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            val syncing = state.refreshing && state.progressTotal > 0
                            val subtitle = when {
                                syncing -> stringResource(
                                    R.string.syncing_progress,
                                    state.progressDone,
                                    state.progressTotal,
                                )

                                state.user.isNotBlank() -> "@${state.user}"
                                else -> ""
                            }
                            AnimatedContent(
                                targetState = subtitle,
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                                label = "subtitle",
                            ) { text ->
                                Text(
                                    text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (syncing) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (searchOpen) {
                        IconButton(onClick = { searchOpen = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close_search))
                        }
                    } else {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (searchOpen || filtering) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = state.rating.isBlank(),
                        onClick = { vm.setRating("") },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                    listOf("general", "mature", "adult").forEach { r ->
                        FilterChip(
                            selected = state.rating.equals(r, ignoreCase = true),
                            onClick = { vm.setRating(r) },
                            label = { Text(ratingLabel(r)) },
                        )
                    }
                    if (filtering) {
                        Text(
                            stringResource(R.string.search_result_count, state.visibleItems.size, state.totalShown),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val shouldLoadMore by remember(state.visibleCount, state.visibleItems.size) {
            derivedStateOf {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
            }
        }
        LaunchedEffect(shouldLoadMore, state.visibleCount) {
            if (shouldLoadMore && state.hasMore) vm.loadMore()
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && state.items.isEmpty() -> CenterBox {
                    CircularProgressIndicator()
                }

                state.visibleItems.isEmpty() -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (filtering) stringResource(R.string.search_no_results)
                            else state.error ?: if (state.followedCount > 0) {
                                pluralStringResource(
                                    R.plurals.empty_following,
                                    state.followedCount,
                                    state.followedCount,
                                )
                            } else {
                                stringResource(R.string.empty_no_watchlist)
                            },
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                if (filtering) {
                                    vm.setQuery("")
                                    vm.setRating("")
                                } else {
                                    vm.refresh()
                                }
                            },
                        ) {
                            Text(stringResource(if (filtering) R.string.search_clear else R.string.retry))
                        }
                    }
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.error != null) {
                        item(key = "error") {
                            Text(
                                state.error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    items(state.visibleItems, key = { it.id }) { item ->
                        FeedCard(
                            item = item,
                            blurNsfw = state.blurNsfw,
                            onOpen = { onOpenSubmission(item.id) },
                            onOpenProfile = onOpenProfile,
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
                            if (state.hasMore) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.loading_more), style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Text(
                                    pluralStringResource(
                                        R.plurals.feed_end,
                                        state.totalShown,
                                        state.totalShown,
                                        state.followedCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun FeedCard(
    item: FeedItem,
    blurNsfw: Boolean,
    onOpen: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var revealed by remember(item.id) { mutableStateOf(!item.isNsfw || !blurNsfw) }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cardPress",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onOpen,
            ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = avatarUrl(item.author),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenProfile(item.author) },
                ) {
                    Text(
                        item.authorDisplay.ifBlank { item.author },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "@${item.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RatingBadge(item.rating)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(item.aspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val blurRadius: Dp by animateDpAsState(
                    targetValue = if (revealed) 0.dp else 30.dp,
                    animationSpec = tween(340),
                    label = "nsfwBlur",
                )
                val veil by animateFloatAsState(
                    targetValue = if (revealed) 0f else 1f,
                    animationSpec = tween(300),
                    label = "nsfwVeil",
                )

                AsyncImage(
                    model = item.thumbUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurRadius),
                )
                if (veil > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = veil }
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(enabled = !revealed) { revealed = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = Color.White,
                            )
                            Text(
                                stringResource(R.string.nsfw_hidden),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title.ifBlank { stringResource(R.string.untitled) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { openInBrowser(context, item.viewUrl) }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.open_on_fa),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RatingBadge(rating: String) {
    val color = ratingColor(rating)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            ratingLabel(rating),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}
