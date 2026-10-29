package cz.foxxo.fafeed.ui

import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.foxxo.fafeed.data.Prefs

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val vm: FeedViewModel = viewModel()
    val prefs = remember { Prefs(context) }
    var loggedIn by remember { mutableStateOf(prefs.cookies.isNotBlank()) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Kept here (not in FeedScreen) so the feed keeps its scroll position while a post is open.
    val feedListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    when {
        !loggedIn || vm.sessionExpired -> LoginScreen(
            onLoggedIn = { cookies, user ->
                vm.acknowledgeExpired()
                vm.onLoggedIn(cookies, user)
                loggedIn = true
            },
        )

        detailId != null -> {
            BackHandler { detailId = null }
            DetailScreen(id = detailId!!, onBack = { detailId = null })
        }

        else -> FeedScreen(
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
