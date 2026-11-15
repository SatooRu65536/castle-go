package dev.satooru.castles.go.ui.detail

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** 属性の表示行．[url] があるときは押すと開く． */
data class AttributeLine(val text: String, val url: String? = null)

data class AttributeRow(val label: String, val lines: List<AttributeLine>)

data class AttributeSection(val title: String, val rows: List<AttributeRow>)

/**
 * 城の属性（API の `attributes`）を，日本語の見出し・項目名・値にする．
 * castle-wiki の `attributeDisplay.ts` と `ja.ts` の表示に合わせる．
 * 各属性は `{ "state": "known", "value": … }`（値あり）か `{ "state": "unknown" }`（不明）．未入力はキーを持たない．
 */
object AttributeFormatter {
    // 画面の上部の基本情報（都道府県・規模・種別・立地・天守の状態・名城の選定）と，タグ・位置は，ここでは出さない．
    private val SECTIONS: List<Pair<String, List<String>>> = listOf(
        "基本情報" to listOf("aliases", "nawabari", "culturalProperties"),
        "所在地" to listOf("country", "region", "city", "addressDetail", "postalCode", "elevation", "relativeHeight"),
        "歴史" to listOf(
            "builtYear", "builders", "renovations", "abandonedYear", "abandonmentReason", "lords", "han", "events",
        ),
        "天守" to listOf("tenshuPresence", "tenshuFloors", "tenshuStyle", "tenshuComposition", "tenshuRebuiltYear"),
        "構造物" to listOf("remains", "formerStructures"),
        "出典・関連リンク" to listOf("sources", "links"),
    )

    private val LABELS = mapOf(
        "aliases" to "別名", "nawabari" to "縄張り", "culturalProperties" to "文化財指定",
        "country" to "国", "region" to "市・道", "city" to "市区町村", "addressDetail" to "住所以下", "postalCode" to "郵便番号",
        "elevation" to "標高", "relativeHeight" to "比高",
        "builtYear" to "築城年", "builders" to "築城者", "renovations" to "主な改修", "abandonedYear" to "廃城年",
        "abandonmentReason" to "廃城の理由", "lords" to "歴代城主", "han" to "藩", "events" to "主な出来事",
        "tenshuPresence" to "天守の有無", "tenshuFloors" to "天守の階数", "tenshuStyle" to "天守の型式",
        "tenshuComposition" to "天守の構成", "tenshuRebuiltYear" to "天守の再建年",
        "remains" to "遺構", "formerStructures" to "元々あった構造物", "sources" to "出典", "links" to "関連リンク",
    )

    private val COUNTRY = mapOf("JP" to "日本", "KR" to "韓国")
    private val KOREA_REGION = mapOf(
        "11" to "ソウル特別市", "26" to "釜山広域市", "27" to "大邱広域市", "28" to "仁川広域市",
        "29" to "光州広域市", "30" to "大田広域市", "31" to "蔚山広域市", "50" to "世宗特別自治市",
        "41" to "京畿道", "42" to "江原特別自治道", "43" to "忠清北道", "44" to "忠清南道",
        "45" to "全北特別自治道", "46" to "全羅南道", "47" to "慶尚北道", "48" to "慶尚南道",
        "49" to "済州特別自治道",
    )
    private val NAWABARI = mapOf(
        "rinkaku" to "輪郭式", "teikaku" to "梯郭式", "renkaku" to "連郭式", "kankaku" to "環郭式",
        "heikaku" to "並郭式", "other" to "その他",
    )
    private val CULTURAL_PROPERTY = mapOf(
        "national_treasure" to "国宝", "important_cultural_property" to "重要文化財",
        "special_historic_site" to "特別史跡", "historic_site" to "史跡",
        "prefectural" to "都道府県指定", "municipal" to "市区町村指定",
    )
    private val TENSHU_PRESENCE = mapOf("present" to "天守あり", "base_only" to "天守台のみ", "absent" to "天守なし")
    private val TENSHU_STYLE = mapOf("boro" to "望楼型", "sotou" to "層塔型")
    private val TENSHU_COMPOSITION = mapOf(
        "independent" to "独立式", "compound" to "複合式", "connected" to "連結式", "linked" to "連立式",
    )
    private val REMAIN = mapOf(
        "stone_wall" to "石垣", "earthwork" to "土塁", "moat" to "堀", "kuruwa" to "曲輪",
        "koguchi" to "虎口", "tenshu_base" to "天守台", "other" to "その他",
    )
    private val STRUCTURE_STATUS = mapOf(
        "existing" to "現存", "relocated" to "移築", "burned" to "焼失", "demolished" to "破却",
        "restored" to "復元", "unknown" to "不明",
    )

    /** 属性を，表示するセクションと行にする．未入力の属性と，行のないセクションは含めない． */
    fun sections(attributes: Map<String, JsonElement>): List<AttributeSection> =
        SECTIONS.mapNotNull { (title, keys) ->
            val rows = keys.mapNotNull { key -> row(key, attributes) }
            if (rows.isEmpty()) null else AttributeSection(title, rows)
        }

    private fun row(key: String, attributes: Map<String, JsonElement>): AttributeRow? {
        val label = LABELS[key] ?: return null
        if (key == "tenshuFloors") return tenshuFloorsRow(label, attributes)
        val attribute = attributes[key] as? JsonObject ?: return null
        return when (attribute.str("state")) {
            "unknown" -> AttributeRow(label, listOf(AttributeLine("不明")))
            "known" -> format(key, attribute["value"]).takeIf { it.isNotEmpty() }?.let { AttributeRow(label, it) }
            else -> null
        }
    }

