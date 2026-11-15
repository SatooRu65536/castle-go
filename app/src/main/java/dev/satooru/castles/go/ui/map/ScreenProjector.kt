package dev.satooru.castles.go.ui.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * 緯度経度を画面の位置（px）に変換する．
 *
 * 地図に傾き（tilt）がなければ，画面の位置は，メルカトル図法の世界座標（0〜1）からの 1 次変換
 * （拡大・回転・平行移動）になる．実際の地図の変換を 3 点だけ呼んで，この 1 次変換を求めておけば，
 * 城が何千件あっても，1 件ごとに地図の変換を呼ばずに画面の位置を計算できる．
 */
class ScreenProjector private constructor(
    private val m00: Double, private val m01: Double,
    private val m10: Double, private val m11: Double,
    private val tx: Double, private val ty: Double,
) {
    fun toScreen(lat: Double, lng: Double): Pair<Float, Float> {
        val (x, y) = world(lat, lng)
        return (m00 * x + m01 * y + tx).toFloat() to (m10 * x + m11 * y + ty).toFloat()
    }

    companion object {
        /** メルカトル図法の世界座標（左上が (0, 0)，右下が (1, 1)） */
        fun world(lat: Double, lng: Double): Pair<Double, Double> {
            val x = (lng + 180.0) / 360.0
            val y = 0.5 - ln(tan(PI / 4 + Math.toRadians(lat) / 2)) / (2 * PI)
            return x to y
        }

        private fun latLng(x: Double, y: Double): Pair<Double, Double> {
            val lat = Math.toDegrees(2 * atan(exp(PI * (1 - 2 * y))) - PI / 2)
            return lat to (x * 360.0 - 180.0)
        }

        /**
         * 中心とその周りの 3 点の変換から，1 次変換を求める．傾きのある地図では正しくない．
         * @param project 実際の地図の変換（緯度，経度 → 画面の位置 px）
         */
        fun fromSamples(
            centerLat: Double,
            centerLng: Double,
            zoom: Float,
            density: Float,
            project: (lat: Double, lng: Double) -> Pair<Double, Double>,
        ): ScreenProjector? {
            // 標本の間隔は，画面上で約 600 px になる世界座標の差（整数の px に丸められても，誤差が小さくなるように）．
            val worldPx = 256.0 * 2.0.pow(zoom.toDouble()) * density
            val step = 600.0 / worldPx
            val (x0, y0) = world(centerLat, centerLng)
            val (lat1, lng1) = latLng(x0 + step, y0)
            val (lat2, lng2) = latLng(x0, y0 + step)
            val (sx0, sy0) = project(centerLat, centerLng)
            val (sx1, sy1) = project(lat1, lng1)
            val (sx2, sy2) = project(lat2, lng2)
            // 世界座標の差 (step, 0)，(0, step) が，画面でどう動くか．
            val m00 = (sx1 - sx0) / step
            val m10 = (sy1 - sy0) / step
            val m01 = (sx2 - sx0) / step
            val m11 = (sy2 - sy0) / step
            if (listOf(m00, m01, m10, m11).any { it.isNaN() || it.isInfinite() }) return null
            return ScreenProjector(m00, m01, m10, m11, sx0 - (m00 * x0 + m01 * y0), sy0 - (m10 * x0 + m11 * y0))
        }
    }
}
