package dev.satooru.castles.go

import dev.satooru.castles.go.data.repository.Direction
import dev.satooru.castles.go.data.repository.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionTest {
    private val origin = GeoPoint(35.0, 135.0)

    @Test
    fun 八方角() {
        assertEquals("北", Direction.compass(Direction.bearing(origin, GeoPoint(36.0, 135.0))))
        assertEquals("東", Direction.compass(Direction.bearing(origin, GeoPoint(35.0, 136.0))))
        assertEquals("南", Direction.compass(Direction.bearing(origin, GeoPoint(34.0, 135.0))))
        assertEquals("西", Direction.compass(Direction.bearing(origin, GeoPoint(35.0, 134.0))))
        assertEquals("北東", Direction.compass(Direction.bearing(origin, GeoPoint(36.0, 136.0))))
        assertEquals("北", Direction.compass(359.0))
    }

    @Test
    fun 進行方向から見た向き() {
        // 東へ進んでいるとき
        assertEquals("前方", Direction.relative(90.0, 90.0))
        assertEquals("右", Direction.relative(180.0, 90.0))
        assertEquals("左", Direction.relative(0.0, 90.0))
        assertEquals("後方", Direction.relative(270.0, 90.0))
        assertEquals("右前方", Direction.relative(135.0, 90.0))
        assertEquals("左後方", Direction.relative(225.0, 0.0))
        // 北（0 度をまたぐ）
        assertEquals("左前方", Direction.relative(350.0, 30.0))
    }
}
