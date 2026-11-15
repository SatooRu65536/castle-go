package dev.satooru.castles.go.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

object CarIntents {
    private const val SCHEME = "castlego"
    private const val HOST = "castle"

    /** 通知から城の基本情報の画面を開く Intent． */
    fun openCastle(context: Context, castleId: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(context, CastleCarAppService::class.java))
            .setData(Uri.parse("$SCHEME://$HOST/$castleId"))

    fun castleId(intent: Intent): String? {
        val data = intent.data ?: return null
        return if (data.scheme == SCHEME && data.host == HOST) data.lastPathSegment else null
    }
}
