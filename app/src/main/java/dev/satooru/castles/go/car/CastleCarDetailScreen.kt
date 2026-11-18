package dev.satooru.castles.go.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.repository.CastleDetail
import dev.satooru.castles.go.data.repository.DetailResult
import dev.satooru.castles.go.data.repository.Direction
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.ui.common.Labels
import dev.satooru.castles.go.ui.common.MdBlock
import dev.satooru.castles.go.ui.common.MdSpan
import dev.satooru.castles.go.ui.common.parseInline
import dev.satooru.castles.go.ui.common.parseMarkdown
import kotlinx.coroutines.launch

/** 城の基本情報．車載には長文を出さないため，概要は先頭の段落を短くして出す． */
class CastleCarDetailScreen(
    carContext: CarContext,
    private val container: AppContainer,
    private val castleId: String,
) : Screen(carContext) {
    private var castle: CastleEntity? = null
    private var detail: CastleDetail? = null
    private var loaded = false

    init {
        lifecycleScope.launch {
            castle = container.castleRepository.findCastle(castleId)
            // 概要は詳細から．キャッシュがあれば通信しない．取得できなくても基本情報は出す．
            if (castle != null) {
                detail = (container.castleRepository.loadDetail(castleId) as? DetailResult.Success)?.detail
            }
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val c = castle
        val pane = Pane.Builder()
        if (!loaded) return PaneTemplate.Builder(pane.setLoading(true).build()).setTitle("城の情報").setHeaderAction(Action.BACK).build()
        if (c == null) {
            pane.addRow(Row.Builder().setTitle("この城は端末にありません").build())
            return PaneTemplate.Builder(pane.build()).setTitle("城の情報").setHeaderAction(Action.BACK).build()
        }
        val limit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE)
        rows(c).take(limit).forEach { pane.addRow(it) }
        pane.addAction(
            Action.Builder()
                .setTitle("ナビを開始")
                .setOnClickListener { navigate(c) }
                .build(),
        )
        return PaneTemplate.Builder(pane.build()).setTitle(c.name).setHeaderAction(Action.BACK).build()
    }

    /** 行数に上限（4 行）があるため大事な順に並べ，1 行に題と本文 2 行を詰める． */
    private fun rows(c: CastleEntity): List<Row> = listOfNotNull(
        directionRow(c),
        scaleRow(c),
        overviewRow(),
    )

    /** 「南方3.0km（左前方）」の形．現在地からの 8 方角，距離，進行方向から見た 8 方向．現在地がなければ出さない． */
    private fun directionRow(c: CastleEntity): Row? {
        val here = container.locationRepository.location.value ?: return null
        val target = GeoPoint(c.lat, c.lng)
        val bearing = Direction.bearing(here, target)
        val heading = container.locationRepository.heading.value
        val relative = heading?.let { "（${Direction.relative(bearing, it.toDouble())}）" }.orEmpty()
        val distance = Labels.distance(DistanceCalculator.meters(here, target)).replace(" ", "")
        return Row.Builder().setTitle("${Direction.compass(bearing)}方$distance$relative").build()
    }

    private fun scaleRow(c: CastleEntity): Row {
        val title = listOfNotNull("規模 ${c.scale}", Labels.locationType(c.locationType)).joinToString(" ・ ")
        val row = Row.Builder().setTitle(title)
        listOfNotNull(
            Labels.castleKind(c.castleKind)?.let { "種別: $it" },
            Labels.tenshuStatus(c.tenshuStatus)?.let { "天守: $it" },
        ).joinToString(" ・ ").takeIf { it.isNotEmpty() }?.let { row.addText(it) }
        c.meijoNumber?.let { row.addText("${Labels.meijoSelection(c.meijoSelection) ?: "名城"} No.$it") }
        return row.build()
    }

    /** 概要は題の下に 2 行で出す． */
    private fun overviewRow(): Row? {
        val text = overview() ?: return null
        val row = Row.Builder().setTitle("概要")
        text.chunked(OVERVIEW_LINE).forEach { row.addText(it) }
        return row.build()
    }

    /** 本文の先頭の段落（記法を除く）を [OVERVIEW_MAX] 文字までにする（省略するときは末尾に … を付ける）． */
    private fun overview(): String? {
        val paragraph = parseMarkdown(detail?.body.orEmpty()).firstOrNull { it is MdBlock.Paragraph } as? MdBlock.Paragraph
            ?: return null
        val text = parseInline(paragraph.text).joinToString("") {
            when (it) {
                is MdSpan.Text -> it.text
                is MdSpan.Link -> it.text
            }
        }.trim()
        if (text.isEmpty()) return null
        return if (text.length > OVERVIEW_MAX) text.take(OVERVIEW_MAX - 1) + "…" else text
    }

    /** 外部ナビアプリに緯度経度を引き継ぐ．POI アプリで使えるかは実機・DHU で要確認． */
    private fun navigate(c: CastleEntity) {
        val uri = Uri.parse("geo:${c.lat},${c.lng}?q=${c.lat},${c.lng}(${Uri.encode(c.name)})")
        carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
    }

    private companion object {
        /** 1 行に出す文字数 */
        const val OVERVIEW_LINE = 60
        const val OVERVIEW_MAX = OVERVIEW_LINE * 2
    }
}
