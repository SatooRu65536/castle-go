package dev.satooru.castles.go.ui.common

import java.util.Locale

/** 列挙値の表示名．未知の値は「その他」として扱う． */
object Labels {
    private val prefectureNames = listOf(
        "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県", "茨城県", "栃木県", "群馬県",
        "埼玉県", "千葉県", "東京都", "神奈川県", "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県",
        "岐阜県", "静岡県", "愛知県", "三重県", "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県",
        "鳥取県", "島根県", "岡山県", "広島県", "山口県", "徳島県", "香川県", "愛媛県", "高知県", "福岡県",
        "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県",
    )

    /** JIS X 0401 のコード（`01`〜`47`）→ 都道府県名 */
    val prefectures: List<Pair<String, String>> =
        prefectureNames.mapIndexed { i, name -> String.format(Locale.ROOT, "%02d", i + 1) to name }

    val castleKinds: List<Pair<String, String>> = listOf(
        "castle" to "城", "jinya" to "陣屋", "fort" to "砦", "yakata" to "館",
        "gusuku" to "グスク", "chashi" to "チャシ", "other" to "その他",
    )
    val locationTypes: List<Pair<String, String>> = listOf(
        "yamajiro" to "山城", "hirayamajiro" to "平山城", "hirajiro" to "平城", "mizujiro" to "水城",
    )
    val tenshuStatuses: List<Pair<String, String>> = listOf(
        "existing" to "現存", "wooden_restoration" to "木造復元", "exterior_restoration" to "外観復元",
        "revival" to "復興", "mock" to "模擬",
    )
    val meijoSelections: List<Pair<String, String>> = listOf(
        "top100" to "日本100名城", "continued_top100" to "続日本100名城",
    )

    fun prefecture(code: String?): String? = code?.let { c -> prefectures.firstOrNull { it.first == c }?.second ?: "その他" }
    fun castleKind(v: String?): String? = lookup(castleKinds, v)
    fun locationType(v: String?): String? = lookup(locationTypes, v)
    fun tenshuStatus(v: String?): String? = lookup(tenshuStatuses, v)
    fun meijoSelection(v: String?): String? = lookup(meijoSelections, v)

    private fun lookup(table: List<Pair<String, String>>, v: String?): String? =
        v?.let { key -> table.firstOrNull { it.first == key }?.second ?: "その他" }

    fun distance(meters: Double): String =
        if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.JAPAN, "%.1f km", meters / 1000)
}
