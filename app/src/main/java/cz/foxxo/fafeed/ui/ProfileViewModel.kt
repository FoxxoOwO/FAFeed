package cz.foxxo.fafeed.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.foxxo.fafeed.R
import cz.foxxo.fafeed.data.FaAuthException
import cz.foxxo.fafeed.data.FaClient
import cz.foxxo.fafeed.data.FaParser
import cz.foxxo.fafeed.data.FeedItem
import cz.foxxo.fafeed.data.Prefs
import kotlinx.coroutines.launch

data class ProfileState(
    val items: List<FeedItem> = emptyList(),
    val nextUrl: String? = null,
    val loading: Boolean = true,
    val moreLoading: Boolean = false,
    val error: String? = null,
    val loadedUser: String = "",
)

/** Full gallery of one watched account, loaded page by page. */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val client = FaClient(userAgentProvider = { prefs.effectiveUserAgent }) { prefs.cookies }

    var state by mutableStateOf(ProfileState())
        private set

    fun load(user: String) {
        if (user.isBlank() || (state.loadedUser == user && state.error == null)) return
        state = ProfileState(loadedUser = user)
        viewModelScope.launch {
            val page = runCatching { client.feed(galleryUrl(user)) }
                .getOrElse { fail(it); return@launch }
            if (page.loginRequired) {
                fail(FaAuthException())
                return@launch
            }
            state = ProfileState(
                items = page.items.distinctBy { it.id },
                nextUrl = page.nextUrl,
                loading = false,
                loadedUser = user,
            )
        }
    }

    fun loadMore() {
        val url = state.nextUrl
        if (url.isNullOrBlank() || state.moreLoading || state.error != null) return
        state = state.copy(moreLoading = true)
        viewModelScope.launch {
            val page = runCatching { client.feed(url) }
                .getOrElse { fail(it); return@launch }
            if (page.loginRequired) {
                fail(FaAuthException())
                return@launch
            }
            state = state.copy(
                items = (state.items + page.items).distinctBy { it.id },
                nextUrl = page.nextUrl,
                moreLoading = false,
            )
        }
    }

    fun retry() {
        val user = state.loadedUser
        state = ProfileState()
        load(user)
    }

    private fun fail(error: Throwable) {
        state = state.copy(
            loading = false,
            moreLoading = false,
            error = when (error) {
                is FaAuthException -> getApplication<Application>().getString(R.string.session_expired)
                else -> error.message ?: getApplication<Application>().getString(R.string.load_failed)
            },
        )
    }

    companion object {
        fun galleryUrl(user: String): String = "${FaParser.BASE}/gallery/$user/"
    }
}
