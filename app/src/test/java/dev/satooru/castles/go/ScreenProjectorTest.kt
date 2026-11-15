package dev.satooru.castles.go

import dev.satooru.castles.go.ui.map.ScreenProjector
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class ScreenProjectorTest {
    /** 回転・拡大・平行移動を持つ，傾きのない地図の変換（実際の地図の変換の代わり）． */
    private fun fakeMap(zoom: Float, density: Float, bearingDeg: Double, centerLat: Double, centerLng: Double): (Double, Double) -> Pair<Double, Double> {
        val scale = 256.0 * 2.0.pow(zoom.toDouble()) * density
        val theta = Math.toRadians(bearingDeg)
        val (cx, cy) = ScreenProjector.world(centerLat, centerLng)
        return { lat, lng ->
            val (x, y) = ScreenProjector.world(lat, lng)
            val dx = (x - cx) * scale
            val dy = (y - cy) * scale
            // 画面の中心を (540, 1200) とし，bearing だけ回す．
            (540 + dx * cos(theta) - dy * sin(theta)) to (1200 + dx * sin(theta) + dy * cos(theta))
        }
    }

    @Test
    fun linearTransformMatchesTheRealProjectionAtOtherPoints() {
        for (bearing in listOf(0.0, 35.0, -120.0)) {
            for (zoom in listOf(5f, 10f, 14f)) {
                val project = fakeMap(zoom, 2.75f, bearing, 35.18, 136.9)
                val projector = ScreenProjector.fromSamples(35.18, 136.9, zoom, 2.75f, project)!!
                // 画面の範囲に入る点で比べる．
                val offset = 300.0 / (256.0 * 2.0.pow(zoom.toDouble()) * 2.75) // 世界座標で約 300 px
                for ((dLat, dLng) in listOf(0.0 to 0.0, offset * 90 to -offset * 90, -offset * 60 to offset * 120)) {
                    val lat = 35.18 + dLat
                    val lng = 136.9 + dLng
                    val expected = project(lat, lng)
                    val actual = projector.toScreen(lat, lng)
                    assertEquals("bearing=$bearing zoom=$zoom x", expected.first, actual.first.toDouble(), 0.5)
                    assertEquals("bearing=$bearing zoom=$zoom y", expected.second, actual.second.toDouble(), 0.5)
                }
            }
        }
    }
}
