package dev.satooru.castles.go

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.satooru.castles.go.ui.CastleGoNavHost
import dev.satooru.castles.go.ui.theme.CastleGoTheme

class MainActivity : ComponentActivity() {
    /** 通知から開かれたときに表示する城の ID */
    private var openCastleId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openCastleId = intent?.getStringExtra(EXTRA_CASTLE_ID)
        setContent {
            CastleGoTheme {
                CastleGoNavHost(openCastleId = openCastleId, onCastleOpened = { openCastleId = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openCastleId = intent.getStringExtra(EXTRA_CASTLE_ID)
    }

    companion object {
        const val EXTRA_CASTLE_ID = "castleId"
    }
}
