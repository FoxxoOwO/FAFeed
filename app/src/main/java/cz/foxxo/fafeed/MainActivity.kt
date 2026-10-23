package cz.foxxo.fafeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cz.foxxo.fafeed.ui.AppRoot
import cz.foxxo.fafeed.ui.theme.FaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaTheme {
                AppRoot()
            }
        }
    }
}