    private fun tenshuFloorsRow(label: String, attributes: Map<String, JsonElement>): AttributeRow? {
        val roof = knownInt(attributes, "tenshuRoofCount") ?: return null
        val floor = knownInt(attributes, "tenshuFloorCount") ?: return null
        val basement = knownInt(attributes, "tenshuBasementCount")
        val text = "${roof}重${floor}階" + if (basement != null && basement > 0) "地下${basement}階" else ""
        return AttributeRow(label, listOf(AttributeLine(text)))
    }

    private fun knownInt(attributes: Map<String, JsonElement>, key: String): Int? {
        val attribute = attributes[key] as? JsonObject ?: return null
        if (attribute.str("state") != "known") return null
        return (attribute["value"] as? JsonPrimitive)?.intOrNull
    }

    private fun format(key: String, value: JsonElement?): List<AttributeLine> = when (key) {
        "aliases" -> lines(value.items().mapNotNull { it.text() }.joinToString("，"))
        "nawabari" -> lines(enumLabel(NAWABARI, value.text()))
        "country" -> lines(enumLabel(COUNTRY, value.text()))
        "region" -> lines(enumLabel(KOREA_REGION, value.text()))
        "culturalProperties" -> lines(
            *value.items().map { joinNonEmpty(listOf(enumLabel(CULTURAL_PROPERTY, it.field("kind")), it.field("target")), "：") }.toTypedArray(),
        )
        "city", "addressDetail", "abandonmentReason", "han" -> lines(value.text().orEmpty())
        "postalCode" -> lines(value.text()?.let { if (it.length == 7) "〒${it.take(3)}-${it.drop(3)}" else it }.orEmpty())
        "elevation", "relativeHeight" -> lines(value.text()?.let { "${it}m" }.orEmpty())
        "builtYear", "abandonedYear", "tenshuRebuiltYear" -> lines(year(value))
        "builders" -> lines(value.items().mapNotNull { it.field("name").takeIf { n -> n.isNotEmpty() } }.joinToString("，"))
        "renovations" -> lines(
            *value.items().map { joinNonEmpty(listOf(year(it.get("year")), it.field("person"), it.field("description")), " ") }.toTypedArray(),
        )
        "lords" -> lines(*value.items().map(::lord).toTypedArray())
        "events" -> lines(
            *value.items().map { joinNonEmpty(listOf(year(it.get("year")), it.field("name"), it.field("description")), " ") }.toTypedArray(),
        )
        "tenshuPresence" -> lines(enumLabel(TENSHU_PRESENCE, value.text()))
        "tenshuStyle" -> lines(enumLabel(TENSHU_STYLE, value.text()))
        "tenshuComposition" -> lines(enumLabel(TENSHU_COMPOSITION, value.text()))
        "remains" -> lines(
            *value.items().map { joinNonEmpty(listOf(enumLabel(REMAIN, it.field("kind")), it.field("description")), "：") }.toTypedArray(),
        )
        "formerStructures" -> lines(*value.items().map(::formerStructure).toTypedArray())
        "sources" -> value.items().mapNotNull { source ->
            val text = joinNonEmpty(
                listOf(source.field("title"), source.field("author"), source.field("year"), source.field("pages")), "，",
            )
            if (text.isEmpty()) null else AttributeLine(text, source.field("url").ifEmpty { null })
        }
        "links" -> value.items().mapNotNull { link ->
            val url = link.field("url")
            val title = link.field("title").ifEmpty { url }
            if (title.isEmpty()) null else AttributeLine(title, url.ifEmpty { null })
        }
        else -> emptyList()
    }

    private fun lord(element: JsonElement): String {
        val start = year(element.get("start"))
        val end = year(element.get("end"))
        val term = if (start.isNotEmpty() || end.isNotEmpty()) "${start}〜${end}" else ""
        val family = element.field("family").let { if (it.isNotEmpty()) "（$it）" else "" }
        return joinNonEmpty(listOf(element.field("name"), family, term, element.field("notes")), " ")
    }

    private fun formerStructure(element: JsonElement): String {
        val status = enumLabel(STRUCTURE_STATUS, element.field("status"))
        val relocated = element.field("relocatedTo").let { if (it.isNotEmpty()) "移築先：$it" else "" }
        return joinNonEmpty(listOf(element.field("name"), "（$status）", relocated), " ")
    }

    /** 年は `{ "year", "precision", "label" }`．表示は `label`． */
    private fun year(element: JsonElement?): String = element.field("label")

    private fun lines(vararg texts: String): List<AttributeLine> = texts.filter { it.isNotEmpty() }.map { AttributeLine(it) }

    private fun joinNonEmpty(texts: List<String>, separator: String): String =
        texts.filter { it.isNotEmpty() }.joinToString(separator)

    /** 翻訳にない値は，値をそのまま返す（castle-wiki の `formatEnumLabel` と同じ）． */
    private fun enumLabel(table: Map<String, String>, value: String?): String =
        value?.let { table[it] ?: it }.orEmpty()

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonElement?.items(): List<JsonElement> = (this as? JsonArray) ?: emptyList()
    private fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private fun JsonElement?.field(key: String): String = get(key).text().orEmpty()
    private fun JsonObject.str(key: String): String? = this[key].text()
}
