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
 * Sign-in runs entirely inside the WebView, because `www.furaffinity.net` is protected by a
 * Cloudflare challenge that OkHttp cannot solve. FurAffinity also moved the login form to
 * `login.furaffinity.net` (Django SSO), so the success state is read from the rendered page
 * instead of from the URL, and cookies of both hosts are merged into one header.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: (cookies: String, user: String, userAgent: String) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var checking by remember { mutableStateOf(false) }

    val notYetMsg = stringResource(R.string.login_not_yet)
    val verifyFailedMsg = stringResource(R.string.login_verify_failed)
    val challengeMsg = stringResource(R.string.login_challenge)
    val blockedMsg = stringResource(R.string.login_blocked)

    fun readCookies(): String {
        val manager = CookieManager.getInstance()
        runCatching { manager.flush() }
        val www = runCatching { manager.getCookie(FaParser.BASE) }.getOrNull()
        val login = runCatching { manager.getCookie(FaCookies.LOGIN_HOST) }.getOrNull()
        return FaCookies.merge(listOf(www, login))
    }

    fun accept(cookies: String, user: String) {
        val agent = webView?.settings?.userAgentString.orEmpty().ifBlank { FA_USER_AGENT }
        onLoggedIn(cookies, user, agent)
    }

    /** Last resort: ask OkHttp, reusing the WebView's UA so the Cloudflare clearance stays valid. */
    fun verifyWithOkHttp(cookies: String) {
        if (busy) return
        if (!FaCookies.hasSession(cookies)) {
            message = notYetMsg
            return
        }
        busy = true
        message = null
        scope.launch {
            val agent = webView?.settings?.userAgentString.orEmpty().ifBlank { FA_USER_AGENT }
            try {
                val user = FaClient(userAgentProvider = { agent }) { cookies }.verify(cookies)
                if (user != null) {
                    accept(cookies, user)
                } else {
                    busy = false
                    message = verifyFailedMsg
                }
            } catch (_: FaBlockedException) {
                busy = false
                message = blockedMsg
            } catch (_: Exception) {
                busy = false
                message = verifyFailedMsg
            }
        }
    }

    fun submit() {
        if (busy) return
        val view = webView
        val cookies = readCookies()
        if (view == null) {
            verifyWithOkHttp(cookies)
            return
        }
        view.evaluateJavascript(FA_DOM_PROBE) { raw ->
            when (val result = domResult(raw)) {
                DOM_CHALLENGE -> message = challengeMsg
                DOM_FORM -> message = notYetMsg
                else -> if (result.startsWith(DOM_USER)) {
                    val name = result.removePrefix(DOM_USER).trim()
                    if (name.isNotEmpty()) {
                        busy = true
                        message = null
                        accept(cookies, name)
                    } else {
                        verifyWithOkHttp(cookies)
                    }
                } else {
                    verifyWithOkHttp(cookies)
                }
            }
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
    BackHandler(enabled = webView?.canGoBack() != true && onCancel != null) {
        onCancel?.invoke()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.login_subtitle),
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
                text = { Text(stringResource(if (busy) R.string.verifying else R.string.im_signed_in)) },
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
                        // Whatever UA this WebView reports is the one OkHttp will send afterwards.
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                webView = view
                                if (busy || checking) return
                                checking = true
                                view.evaluateJavascript(FA_DOM_PROBE) { raw ->
                                    checking = false
                                    if (busy) return@evaluateJavascript
                                    val result = domResult(raw)
                                    if (result.startsWith(DOM_USER)) {
                                        val name = result.removePrefix(DOM_USER).trim()
                                        val cookies = readCookies()
                                        if (name.isNotEmpty() && cookies.isNotBlank()) {
                                            busy = true
                                            message = null
                                            accept(cookies, name)
                                        }
                                    }
                                }
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
                                message = blockedMsg
                            }
                        }
                        // Start on www so the Cloudflare clearance for the host we scrape gets issued.
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
