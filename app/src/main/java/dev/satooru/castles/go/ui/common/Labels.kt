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

    val countries: List<Pair<String, String>> = listOf("JP" to "日本", "KR" to "韓国")

    /** 韓国の市・道．ISO 3166-2:KR の数字部分 */
    val koreaRegions: List<Pair<String, String>> = listOf(
        "11" to "ソウル特別市", "26" to "釜山広域市", "27" to "大邱広域市", "28" to "仁川広域市",
        "29" to "光州広域市", "30" to "大田広域市", "31" to "蔚山広域市", "50" to "世宗特別自治市",
        "41" to "京畿道", "42" to "江原特別自治道", "43" to "忠清北道", "44" to "忠清南道",
        "45" to "全北特別自治道", "46" to "全羅南道", "47" to "慶尚北道", "48" to "慶尚南道",
        "49" to "済州特別自治道",
    )

    fun country(code: String?): String? = lookup(countries, code)
    fun koreaRegion(code: String?): String? = code?.let { c -> koreaRegions.firstOrNull { it.first == c }?.second ?: c }

    /**
     * 所在地の表示．日本は都道府県，韓国は「韓国 ・ 市・道」．
     * 日本以外の城は，都道府県を持たず，市・道で表す．
     */
    fun area(country: String, prefecture: String?, region: String?): String? =
        if (country == "JP") prefecture(prefecture)
        else listOfNotNull(country(country), koreaRegion(region)).joinToString(" ・ ")

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
