package com.example.byokgpt.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onUrlClicked: ((String) -> Unit)? = null
) {
    val blocks = remember(content) { parseMarkdown(content) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = block.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        language = block.language,
                        code = block.code
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    RenderFormattedText(
                        text = block.text,
                        textColor = textColor,
                        onUrlClicked = onUrlClicked
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        RenderFormattedText(
                            text = block.text,
                            textColor = textColor,
                            onUrlClicked = onUrlClicked
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        RenderFormattedText(
                            text = block.text,
                            textColor = textColor,
                            onUrlClicked = onUrlClicked
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = block.text,
                            fontStyle = FontStyle.Italic,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E1E2E))
            .border(1.dp, Color(0xFF313244), RoundedCornerShape(10.dp))
    ) {
        // Code Block Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181825))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA6ADC8)
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(code))
                        isCopied = true
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (isCopied) Color(0xFFA6E3A1) else Color(0xFFA6ADC8),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCopied) "Copied!" else "Copy",
                    fontSize = 11.sp,
                    color = if (isCopied) Color(0xFFA6E3A1) else Color(0xFFA6ADC8)
                )
            }
        }

        // Code Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(0xFFCDD6F4)
            )
        }
    }
}

@Composable
fun RenderFormattedText(
    text: String,
    textColor: Color,
    onUrlClicked: ((String) -> Unit)? = null
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, textColor, linkColor) {
        buildAnnotatedStringWithFormatting(text, textColor, linkColor)
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = textColor, lineHeight = 22.sp),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onUrlClicked?.invoke(annotation.item)
                }
        }
    )
}

fun buildAnnotatedStringWithFormatting(
    text: String,
    baseColor: Color,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val mdLinkRegex = Regex("""^\[([^\]]+)\]\((https?://[^\s)]+)\)""")
        val urlRegex = Regex("""^https?://[^\s)\]]+""")

        while (cursor < text.length) {
            val remaining = text.substring(cursor)

            // 1. Check for markdown link [text](url)
            val mdMatch = mdLinkRegex.find(remaining)
            if (mdMatch != null && mdMatch.range.first == 0) {
                val label = mdMatch.groupValues[1]
                val url = mdMatch.groupValues[2]
                pushStringAnnotation(tag = "URL", annotation = url)
                pushStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                append(label)
                pop()
                pop()
                cursor += mdMatch.value.length
                continue
            }

            // 2. Check for plain URL http(s)://
            val urlMatch = urlRegex.find(remaining)
            if (urlMatch != null && urlMatch.range.first == 0) {
                val url = urlMatch.value
                pushStringAnnotation(tag = "URL", annotation = url)
                pushStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                append(url)
                pop()
                pop()
                cursor += url.length
                continue
            }

            // 3. Check for bold **...**
            if (remaining.startsWith("**")) {
                val endBold = remaining.indexOf("**", 2)
                if (endBold != -1) {
                    val boldText = remaining.substring(2, endBold)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                    append(boldText)
                    pop()
                    cursor += endBold + 2
                    continue
                } else {
                    append("**")
                    cursor += 2
                    continue
                }
            }

            // 4. Check for inline code `...`
            if (remaining.startsWith("`")) {
                val endCode = remaining.indexOf("`", 1)
                if (endCode != -1) {
                    val codeSnippet = remaining.substring(1, endCode)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF313244).copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                    append(" $codeSnippet ")
                    pop()
                    cursor += endCode + 1
                    continue
                } else {
                    append("`")
                    cursor += 1
                    continue
                }
            }

            // Next special token
            val nextSpecial = listOfNotNull(
                remaining.indexOf('[').takeIf { it != -1 },
                remaining.indexOf("http://").takeIf { it != -1 },
                remaining.indexOf("https://").takeIf { it != -1 },
                remaining.indexOf("**").takeIf { it != -1 },
                remaining.indexOf('`').takeIf { it != -1 }
            ).minOrNull()

            if (nextSpecial != null && nextSpecial > 0) {
                append(remaining.substring(0, nextSpecial))
                cursor += nextSpecial
            } else {
                append(remaining[0])
                cursor += 1
            }
        }
    }
}

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            result.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.startsWith("### ")) {
            result.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ")))
            i++
            continue
        }
        if (trimmed.startsWith("## ")) {
            result.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ")))
            i++
            continue
        }
        if (trimmed.startsWith("# ")) {
            result.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ")))
            i++
            continue
        }

        if (trimmed.startsWith("> ")) {
            result.add(MarkdownBlock.Blockquote(trimmed.removePrefix("> ")))
            i++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            result.add(MarkdownBlock.BulletItem(trimmed.substring(2)))
            i++
            continue
        }

        val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)$").find(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val content = numberedMatch.groupValues[2]
            result.add(MarkdownBlock.NumberedItem(num, content))
            i++
            continue
        }

        if (trimmed.isNotEmpty()) {
            result.add(MarkdownBlock.Paragraph(trimmed))
        }

        i++
    }

    return result
}
