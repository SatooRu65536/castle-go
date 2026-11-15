package dev.satooru.castles.go.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.data.repository.NearbyCastle
import dev.satooru.castles.go.settings.NearbyMode
import dev.satooru.castles.go.ui.common.Labels
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 絞り込みの切り替え．車載では未訪問の名城のみ（100名城・続100名城）に絞れる． */
private enum class CarFilterMode(val label: String) {
    ALL("すべて"),
    TOP100("未訪問の100名城"),
    CONTINUED("未訪問の続100名城");

    fun next() = entries[(ordinal + 1) % entries.size]

    fun toFilter() = when (this) {
        ALL -> CastleFilter()
        TOP100 -> CastleFilter(meijoSelection = "top100", unvisitedOnly = true)
        CONTINUED -> CastleFilter(meijoSelection = "continued_top100", unvisitedOnly = true)
    }
}

/**
 * 最初の画面．現在地と周辺の城を [PlaceListMapTemplate] で表示する．
 * 設定の「周辺の表示」（上位 N 件，または半径 N km 以内）に従い，Room のみを参照する．
 */
class NearbyCarScreen(carContext: CarContext, private val container: AppContainer) : Screen(carContext) {
    private var mode = CarFilterMode.ALL
    private var origin: GeoPoint? = null
    private var items: List<NearbyCastle> = emptyList()
    private var loadJob: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                // 表示中（STARTED）のみ現在地と設定を監視する．STOPPED で購読を止める．
                owner.lifecycleScope.launch {
                    owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        combine(container.locationRepository.location, container.settingsRepository.settings) { l, s -> l to s }
                            .collect { reload() }
                    }
                }
            }
        })
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val here = container.locationRepository.location.value
            origin = here
            val settings = container.settingsRepository.settings.value
            if (here == null) {
                items = emptyList()
            } else {
                val limit = minOf(
                    if (settings.nearbyMode == NearbyMode.TOP_N) settings.topN else MAX_ROWS,
                    contentLimit(),
                )
                items = container.castleRepository.nearby(
                    origin = here,
                    filter = mode.toFilter(),
                    limit = limit,
                    radiusKm = if (settings.nearbyMode == NearbyMode.RADIUS) settings.effectiveRadiusKm else null,
                )
            }
            invalidate()
        }
    }

    private fun contentLimit(): Int =
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)

    override fun onGetTemplate(): Template {
        val builder = PlaceListMapTemplate.Builder()
            .setTitle("周辺の城")
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(true)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(mode.label)
                            .setOnClickListener { mode = mode.next(); reload() }
                            .build(),
                    )
                    .build(),
            )
            .setOnContentRefreshListener { reload() }
        origin?.let {
            builder.setAnchor(Place.Builder(CarLocation.create(it.lat, it.lng)).build())
        }
        if (origin == null) {
            if (container.locationRepository.hasPermission()) builder.setLoading(true)
            else builder.setItemList(
                ItemList.Builder().setNoItemsMessage("スマートフォンで位置情報を許可してください").build(),
            )
            return builder.build()
        }
        val list = ItemList.Builder().setNoItemsMessage("周辺に城がありません")
        items.forEach { list.addItem(row(it)) }
        return builder.setItemList(list.build()).build()
    }

    private fun row(n: NearbyCastle): Row {
        val c = n.castle
        return Row.Builder()
            .setTitle(c.name)
            .addText(
                listOfNotNull(
                    Labels.distance(n.distanceMeters),
                    Labels.prefecture(c.prefecture),
                    Labels.tenshuStatus(c.tenshuStatus)?.let { "天守: $it" },
                ).joinToString(" ・ "),
            )
            .setMetadata(
                Metadata.Builder()
                    .setPlace(
                        Place.Builder(CarLocation.create(c.lat, c.lng))
                            .setMarker(PlaceMarker.Builder().build())
                            .build(),
                    )
                    .build(),
            )
            .setOnClickListener {
                screenManager.push(CastleCarDetailScreen(carContext, container, c.id))
            }
            .build()
    }

    private companion object {
        const val MAX_ROWS = 100
    }
}
