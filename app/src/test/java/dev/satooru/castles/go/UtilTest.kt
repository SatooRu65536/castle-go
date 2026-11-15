package dev.satooru.castles.go

import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.ui.common.Labels
import dev.satooru.castles.go.ui.detail.attributeText
import dev.satooru.castles.go.ui.map.MapViewModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {
    @Test
    fun distanceBetweenNagoyaAndTokyoIsAbout260km() {
        val nagoya = GeoPoint(35.185, 136.899)
        val tokyo = GeoPoint(35.681, 139.767)
        val km = DistanceCalculator.meters(nagoya, tokyo) / 1000
        assertTrue("km=$km", km in 255.0..270.0)
    }

    @Test
    fun distanceToSelfIsZero() {
        val p = GeoPoint(35.0, 135.0)
        assertEquals(0.0, DistanceCalculator.meters(p, p), 0.001)
    }

    @Test
    fun labelsTreatUnknownValuesAsOther() {
        assertEquals("愛知県", Labels.prefecture("23"))
        assertEquals("その他", Labels.castleKind("future_kind"))
        assertNull(Labels.tenshuStatus(null))
        assertEquals(47, Labels.prefectures.size)
    }

    @Test
    fun distanceFormat() {
        assertEquals("350 m", Labels.distance(350.4))
        assertEquals("1.2 km", Labels.distance(1234.0))
    }

    @Test
    fun attributeTextHandlesKnownUnknownAndYear() {
        val json = Json.parseToJsonElement(
            """{"a":{"state":"known","value":"石垣"},"b":{"state":"unknown"},""" +
                """"c":{"state":"known","value":{"year":1612,"precision":"year","label":"1612年"}},""" +
                """"d":{"state":"known","value":["x","y"]}}""",
        ) as kotlinx.serialization.json.JsonObject
        assertEquals("石垣", attributeText(json["a"]!!))
        assertEquals("不明", attributeText(json["b"]!!))
        assertEquals("1612年", attributeText(json["c"]!!))
        assertEquals("x，y", attributeText(json["d"]!!))
    }

    @Test
    fun minScaleGrowsAsZoomIn() {
        assertEquals(6, MapViewModel.minScaleFor(5f))
        assertEquals(2, MapViewModel.minScaleFor(14f))
        assertEquals(1, MapViewModel.minScaleFor(15f))
    }

    @Test
    fun notifyDistanceInputIsRoundedToOneDecimalAndLimitedTo10km() {
        assertEquals(1.5, dev.satooru.castles.go.ui.settings.parseDistanceKm("1.5")!!, 0.0)
        assertEquals(0.1, dev.satooru.castles.go.ui.settings.parseDistanceKm("0.1")!!, 0.0)
        assertEquals(10.0, dev.satooru.castles.go.ui.settings.parseDistanceKm("10")!!, 0.0)
        assertEquals(2.3, dev.satooru.castles.go.ui.settings.parseDistanceKm("2.34")!!, 0.0)
        assertNull(dev.satooru.castles.go.ui.settings.parseDistanceKm("10.1"))
        assertNull(dev.satooru.castles.go.ui.settings.parseDistanceKm("0"))
        assertNull(dev.satooru.castles.go.ui.settings.parseDistanceKm("abc"))
        assertNull(dev.satooru.castles.go.ui.settings.parseDistanceKm(""))
    }

    @Test
    fun navigationUrlOmitsTravelModeWhenNotSpecified() {
        val nav = dev.satooru.castles.go.navigation.ExternalNavigation
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=35.185,136.899",
            nav.url(35.185, 136.899),
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=35.185,136.899&travelmode=walking",
            nav.url(35.185, 136.899, dev.satooru.castles.go.settings.TravelMode.WALKING),
        )
    }
}
