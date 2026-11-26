package cz.foxxo.fafeed

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.foxxo.fafeed.ui.AppRoot

class MainActivity : ComponentActivity() {
    private var deepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink = intent.data?.toString()
        setContent {
            AppRoot(
                deepLink = deepLink,
                onDeepLinkHandled = { deepLink = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink = intent.data?.toString()
    }
}
