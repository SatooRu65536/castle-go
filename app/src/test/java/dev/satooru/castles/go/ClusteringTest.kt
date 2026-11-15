package dev.satooru.castles.go

import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.ui.map.MapItem
import dev.satooru.castles.go.ui.map.clusterCastles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusteringTest {
    private fun castle(id: String, scale: Int, lat: Double = 35.0, lng: Double = 136.0) = CastleEntity(
        id = id, currentRevisionNumber = 1, name = id, nameKana = "", searchText = "", lat = lat, lng = lng,
        scale = scale, castleKind = null, locationType = null, prefecture = null, tenshuStatus = null,
        meijoSelection = null, meijoNumber = null, primaryPhotoId = null, updatedAt = 0, deletedAt = null,
    )

    /** 城の ID ごとに，画面上の位置（px）を与える． */
    private fun cluster(castles: List<CastleEntity>, at: Map<String, Pair<Float, Float>>, keep: String? = null) =
        clusterCastles(castles, position = { at[it.id] }, radiusPx = { 10f }, keepSingleId = keep)

    @Test
    fun overlappingCastlesAreMergedIntoOneClusterWithCount() {
        val items = cluster(
            listOf(castle("a", 3, lat = 35.0), castle("b", 2, lat = 36.0), castle("c", 1, lat = 37.0)),
            mapOf("a" to (100f to 100f), "b" to (110f to 100f), "c" to (115f to 105f)),
        )
        val cluster = items.single() as MapItem.Cluster
        assertEquals(3, cluster.count)
        assertEquals(36.0, cluster.lat, 1e-9) // 3 件の平均
    }

    @Test
    fun castlesThatDoNotOverlapStaySeparate() {
        val items = cluster(
            listOf(castle("a", 3), castle("b", 2)),
            mapOf("a" to (0f to 0f), "b" to (100f to 0f)),
        )
        assertEquals(2, items.size)
        assertTrue(items.all { it is MapItem.Single })
    }

    @Test
    fun touchingExactlyAtSumOfRadiiIsNotMerged() {
        val items = cluster(listOf(castle("a", 3), castle("b", 2)), mapOf("a" to (0f to 0f), "b" to (20f to 0f)))
        assertEquals(2, items.size)
    }

    @Test
    fun largerScaleCastlesAreGroupedFirstAndSelectedCastleStaysSingle() {
        val at = mapOf("a" to (0f to 0f), "b" to (5f to 0f), "c" to (8f to 0f))
        val items = cluster(listOf(castle("a", 1), castle("b", 5), castle("c", 2)), at, keep = "b")
        // b は選択中なので単独．a と c は重なるのでまとめる．
        assertEquals(2, items.size)
        assertTrue(items.any { it is MapItem.Single && it.castle.id == "b" })
        assertEquals(2, items.filterIsInstance<MapItem.Cluster>().single().count)
    }

    @Test
    fun withoutScreenPositionsEveryCastleIsSingle() {
        val items = clusterCastles(listOf(castle("a", 3), castle("b", 2)), position = { null }, radiusPx = { 10f })
        assertEquals(2, items.size)
        assertTrue(items.all { it is MapItem.Single })
    }
}
