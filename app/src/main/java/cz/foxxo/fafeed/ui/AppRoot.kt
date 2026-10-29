package cz.foxxo.fafeed.ui

import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.foxxo.fafeed.data.Prefs

private enum class Screen { LOGIN, FEED, DETAIL }

private data class UiRoute(val screen: Screen, val detailId: Long? = null)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val vm: FeedViewModel = viewModel()
    val prefs = remember { Prefs(context) }
    var loggedIn by remember { mutableStateOf(prefs.cookies.isNotBlank()) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Kept here (not in FeedScreen) so the feed keeps its scroll position while a post is open.
    val feedListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val route = when {
        !loggedIn || vm.sessionExpired -> UiRoute(Screen.LOGIN)
        detailId != null -> UiRoute(Screen.DETAIL, detailId)
        else -> UiRoute(Screen.FEED)
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val openingPost = targetState.screen == Screen.DETAIL && initialState.screen == Screen.FEED
            val closingPost = initialState.screen == Screen.DETAIL && targetState.screen == Screen.FEED
            when {
                openingPost -> (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 12 } + fadeOut(tween(160)))

                closingPost -> (slideInHorizontally(tween(260)) { -it / 12 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(160)))

                else -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            }
        },
        label = "screen",
    ) { current ->
        when (current.screen) {
            Screen.LOGIN -> LoginScreen(
                onLoggedIn = { cookies, user ->
                    vm.acknowledgeExpired()
                    vm.onLoggedIn(cookies, user)
                    loggedIn = true
                },
            )

            Screen.DETAIL -> {
                BackHandler { detailId = null }
                DetailScreen(id = current.detailId ?: 0L, onBack = { detailId = null })
            }

            Screen.FEED -> FeedScreen(
                vm = vm,
                listState = feedListState,
                onOpenSubmission = { detailId = it },
                onLogout = {
                    runCatching {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                    vm.logout()
                    vm.acknowledgeExpired()
                    loggedIn = false
                },
            )
        }
    }
}