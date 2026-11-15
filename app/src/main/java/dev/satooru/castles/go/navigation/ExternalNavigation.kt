package dev.satooru.castles.go.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.satooru.castles.go.settings.TravelMode

/** 外部のナビアプリへ目的地と移動手段を引き継ぐ．ルート計算と音声案内は外部アプリに任せる． */
object ExternalNavigation {
    private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /** Google マップの URL（Maps URLs）．`travelmode` に移動手段を渡す． */
    fun uri(lat: Double, lng: Double, mode: TravelMode): Uri =
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=${mode.urlParam}")

    /** Google マップのアプリを優先して開く．アプリがなければ，URL を扱える他のアプリ（ブラウザーなど）で開く． */
    fun start(context: Context, lat: Double, lng: Double, mode: TravelMode) {
        val uri = uri(lat, lng, mode)
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent(intent).setPackage(GOOGLE_MAPS_PACKAGE))
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "ナビを開けるアプリが見つかりません", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
