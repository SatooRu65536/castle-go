package dev.satooru.castles.go

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.satooru.castles.go.ui.CastleGoNavHost
import dev.satooru.castles.go.ui.theme.CastleGoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CastleGoTheme {
                CastleGoNavHost()
            }
        }
    }
}
