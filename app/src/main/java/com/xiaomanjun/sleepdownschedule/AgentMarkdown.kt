package com.xiaomanjun.sleepdownschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private sealed interface AgentMarkdownBlock {
    data class Paragraph(val text: String) : AgentMarkdownBlock
    data class Bullet(val text: String) : AgentMarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : AgentMarkdownBlock
}

private sealed interface AgentMarkdownRenderBlock {
    data class Paragraph(val text: AnnotatedString) : AgentMarkdownRenderBlock
    data class Bullet(val text: AnnotatedString) : AgentMarkdownRenderBlock
    data class Table(
        val header: List<AnnotatedString>,
        val rows: List<List<AnnotatedString>>
    ) : AgentMarkdownRenderBlock
}

@Composable
fun AgentMarkdownText(markdown: String, color: Color, style: TextStyle) {
    // Compile both block structure and inline spans once per message. Previously the block list
    // was remembered, but every Text rebuilt its AnnotatedString whenever an ancestor recomposed.
    val blocks = remember(markdown) {
        parseAgentMarkdown(markdown).map { block ->
            when (block) {
                is AgentMarkdownBlock.Paragraph ->
                    AgentMarkdownRenderBlock.Paragraph(agentInlineMarkdown(block.text))
                is AgentMarkdownBlock.Bullet ->
                    AgentMarkdownRenderBlock.Bullet(agentInlineMarkdown(block.text))
                is AgentMarkdownBlock.Table ->
                    AgentMarkdownRenderBlock.Table(
                        header = block.header.map(::agentInlineMarkdown),
                        rows = block.rows.map { row -> row.map(::agentInlineMarkdown) }
                    )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        blocks.forEach { block ->
            when (block) {
                is AgentMarkdownRenderBlock.Paragraph -> Text(block.text, color = color, style = style)
                is AgentMarkdownRenderBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("•", color = color, style = style)
                    Text(block.text, modifier = Modifier.weight(1f), color = color, style = style)
                }
                is AgentMarkdownRenderBlock.Table -> AgentMarkdownTable(block, color, style)
            }
        }
    }
}

@Composable
private fun AgentMarkdownTable(table: AgentMarkdownRenderBlock.Table, color: Color, style: TextStyle) {
    val columnCount = table.header.size.coerceAtLeast(1)
    val borderColor = color.copy(alpha = 0.22f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.6.dp, borderColor, RoundedCornerShape(10.dp))
    ) {
        AgentMarkdownTableRow(table.header, columnCount, color, style, header = true)
        table.rows.forEach { row ->
            HorizontalDivider(color = borderColor, thickness = 0.6.dp)
            AgentMarkdownTableRow(row, columnCount, color, style, header = false)
        }
    }
}

@Composable
private fun AgentMarkdownTableRow(
    cells: List<AnnotatedString>,
    columnCount: Int,
    color: Color,
    style: TextStyle,
    header: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (header) Modifier.background(color.copy(alpha = 0.08f)) else Modifier)
    ) {
        repeat(columnCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Text(
                    text = cells.getOrNull(index) ?: AnnotatedString(""),
                    color = color,
                    style = style,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

private fun parseAgentMarkdown(markdown: String): List<AgentMarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<AgentMarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        if (line.isBlank()) {
            index++
            continue
        }
        if (index + 1 < lines.size && line.contains('|') && isMarkdownTableDivider(lines[index + 1])) {
            val header = markdownCells(line)
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                rows += markdownCells(lines[index])
                index++
            }
            blocks += AgentMarkdownBlock.Table(header, rows)
            continue
        }
        if (line.startsWith("- ") || line.startsWith("* ")) {
            blocks += AgentMarkdownBlock.Bullet(line.drop(2).trim())
            index++
            continue
        }
        val paragraph = mutableListOf(line)
        index++
        while (index < lines.size) {
            val next = lines[index].trim()
            if (next.isBlank() || next.startsWith("- ") || next.startsWith("* ") ||
                (index + 1 < lines.size && next.contains('|') && isMarkdownTableDivider(lines[index + 1]))) {
                break
            }
            paragraph += next
            index++
        }
        blocks += AgentMarkdownBlock.Paragraph(paragraph.joinToString("\n"))
    }
    return blocks.ifEmpty { listOf(AgentMarkdownBlock.Paragraph(markdown)) }
}

private fun isMarkdownTableDivider(line: String): Boolean {
    val cells = markdownCells(line)
    return cells.isNotEmpty() && cells.all { it.matches(Regex(":?-{3,}:?")) }
}

private fun markdownCells(line: String): List<String> = line
    .trim()
    .removePrefix("|")
    .removeSuffix("|")
    .split('|')
    .map(String::trim)

private fun agentInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append("**")
                    index += 2
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append('`')
                    index++
                }
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
}
