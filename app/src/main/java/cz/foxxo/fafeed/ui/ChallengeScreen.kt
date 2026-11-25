package cz.foxxo.fafeed.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cz.foxxo.fafeed.R
import cz.foxxo.fafeed.data.FA_USER_AGENT
import cz.foxxo.fafeed.data.FaBlockedException
import cz.foxxo.fafeed.data.FaClient
import cz.foxxo.fafeed.data.FaCookies
import cz.foxxo.fafeed.data.FaParser
import kotlinx.coroutines.launch

/**
 * Lets the user complete a Cloudflare challenge in a real WebView (no bot-detection
 * evasion). Once the page is usable again, the merged cookies and the WebView's UA are
 * handed back so OkHttp can continue syncing with the same clearance.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    onResolved: (cookies: String, userAgent: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val waitMsg = stringResource(R.string.challenge_wait)
    val failedMsg = stringResource(R.string.challenge_failed)

    fun readCookies(): String {
        val manager = CookieManager.getInstance()
        runCatching { manager.flush() }
        return FaCookies.merge(
            listOf(
                runCatching { manager.getCookie(FaParser.BASE) }.getOrNull(),
                runCatching { manager.getCookie(FaCookies.LOGIN_HOST) }.getOrNull(),
            ),
        )
    }

    fun submit() {
        if (busy) return
        val view = webView
        if (view == null) {
            message = failedMsg
            return
        }
        val cookies = readCookies()
        if (!FaCookies.hasSession(cookies)) {
            message = failedMsg
            return
        }
        val agent = view.settings.userAgentString.orEmpty().ifBlank { FA_USER_AGENT }
        busy = true
        message = null
        view.evaluateJavascript(FA_DOM_PROBE) { raw ->
            if (domResult(raw) == DOM_CHALLENGE) {
                busy = false
                message = waitMsg
                return@evaluateJavascript
            }
            scope.launch {
                try {
                    val user = FaClient(userAgentProvider = { agent }) { cookies }.verify(cookies)
                    if (user != null) {
                        onResolved(cookies, agent)
                    } else {
                        busy = false
                        message = failedMsg
                    }
                } catch (_: FaBlockedException) {
                    busy = false
                    message = waitMsg
                } catch (_: Exception) {
                    busy = false
                    message = failedMsg
                }
            }
        }
    }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onCancel()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.challenge_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.challenge_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { submit() },
                text = {
                    Text(stringResource(if (busy) R.string.challenge_verifying else R.string.challenge_continue))
                },
                icon = {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                },
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                webView = view
                                message = null
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val scheme = request.url.scheme.orEmpty().lowercase()
                                if (scheme == "http" || scheme == "https") return false
                                runCatching { openInBrowser(context, request.url.toString()) }
                                return true
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (!request.isForMainFrame) return
                                message = failedMsg
                            }
                        }
                        loadUrl(FaParser.BASE + "/")
                        webView = this
                    }
                },
            )

            message?.let { text ->
                Text(
                    text,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 84.dp),
                )
            }

            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
