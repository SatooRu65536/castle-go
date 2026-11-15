package dev.satooru.castles.go

import dev.satooru.castles.go.ui.common.MdBlock
import dev.satooru.castles.go.ui.common.MdSpan
import dev.satooru.castles.go.ui.common.parseInline
import dev.satooru.castles.go.ui.common.parseMarkdown
import dev.satooru.castles.go.ui.detail.AttributeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailFormatTest {
    private fun attributes(json: String): Map<String, JsonElement> = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun attributesAreShownWithJapaneseLabelsAndValues() {
        val sections = AttributeFormatter.sections(
            attributes(
                """{
                "name":{"state":"known","value":"姫路城"},
                "scale":{"state":"known","value":6},
                "nawabari":{"state":"known","value":"rinkaku"},
                "city":{"state":"known","value":"姫路市"},
                "postalCode":{"state":"known","value":"6700012"},
                "elevation":{"state":"known","value":46},
                "builtYear":{"state":"known","value":{"year":1346,"precision":"year","label":"正平元年（1346年）"}},
                "lords":{"state":"known","value":[{"name":"赤松貞範","family":"赤松氏","start":{"year":1346,"precision":"year","label":"1346年"},"end":null,"notes":""}]},
                "remains":{"state":"known","value":[{"kind":"stone_wall","description":"天守台をはじめ石垣が残る"}]},
                "tenshuRoofCount":{"state":"known","value":5},
                "tenshuFloorCount":{"state":"known","value":6},
                "tenshuBasementCount":{"state":"known","value":1},
                "tenshuStyle":{"state":"known","value":"boro"},
                "sources":{"state":"known","value":[{"title":"姫路城の歴史","author":"姫路市","year":null,"pages":"","url":"https://example.com/a"}]},
                "han":{"state":"unknown"}
            }""",
            ),
        )
        val byTitle = sections.associate { it.title to it.rows.associate { r -> r.label to r.lines } }
        assertEquals("輪郭式", byTitle["基本情報"]!!["縄張り"]!![0].text)
        assertEquals("〒670-0012", byTitle["所在地"]!!["郵便番号"]!![0].text)
        assertEquals("46m", byTitle["所在地"]!!["標高"]!![0].text)
        assertEquals("正平元年（1346年）", byTitle["歴史"]!!["築城年"]!![0].text)
        assertEquals("赤松貞範 （赤松氏） 1346年〜", byTitle["歴史"]!!["歴代城主"]!![0].text)
        assertEquals("不明", byTitle["歴史"]!!["藩"]!![0].text)
        assertEquals("5重6階地下1階", byTitle["天守"]!!["天守の階数"]!![0].text)
        assertEquals("望楼型", byTitle["天守"]!!["天守の型式"]!![0].text)
        assertEquals("石垣：天守台をはじめ石垣が残る", byTitle["構造物"]!!["遺構"]!![0].text)
        val source = byTitle["出典・関連リンク"]!!["出典"]!![0]
        assertEquals("姫路城の歴史，姫路市", source.text)
        assertEquals("https://example.com/a", source.url)
    }

    @Test
    fun englishPropertyNamesAndBasicInfoRowsAreNotShown() {
        val sections = AttributeFormatter.sections(
            attributes("""{"name":{"state":"known","value":"x"},"lat":{"state":"known","value":1.0},"scale":{"state":"known","value":3},"castleKind":{"state":"known","value":"castle"}}"""),
        )
        assertTrue(sections.isEmpty())
        val all = AttributeFormatter.sections(attributes("""{"builtYear":{"state":"known","value":{"label":"1346年"}}}""")).flatMap { it.rows }
        assertTrue(all.none { row -> row.label.any { it in 'a'..'z' } })
    }

    @Test
    fun unknownEnumValueIsShownAsIs() {
        val rows = AttributeFormatter.sections(attributes("""{"nawabari":{"state":"known","value":"future_type"}}""")).flatMap { it.rows }
        assertEquals("future_type", rows.single().lines.single().text)
    }

    @Test
    fun markdownHeadingsListsAndParagraphsAreParsed() {
        val blocks = parseMarkdown("## 概要\n\n姫路城は，\n兵庫県にある．\n\n## 見どころ\n\n- 天守\n- 石垣\n  - 野面積み\n\n1. 第一\n2. 第二\n\n> 引用\n\n---\n")
        assertEquals(MdBlock.Heading(2, "概要"), blocks[0])
        assertEquals(MdBlock.Paragraph("姫路城は， 兵庫県にある．"), blocks[1])
        assertEquals(MdBlock.Heading(2, "見どころ"), blocks[2])
        assertEquals(MdBlock.ListItem(false, 0, 0, "天守"), blocks[3])
        assertEquals(MdBlock.ListItem(false, 0, 1, "野面積み"), blocks[5])
        assertEquals(MdBlock.ListItem(true, 1, 0, "第一"), blocks[6])
        assertEquals(MdBlock.Quote("引用"), blocks[8])
        assertEquals(MdBlock.Rule, blocks[9])
    }

    @Test
    fun markdownInlineSyntaxIsParsed() {
        val spans = parseInline("**太字**と*斜体*と`code`と[リンク](https://example.com)")
        assertEquals(MdSpan.Text("太字", bold = true), spans[0])
        assertEquals(MdSpan.Text("と"), spans[1])
        assertEquals(MdSpan.Text("斜体", italic = true), spans[2])
        assertEquals(MdSpan.Text("code", code = true), spans[4])
        assertEquals(MdSpan.Link("リンク", "https://example.com"), spans[6])
        assertNull(spans.filterIsInstance<MdSpan.Text>().firstOrNull { it.text.contains("**") })
    }

    @Test
    fun markdownCodeFenceKeepsContentAsIs() {
        val blocks = parseMarkdown("```\n## これは見出しではない\n```\n")
        assertEquals(MdBlock.Code("## これは見出しではない"), blocks.single())
    }

    @Test
    fun markdownIsSplitIntoSectionsAtLevel2Headings() {
        val sections = dev.satooru.castles.go.ui.common.splitBySections(
            parseMarkdown("前文\n\n## 概要\n\n本文\n\n### 小見出し\n\n詳細\n\n## 歴史\n\n沿革"),
        )
        assertEquals(3, sections.size)
        assertEquals(MdBlock.Paragraph("前文"), sections[0].single())
        assertEquals(MdBlock.Heading(2, "概要"), sections[1].first())
        assertEquals(4, sections[1].size) // 概要・本文・小見出し・詳細
        assertEquals(MdBlock.Heading(2, "歴史"), sections[2].first())
        assertTrue(dev.satooru.castles.go.ui.common.splitBySections(emptyList()).isEmpty())
    }

    @Test
    fun areaShowsPrefectureForJapanAndCountryAndRegionForKorea() {
        assertEquals("愛知県", dev.satooru.castles.go.ui.common.Labels.area("JP", "23", null))
        assertEquals("韓国 ・ 釜山広域市", dev.satooru.castles.go.ui.common.Labels.area("KR", null, "26"))
        assertEquals("韓国 ・ 99", dev.satooru.castles.go.ui.common.Labels.area("KR", null, "99")) // 未知の市・道はコードのまま
        assertEquals("韓国", dev.satooru.castles.go.ui.common.Labels.area("KR", null, null))
    }

    @Test
    fun countryAndRegionAreShownInTheLocationSection() {
        val rows = AttributeFormatter.sections(
            attributes("""{"country":{"state":"known","value":"KR"},"region":{"state":"known","value":"48"},"addressDetail":{"state":"known","value":"鎮海区"}}"""),
        ).single { it.title == "所在地" }.rows.associate { it.label to it.lines.single().text }
        assertEquals("韓国", rows["国"])
        assertEquals("慶尚南道", rows["市・道"])
    }
}
