package dev.satooru.castles.go.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.ui.common.Labels
import kotlinx.coroutines.launch

/** 城の基本情報．車載には本文や長文を出さない． */
class CastleCarDetailScreen(
    carContext: CarContext,
    private val container: AppContainer,
    private val castleId: String,
) : Screen(carContext) {
    private var castle: CastleEntity? = null
    private var loaded = false

    init {
        lifecycleScope.launch {
            castle = container.castleRepository.findCastle(castleId)
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
        if (c.nameKana.isNotEmpty()) pane.addRow(Row.Builder().setTitle("読み仮名").addText(c.nameKana).build())
        Labels.area(c.country, c.prefecture, c.region)?.let {
            pane.addRow(Row.Builder().setTitle(if (c.country == "JP") "都道府県" else "所在地").addText(it).build())
        }
        Labels.tenshuStatus(c.tenshuStatus)?.let { pane.addRow(Row.Builder().setTitle("天守").addText(it).build()) }
        c.meijoNumber?.let {
            pane.addRow(
                Row.Builder().setTitle("名城の選定")
                    .addText("${Labels.meijoSelection(c.meijoSelection) ?: "名城"} No.$it").build(),
            )
        }
        pane.addAction(
            Action.Builder()
                .setTitle("ナビを開始")
                .setOnClickListener { navigate(c) }
                .build(),
        )
        return PaneTemplate.Builder(pane.build()).setTitle(c.name).setHeaderAction(Action.BACK).build()
    }

    /** 外部ナビアプリに緯度経度を引き継ぐ．POI アプリで使えるかは実機・DHU で要確認． */
    private fun navigate(c: CastleEntity) {
        val uri = Uri.parse("geo:${c.lat},${c.lng}?q=${c.lat},${c.lng}(${Uri.encode(c.name)})")
        carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
    }
}
