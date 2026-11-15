package dev.satooru.castles.go.settings

/** 移動手段．周辺の半径の既定値と位置情報の更新間隔に反映する． */
enum class TravelMode(
    val label: String,
    val defaultRadiusKm: Double,
    val locationIntervalMillis: Long,
    /** Google マップの URL の `travelmode` */
    val urlParam: String,
) {
    DRIVING("車", 50.0, 15_000, "driving"),
    TRANSIT("公共交通", 50.0, 15_000, "transit"),
    BICYCLING("自転車", 10.0, 8_000, "bicycling"),
    WALKING("徒歩", 2.0, 4_000, "walking"),
}

/** 周辺の表示の方式．Android Auto のみで使う（スマートフォンの地図は画面内の城をすべて出す）． */
enum class NearbyMode(val label: String) {
    TOP_N("件数（上位 N 件）"),
    RADIUS("半径（N km 以内）"),
}

data class AppSettings(
    val travelMode: TravelMode = TravelMode.DRIVING,
    val nearbyMode: NearbyMode = NearbyMode.TOP_N,
    /** [NearbyMode.TOP_N] の N */
    val topN: Int = 20,
    /** [NearbyMode.RADIUS] の N．NULL のときは移動手段の既定値 */
    val radiusKm: Double? = null,
    /** 車載の近づいた通知 */
    val carNotifyEnabled: Boolean = true,
    val carNotifyDistanceKm: Double = 1.0,
    val carNotifyMinScale: Int = 4,
    val carNotifyUnvisitedOnly: Boolean = false,
    /** アプリを閉じている間も通知する（Geofencing）．背景の位置情報の権限（常に許可）が必要． */
    val backgroundNotifyEnabled: Boolean = false,
    /** 地図に表示する規模の集合（[dev.satooru.castles.go.data.local.ScaleMask]）．既定はすべて */
    val visibleScaleMask: Int = dev.satooru.castles.go.data.local.ScaleMask.ALL,
) {
    val effectiveRadiusKm: Double get() = radiusKm ?: travelMode.defaultRadiusKm
}
