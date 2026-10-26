package com.example.byokgpt.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class ParsedDocumentResult(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val textContent: String? = null,
    val imageBase64: String? = null,
    val previewBitmap: Bitmap? = null,
    val isImage: Boolean = false,
    val summary: String = ""
)

object DocumentParser {

    private const val TAG = "DocumentParser"
    private const val MAX_TEXT_LENGTH = 150_000 // ~30k tokens max per file to stay within context limits

    fun parseUri(context: Context, uri: Uri, fileName: String, mimeType: String, sizeBytes: Long): ParsedDocumentResult {
        val lowerName = fileName.lowercase()

        // 1. Image Files
        if (mimeType.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
            lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif")
        ) {
            return parseImage(context, uri, fileName, mimeType, sizeBytes)
        }

        // 2. Plain text, code, CSV, JSON, XML, Markdown
        if (isTextOrCodeFile(lowerName, mimeType)) {
            val text = readPlainText(context, uri)
            return ParsedDocumentResult(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                textContent = text,
                isImage = false,
                summary = "Text/Code Document (${text.length} chars)"
            )
        }

        // 3. PDF Files
        if (mimeType == "application/pdf" || lowerName.endsWith(".pdf")) {
            val pdfText = extractPdfText(context, uri)
            return ParsedDocumentResult(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                textContent = pdfText,
                isImage = false,
                summary = "PDF Document (${pdfText.length} chars extracted)"
            )
        }

        // 4. DOCX / PPTX / XLSX (Office Open XML ZIP format)
        if (lowerName.endsWith(".docx") || lowerName.endsWith(".pptx") || lowerName.endsWith(".xlsx")) {
            val officeText = extractOfficeOpenXmlText(context, uri, lowerName)
            return ParsedDocumentResult(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                textContent = officeText,
                isImage = false,
                summary = "Office Document (${officeText.length} chars extracted)"
            )
        }

        // 5. ZIP Archives
        if (mimeType.contains("zip") || lowerName.endsWith(".zip")) {
            val zipSummary = extractZipArchiveSummary(context, uri)
            return ParsedDocumentResult(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                textContent = zipSummary,
                isImage = false,
                summary = "ZIP Archive"
            )
        }

        // Fallback: Attempt plain text reading
        val fallbackText = readPlainText(context, uri)
        return ParsedDocumentResult(
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            textContent = fallbackText,
            isImage = false,
            summary = "Document ($sizeBytes bytes)"
        )
    }

    private fun isTextOrCodeFile(name: String, mime: String): Boolean {
        if (mime.startsWith("text/")) return true
        if (mime == "application/json" || mime == "application/xml" || mime == "application/javascript") return true
        val extensions = listOf(
            ".txt", ".csv", ".json", ".xml", ".html", ".htm", ".css", ".js", ".ts",
            ".kt", ".kts", ".java", ".py", ".c", ".cpp", ".h", ".hpp", ".cs", ".go",
            ".rs", ".rb", ".php", ".swift", ".sh", ".bash", ".sql", ".md", ".yaml",
            ".yml", ".toml", ".ini", ".properties", ".gradle", ".env", ".log"
        )
        return extensions.any { name.endsWith(it) }
    }

