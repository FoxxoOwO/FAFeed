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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.foxxo.fafeed.data.AccountCredentials
import cz.foxxo.fafeed.data.Accounts
import cz.foxxo.fafeed.data.Prefs
import cz.foxxo.fafeed.ui.theme.FaTheme

private enum class Screen { LOGIN, FEED, DETAIL, PROFILE, CHALLENGE }

private data class UiRoute(
    val screen: Screen,
    val detailId: Long? = null,
    val profileUser: String? = null,
)

@Composable
fun AppRoot(
    deepLink: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: FeedViewModel = viewModel()
    val updateVm: UpdateViewModel = viewModel()
    LaunchedEffect(Unit) { updateVm.autoCheckIfDue() }
    val prefs = remember { Prefs(context) }
    val accounts = remember { Accounts(context) }
    accounts.ensureCurrent()
    var loggedIn by remember { mutableStateOf(prefs.cookies.isNotBlank()) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var profileUser by rememberSaveable { mutableStateOf<String?>(null) }
    var lang by remember { mutableStateOf(prefs.lang) }
    var theme by remember { mutableStateOf(prefs.theme) }
    var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }
    // Kept here (not in FeedScreen) so the feed keeps its scroll position while a post is open.
    val feedListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        when (val target = parseFaDeepLink(link)) {
            is FaDeepLink.Submission -> detailId = target.id
            is FaDeepLink.Profile -> {
                detailId = null
                profileUser = target.user
            }
            null -> Unit
        }
        onDeepLinkHandled()
    }

    fun switchAccount(user: String) {
        if (prefs.user.isNotBlank() && prefs.cookies.isNotBlank()) {
            accounts.save(AccountCredentials(prefs.user, prefs.cookies, prefs.userAgent))
        }
        val target = accounts.get(user) ?: return
        vm.logout()
        prefs.cookies = target.cookies
        prefs.user = target.user
        if (target.userAgent.isNotBlank()) prefs.userAgent = target.userAgent
        accounts.active = target.user
        vm.onLoggedIn(target.cookies, target.user, target.userAgent)
        profileUser = null
        detailId = null
        loggedIn = true
    }

    fun removeAccount(user: String) {
        accounts.remove(user)
        if (accounts.active.equals(user, ignoreCase = true)) {
            val next = accounts.list().firstOrNull()
            if (next != null) {
                switchAccount(next.user)
            } else {
                vm.logout()
                loggedIn = false
            }
        }
    }

    val route = when {
        !loggedIn || vm.sessionExpired -> UiRoute(Screen.LOGIN)
        vm.blocked -> UiRoute(Screen.CHALLENGE)
        detailId != null -> UiRoute(Screen.DETAIL, detailId)
        profileUser != null -> UiRoute(Screen.PROFILE, profileUser = profileUser)
        else -> UiRoute(Screen.FEED)
    }

    FaTheme(theme = theme, dynamicColor = dynamicColor) {
    AppLocale(lang) {
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = targetState.screen == Screen.DETAIL ||
                targetState.screen == Screen.PROFILE || targetState.screen == Screen.CHALLENGE
            val backward = initialState.screen == Screen.DETAIL ||
                initialState.screen == Screen.PROFILE || initialState.screen == Screen.CHALLENGE
            val openingPost = forward && !backward
            val closingPost = backward && !forward
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
                onLoggedIn = { cookies, user, userAgent ->
                    accounts.save(AccountCredentials(user, cookies, userAgent))
                    accounts.active = user
                    vm.acknowledgeExpired()
                    vm.onLoggedIn(cookies, user, userAgent)
                    profileUser = null
                    detailId = null
                    loggedIn = true
                },
            )

            Screen.DETAIL -> {
                BackHandler { detailId = null }
                DetailScreen(
                    id = current.detailId ?: 0L,
                    onBack = { detailId = null },
                    onOpenProfile = { user ->
                        detailId = null
                        profileUser = user
                    },
                )
            }

            Screen.CHALLENGE -> ChallengeScreen(
                onResolved = { cookies, agent -> vm.resolveChallenge(cookies, agent) },
                onCancel = { vm.acknowledgeBlocked() },
            )

            Screen.PROFILE -> {
                BackHandler { profileUser = null }
                ProfileScreen(
                    user = current.profileUser.orEmpty(),
                    onBack = { profileUser = null },
                    onOpenSubmission = { detailId = it },
                )
            }

            Screen.FEED -> FeedScreen(
                vm = vm,
                updateVm = updateVm,
                listState = feedListState,
                lang = lang,
                onSetLang = { prefs.lang = it; lang = it },
                theme = theme,
                onSetTheme = { prefs.theme = it; theme = it },
                dynamicColor = dynamicColor,
                onSetDynamicColor = { prefs.dynamicColor = it; dynamicColor = it },
                accounts = accounts.list(),
                activeAccount = accounts.active,
                onSwitchAccount = { switchAccount(it) },
                onRemoveAccount = { removeAccount(it) },
                onOpenSubmission = { detailId = it },
                onOpenProfile = { profileUser = it },
                onLogout = {
                    if (prefs.user.isNotBlank() && prefs.cookies.isNotBlank()) {
                        accounts.save(AccountCredentials(prefs.user, prefs.cookies, prefs.userAgent))
                    }
                    accounts.active = ""
                    runCatching {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                    vm.logout()
                    vm.acknowledgeExpired()
                    profileUser = null
                    detailId = null
                    loggedIn = false
                },
            )
        }
    }
    UpdateDialog(vm = updateVm)
    }
    }
}
