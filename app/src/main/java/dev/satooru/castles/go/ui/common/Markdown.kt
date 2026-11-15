package dev.satooru.castles.go.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.satooru.castles.go.ui.theme.Ally

/** Markdown のブロック．城の解説で使う記法（見出し・段落・箇条書き・引用・区切り線・コード）を扱う． */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val ordered: Boolean, val number: Int, val depth: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
    data class Code(val text: String) : MdBlock
}

/** インラインの要素．[bold]・[italic]・[code] は重ねて指定できる． */
sealed interface MdSpan {
    data class Text(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false) : MdSpan
    data class Link(val text: String, val url: String) : MdSpan
}

private val HEADING = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
private val LIST_ITEM = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
private val RULE = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")

/** Markdown をブロックに分ける．段落は連続する行を 1 つにまとめる． */
fun parseMarkdown(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    val quote = mutableListOf<String>()
    var code: MutableList<String>? = null

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.joinToString(" ")).also { paragraph.clear() }
    }
    fun flushQuote() {
        if (quote.isNotEmpty()) blocks += MdBlock.Quote(quote.joinToString(" ")).also { quote.clear() }
    }

    for (raw in markdown.replace("\r\n", "\n").split('\n')) {
        val line = raw.trimEnd()
        val fenced = code
        if (fenced != null) {
            if (line.trim().startsWith("```")) {
                blocks += MdBlock.Code(fenced.joinToString("\n"))
                code = null
            } else {
                fenced += raw
            }
            continue
        }
        if (line.trim().startsWith("```")) {
            flushParagraph(); flushQuote(); code = mutableListOf(); continue
        }
        if (line.isBlank()) {
            flushParagraph(); flushQuote(); continue
        }
        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            flushParagraph(); flushQuote()
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            continue
        }
        if (RULE.matches(line)) {
            flushParagraph(); flushQuote(); blocks += MdBlock.Rule; continue
        }
        if (line.trimStart().startsWith(">")) {
            flushParagraph(); quote += line.trimStart().removePrefix(">").trim(); continue
        }
        val item = LIST_ITEM.matchEntire(line)
        if (item != null) {
            flushParagraph(); flushQuote()
            val marker = item.groupValues[2]
            val ordered = marker.first().isDigit()
            blocks += MdBlock.ListItem(
                ordered = ordered,
                number = if (ordered) marker.dropLast(1).toIntOrNull() ?: 1 else 0,
                depth = item.groupValues[1].length / 2,
                text = item.groupValues[3],
            )
            continue
        }
        flushQuote()
        paragraph += line.trim()
    }
    code?.let { blocks += MdBlock.Code(it.joinToString("\n")) }
    flushParagraph(); flushQuote()
    return blocks
}

private val INLINE = Regex(
    "`([^`]+)`" +                         // 1: コード
        "|\\[([^\\]]+)]\\(([^)\\s]+)\\)" + // 2,3: リンク
        "|\\*\\*(.+?)\\*\\*" +             // 4: 太字
        "|__(.+?)__" +                     // 5: 太字
        "|\\*([^*\\s][^*]*?)\\*" +         // 6: 斜体
        "|_([^_\\s][^_]*?)_",              // 7: 斜体
)

/** 1 行のインラインの記法（`**太字**`・`*斜体*`・`` `コード` ``・`[文字](URL)`）を要素に分ける． */
fun parseInline(text: String, bold: Boolean = false, italic: Boolean = false): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    var index = 0
    for (m in INLINE.findAll(text)) {
        if (m.range.first > index) spans += MdSpan.Text(text.substring(index, m.range.first), bold, italic)
        val g = m.groups
        when {
            g[1] != null -> spans += MdSpan.Text(g[1]!!.value, bold, italic, code = true)
            g[2] != null -> spans += MdSpan.Link(g[2]!!.value, g[3]!!.value)
            g[4] != null -> spans += parseInline(g[4]!!.value, bold = true, italic = italic)
            g[5] != null -> spans += parseInline(g[5]!!.value, bold = true, italic = italic)
            g[6] != null -> spans += parseInline(g[6]!!.value, bold = bold, italic = true)
            g[7] != null -> spans += parseInline(g[7]!!.value, bold = bold, italic = true)
        }
        index = m.range.last + 1
    }
    if (index < text.length) spans += MdSpan.Text(text.substring(index), bold, italic)
    return spans
}

private fun inlineToAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    for (span in parseInline(text)) {
        when (span) {
            is MdSpan.Text -> withStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    background = if (span.code) androidx.compose.ui.graphics.Color(0xFFF0F0F0) else androidx.compose.ui.graphics.Color.Unspecified,
                ),
            ) { append(span.text) }
            is MdSpan.Link -> withLink(
                LinkAnnotation.Url(
                    span.url,
                    TextLinkStyles(SpanStyle(color = Ally, textDecoration = TextDecoration.Underline)),
                ),
            ) { append(span.text) }
        }
    }
}

/** Markdown を整形して表示する．リンクは押すと外部のブラウザーなどで開く． */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = parseMarkdown(markdown)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> Text(
                    inlineToAnnotated(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp),
                )
                is MdBlock.Paragraph -> Text(inlineToAnnotated(block.text), style = MaterialTheme.typography.bodyMedium)
                is MdBlock.ListItem -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
                    Text(
                        if (block.ordered) "${block.number}." else "•",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(inlineToAnnotated(block.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline),
                    )
                    Text(
                        inlineToAnnotated(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                MdBlock.Rule -> HorizontalDivider()
                is MdBlock.Code -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                )
            }
        }
    }
}