    private fun readPlainText(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                    if (sb.length > MAX_TEXT_LENGTH) {
                        sb.append("\n... [Content truncated at ${MAX_TEXT_LENGTH} characters]")
                        break
                    }
                }
                sb.toString()
            } ?: "[Unable to read stream]"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read plain text from $uri", e)
            "[Error reading file: ${e.message}]"
        }
    }

    private fun parseImage(context: Context, uri: Uri, fileName: String, mimeType: String, sizeBytes: Long): ParsedDocumentResult {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                return ParsedDocumentResult(
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    isImage = true,
                    summary = "Image file"
                )
            }

            // Downscale to max 1600px width/height for fast streaming and model compatibility
            val maxDimension = 1600
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1) maxDimension.toFloat() / width else maxDimension.toFloat() / height
            } else 1.0f

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * scale).toInt(),
                    (height * scale).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            ParsedDocumentResult(
                fileName = fileName,
                mimeType = "image/jpeg",
                sizeBytes = imageBytes.size.toLong(),
                imageBase64 = base64,
                previewBitmap = scaledBitmap,
                isImage = true,
                summary = "Image (${scaledBitmap.width}x${scaledBitmap.height})"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image $uri", e)
            ParsedDocumentResult(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                isImage = true,
                summary = "Image file"
            )
        }
    }

    /**
     * Extracts text from PDF files using text-stream heuristic.
     */
    private fun extractPdfText(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, Charsets.ISO_8859_1))
                val sb = StringBuilder()
                var inTextObject = false
                val extractedWords = StringBuilder()

                var line: String?
                var lineCount = 0
                while (reader.readLine().also { line = it } != null && lineCount < 20_000) {
                    lineCount++
                    val l = line!!.trim()
                    if (l == "BT") {
                        inTextObject = true
                    } else if (l == "ET") {
                        inTextObject = false
                        extractedWords.append(" ")
                    } else if (inTextObject) {
                        // Extract strings inside parentheses (Text) Tj or [(T)(e)(x)(t)] TJ
                        var idx = 0
                        while (idx < l.length) {
                            val start = l.indexOf('(', idx)
                            if (start == -1) break
                            val end = l.indexOf(')', start + 1)
                            if (end == -1) break
                            val word = l.substring(start + 1, end)
                            if (word.isNotBlank() && word.all { it.code in 32..126 || it == '\n' || it == '\t' }) {
                                extractedWords.append(word)
                            }
                            idx = end + 1
                        }
                    }
                    if (extractedWords.length > MAX_TEXT_LENGTH) break
                }

                val result = extractedWords.toString().trim()
                if (result.length > 50) {
                    result
                } else {
                    "[PDF Document: Attached for AI analysis. Text extraction yielded limited selectable text. The document is stored as attachment for processing.]"
                }
            } ?: "[Unable to open PDF stream]"
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction failed", e)
            "[PDF Document: Text extraction encountered an issue: ${e.message}]"
        }
    }

    /**
     * Extracts text from Microsoft Office OpenXML (DOCX, PPTX, XLSX)
     * DOCX / PPTX / XLSX are zip files containing XML content files.
     */
    private fun extractOfficeOpenXmlText(context: Context, uri: Uri, fileName: String): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                val sb = StringBuilder()
                var entry = zip.nextEntry
                val targetNames = when {
                    fileName.endsWith(".docx") -> listOf("word/document.xml")
                    fileName.endsWith(".pptx") -> listOf("ppt/slides/slide")
                    fileName.endsWith(".xlsx") -> listOf("xl/sharedstrings.xml")
                    else -> listOf("document.xml")
                }

                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (targetNames.any { entryName.contains(it) }) {
                        val reader = BufferedReader(InputStreamReader(zip, Charsets.UTF_8))
                        val xmlContent = reader.readText()
                        // Strip XML tags (<...>) to get raw text
                        val textOnly = xmlContent.replace(Regex("<[^>]+>"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        sb.append(textOnly).append("\n\n")
                    }
                    if (sb.length > MAX_TEXT_LENGTH) break
                    entry = zip.nextEntry
                }
                val res = sb.toString().trim()
                if (res.isNotEmpty()) res else "[Office Document: Content processed]"
            } ?: "[Unable to open document]"
        } catch (e: Exception) {
            Log.e(TAG, "Office doc extraction failed", e)
            "[Office Document: Attached for AI review]"
        }
    }

    /**
     * Extracts directory structure and text sample from ZIP archives safely.
     */
    private fun extractZipArchiveSummary(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                val sb = StringBuilder()
                sb.append("--- ZIP Archive Contents ---\n")
                var entry = zip.nextEntry
                var fileCount = 0
                while (entry != null && fileCount < 100) {
                    val sizeStr = if (entry.size >= 0) " (${entry.size} bytes)" else ""
                    sb.append("• ").append(entry.name).append(sizeStr).append("\n")
                    fileCount++
                    entry = zip.nextEntry
                }
                sb.toString()
            } ?: "[Unable to open ZIP stream]"
        } catch (e: Exception) {
            Log.e(TAG, "ZIP extraction failed", e)
            "[ZIP Archive: Attached for review]"
        }
    }
}
