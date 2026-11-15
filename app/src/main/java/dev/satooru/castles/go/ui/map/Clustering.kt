package dev.satooru.castles.go.ui.map

import dev.satooru.castles.go.data.local.CastleEntity
import kotlin.math.hypot

/** 地図に出すもの．1 つの城のピンか，重なった複数の城をまとめたもの． */
sealed interface MapItem {
    data class Single(val castle: CastleEntity) : MapItem

    /** 重なった城をまとめたもの．位置は，まとめた城の位置の平均． */
    data class Cluster(val lat: Double, val lng: Double, val members: List<CastleEntity>) : MapItem {
        val count: Int get() = members.size

        /** 表示の識別用．まとめる城が変わったら変わる． */
        val key: String get() = "cluster-${members.first().id}-$count"
    }
}

/**
 * 画面上でピンが重なる城を，1 つにまとめる．
 *
 * 規模の大きい城から順に，まだどこにもまとめていない城のうち，ピンの円が重なるもの
 * （画面上の中心間の距離が，2 つの半径の和より小さいもの）を同じグループにする．
 * 2 件以上のグループはまとめ，1 件だけのグループは単独のピンにする．
 *
 * @param position 城の画面上の位置（px）．地図がまだ使えないなどで NULL のときは，まとめずに単独のピンにする
 * @param radiusPx 城のピンの円の半径（px，縁を含む）
 * @param keepSingleId 常に単独のピンにする城の ID（選択中の城）
 */
fun clusterCastles(
    castles: List<CastleEntity>,
    position: (CastleEntity) -> Pair<Float, Float>?,
    radiusPx: (CastleEntity) -> Float,
    keepSingleId: String? = null,
): List<MapItem> {
    val sorted = castles.sortedWith(compareByDescending<CastleEntity> { it.scale }.thenBy { it.id })
    val points = sorted.map { position(it) }
    if (points.any { it == null }) return sorted.map { MapItem.Single(it) }
    val xy = points.map { it!! }
    val radii = sorted.map(radiusPx)
    val assigned = BooleanArray(sorted.size)
    // 画面を，最大の半径の 2 倍（重なりうる最大の距離）の升目に区切り，隣り合う升目の城だけを比べる．
    // すべての組を比べると，城が何千件もあるときに遅くなるため．
    val cell = (radii.maxOrNull() ?: 1f).coerceAtLeast(1f) * 2f
    val grid = HashMap<Long, MutableList<Int>>()
    fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)
    for (i in sorted.indices) {
        val cx = kotlin.math.floor(xy[i].first / cell).toInt()
        val cy = kotlin.math.floor(xy[i].second / cell).toInt()
        grid.getOrPut(cellKey(cx, cy)) { mutableListOf() } += i
    }
    val items = mutableListOf<MapItem>()
    for (i in sorted.indices) {
        if (assigned[i]) continue
        assigned[i] = true
        if (sorted[i].id == keepSingleId) {
            items += MapItem.Single(sorted[i])
            continue
        }
        val cx = kotlin.math.floor(xy[i].first / cell).toInt()
        val cy = kotlin.math.floor(xy[i].second / cell).toInt()
        val near = mutableListOf<Int>()
        for (dx in -1..1) for (dy in -1..1) grid[cellKey(cx + dx, cy + dy)]?.let { near += it }
        near.sort()
        val group = mutableListOf(sorted[i])
        for (j in near) {
            if (j <= i || assigned[j] || sorted[j].id == keepSingleId) continue
            val distance = hypot(xy[i].first - xy[j].first, xy[i].second - xy[j].second)
            if (distance < radii[i] + radii[j]) {
                assigned[j] = true
                group += sorted[j]
            }
        }
        items += if (group.size == 1) {
            MapItem.Single(group.first())
        } else {
            MapItem.Cluster(group.map { it.lat }.average(), group.map { it.lng }.average(), group)
        }
    }
    return items
}
