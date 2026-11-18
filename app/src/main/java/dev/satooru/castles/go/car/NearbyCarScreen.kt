package dev.satooru.castles.go.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import android.text.Spannable
import android.text.SpannableString
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
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
import dev.satooru.castles.go.data.repository.Direction
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.data.repository.NearbyCastle
import dev.satooru.castles.go.settings.NearbyMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 最初の画面．現在地と周辺の城を [PlaceListMapTemplate] で表示する．
 * 設定の「周辺の表示」（上位 N 件，または半径 N km 以内）に従い，Room のみを参照する．
 */
class NearbyCarScreen(
    carContext: CarContext,
    private val container: AppContainer,
    private val onOpenDetail: (castleId: String) -> Unit,
) : Screen(carContext) {
    /** 近づいた城の知らせ（城の ID と「名古屋城(規模 6) 1.5km」）．地図の上のボタンに [ALERT_MILLIS] だけ出す． */
    private var alert: Pair<String, String>? = null
    private var alertJob: Job? = null

    private var origin: GeoPoint? = null
    private var items: List<NearbyCastle> = emptyList()
    private var loadJob: Job? = null

    /** 最後に反映した行の城名．ホストの「テンプレートの更新」の判定（行数と各行の題が同じ）に使う． */
    private var sentTitles: List<String>? = null

    /** 行の構成が変わる更新に使った回数．ホストの枠（1 つの操作の流れで 5 枚）に収める． */
    private var structuralChanges = 0

    /** 通知から開き直されて，ホストの枠が戻ったとき． */
    fun resetQuota() {
        structuralChanges = 0
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                // 表示中（STARTED）のみ現在地と設定を監視する．STOPPED で購読を止める．
                owner.lifecycleScope.launch {
                    owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        combine(container.locationRepository.location, container.settingsRepository.settings) { l, s -> l to s }
                            .collect { reload(Trigger.AUTO) }
                    }
                }
            }
        })
    }

    private enum class Trigger { AUTO, REFRESH }

    /**
     * 周辺の城を読み直す．PlaceListMapTemplate は，行の構成が変わる更新を 1 枚と数え，
     * 5 枚目に置くとホストがエラーにして落ちる（[PlaceListMapTemplate] の「更新」の条件）．
     * そのため，位置の更新による構成の変更は使う回数を絞り，利用者の更新操作では枠を使わない．
     */
    private fun reload(trigger: Trigger) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val here = container.locationRepository.location.value
            val settings = container.settingsRepository.settings.value
            val next: List<NearbyCastle>
            if (here == null) {
                next = emptyList()
            } else {
                val limit = minOf(
                    if (settings.nearbyMode == NearbyMode.TOP_N) settings.topN else MAX_ROWS,
                    contentLimit(),
                )
                next = container.castleRepository.nearby(
                    origin = here,
                    filter = CastleFilter(),
                    limit = limit,
                    radiusKm = if (settings.nearbyMode == NearbyMode.RADIUS) settings.effectiveRadiusKm else null,
                )
            }
            val titles = next.map { it.castle.name }
            val structural = sentTitles != null && sentTitles != titles
            if (structural && trigger != Trigger.REFRESH) {
                // 枠を使い切る更新は，利用者が「更新」を押すまで反映しない．
                if (structuralChanges >= MAX_STRUCTURAL_CHANGES) return@launch
                structuralChanges++
            }
            origin = here
            items = next
            sentTitles = titles
            invalidate()
        }
    }

    /** 地図の上のボタンで近づいた城を知らせる．押すと詳細を開く．行は変えないため，更新の枠は使わない． */
    fun showAlert(castleId: String, label: String) {
        val shown = castleId to label
        alert = shown
        alertJob?.cancel()
        alertJob = lifecycleScope.launch {
            delay(ALERT_MILLIS)
            if (alert == shown) {
                alert = null
                invalidate()
            }
        }
        invalidate()
    }

    private fun contentLimit(): Int =
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)

    override fun onGetTemplate(): Template {
        val builder = PlaceListMapTemplate.Builder()
            .setTitle("周辺の城")
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(true)
            .setOnContentRefreshListener { reload(Trigger.REFRESH) }
        alert?.let { (castleId, label) ->
            builder.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(label)
                            .setOnClickListener {
                                alert = null
                                onOpenDetail(castleId)
                            }
                            .build(),
                    )
                    .build(),
            )
        }
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
            .addText(detailText(n))
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

    /**
     * 「北西(左前方) ・ 15 km」の形にする．PlaceListMapTemplate の行には距離の [DistanceSpan] が必須のため，
     * 距離は末尾の 1 文字にスパンを付けてホストに表示させる．進行方向が得られないときは方角のみ．
     */
    private fun detailText(n: NearbyCastle): CharSequence {
        val c = n.castle
        val distance = if (n.distanceMeters < 1000) {
            Distance.create(n.distanceMeters, Distance.UNIT_METERS)
        } else {
            Distance.create(n.distanceMeters / 1000, Distance.UNIT_KILOMETERS)
        }
        val bearing = origin?.let { Direction.bearing(it, GeoPoint(c.lat, c.lng)) }
        val heading = container.locationRepository.heading.value
        val direction = bearing?.let {
            val compass = Direction.compass(it)
            if (heading == null) compass else "$compass(${Direction.relative(it, heading.toDouble())})"
        }
        val prefix = if (direction == null) "" else "$direction ・ "
        val text = SpannableString("$prefix ")
        text.setSpan(DistanceSpan.create(distance), prefix.length, prefix.length + 1, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        return text
    }

    private companion object {
        const val MAX_ROWS = 100
        const val ALERT_MILLIS = 30_000L

        /**
         * 構成が変わる更新の上限．実機（DHU）で 3 回にしたところ，詳細から戻った後に 5 枚目で
         * 「Unsupported template type as the last step in a task」となって落ちたため，余裕を見て 1 回にする．
         */
        const val MAX_STRUCTURAL_CHANGES = 1
    }
}
