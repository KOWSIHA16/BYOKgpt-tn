package com.example.byokgpt.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.byokgpt.util.DocumentParser

data class Attachment(
    val uriString: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String = "",
    val extractedText: String? = null
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isPdf: Boolean
        get() = mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)

    val isTextOrCode: Boolean
        get() = mimeType.startsWith("text/") ||
                fileName.endsWith(".txt", ignoreCase = true) ||
                fileName.endsWith(".json", ignoreCase = true) ||
                fileName.endsWith(".xml", ignoreCase = true) ||
                fileName.endsWith(".csv", ignoreCase = true) ||
                fileName.endsWith(".kt", ignoreCase = true) ||
                fileName.endsWith(".java", ignoreCase = true) ||
                fileName.endsWith(".py", ignoreCase = true) ||
                fileName.endsWith(".js", ignoreCase = true) ||
                fileName.endsWith(".ts", ignoreCase = true)

    val isOfficeDoc: Boolean
        get() = fileName.endsWith(".docx", ignoreCase = true) ||
                fileName.endsWith(".xlsx", ignoreCase = true) ||
                fileName.endsWith(".pptx", ignoreCase = true)

    val isZip: Boolean
        get() = mimeType.contains("zip") || fileName.endsWith(".zip", ignoreCase = true)
}

sealed class AttachmentValidationResult {
    data object Valid : AttachmentValidationResult()
    data class FileTooLarge(val sizeBytes: Long, val maxBytes: Long) : AttachmentValidationResult()
    data class UnsupportedType(val mimeType: String) : AttachmentValidationResult()
    data class Error(val message: String) : AttachmentValidationResult()
}

object AttachmentValidator {
    const val MAX_SIZE_BYTES: Long = 10 * 1024 * 1024 // 10 MB
    const val MAX_FILES: Int = 5

    private val SUPPORTED_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif",
        "pdf", "txt", "csv", "json", "xml", "html", "htm", "css", "js", "ts",
        "kt", "kts", "java", "py", "c", "cpp", "h", "cs", "go", "rs", "rb", "php",
        "swift", "sh", "sql", "md", "yaml", "yml", "toml", "docx", "xlsx", "pptx", "zip"
    )

    fun validate(fileName: String, mimeType: String?, sizeBytes: Long): AttachmentValidationResult {
        if (sizeBytes > MAX_SIZE_BYTES) {
            return AttachmentValidationResult.FileTooLarge(sizeBytes, MAX_SIZE_BYTES)
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val isSupportedExt = ext.isNotEmpty() && SUPPORTED_EXTENSIONS.contains(ext)
        val isSupportedMime = mimeType?.let { m ->
            m.startsWith("image/") || m.startsWith("text/") ||
            m == "application/pdf" || m == "application/json" || m == "application/xml" ||
            m.contains("zip") || m.contains("word") || m.contains("spreadsheet") || m.contains("presentation")
        } ?: false

        if (!isSupportedExt && !isSupportedMime) {
            return AttachmentValidationResult.UnsupportedType(mimeType ?: ext)
        }
        return AttachmentValidationResult.Valid
    }

    fun validate(mimeType: String?, sizeBytes: Long): AttachmentValidationResult {
        return validate(fileName = "file", mimeType = mimeType, sizeBytes = sizeBytes)
    }

    fun validateCumulative(existing: List<Attachment>, newAttachment: Attachment): AttachmentValidationResult {
        if (existing.size >= MAX_FILES) {
            return AttachmentValidationResult.Error("Maximum $MAX_FILES attachments allowed per message.")
        }
        val singleVal = validate(newAttachment.fileName, newAttachment.mimeType, newAttachment.sizeBytes)
        if (singleVal !is AttachmentValidationResult.Valid) {
            return singleVal
        }
        val totalBytes = existing.sumOf { it.sizeBytes } + newAttachment.sizeBytes
        if (totalBytes > MAX_SIZE_BYTES) {
            return AttachmentValidationResult.FileTooLarge(totalBytes, MAX_SIZE_BYTES)
        }
        return AttachmentValidationResult.Valid
    }

    fun loadAttachmentFromUri(context: Context, uri: Uri): Result<Attachment> {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            var fileName = "attachment"
            var fileSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "attachment"
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val validation = validate(fileName, mimeType, fileSize)
            when (validation) {
                is AttachmentValidationResult.FileTooLarge -> {
                    return Result.failure(IllegalArgumentException("File '${fileName}' exceeds the 10MB limit."))
                }
                is AttachmentValidationResult.UnsupportedType -> {
                    return Result.failure(IllegalArgumentException("Unsupported file type: ${validation.mimeType}."))
                }
                is AttachmentValidationResult.Error -> {
                    return Result.failure(IllegalArgumentException(validation.message))
                }
                is AttachmentValidationResult.Valid -> {
                    // Deep document parsing: extract text or scale image base64
                    val parsed = DocumentParser.parseUri(context, uri, fileName, mimeType, fileSize)
                    Result.success(
                        Attachment(
                            uriString = uri.toString(),
                            fileName = fileName,
                            mimeType = parsed.mimeType,
                            sizeBytes = if (parsed.sizeBytes > 0) parsed.sizeBytes else fileSize,
                            base64Data = parsed.imageBase64 ?: "",
                            extractedText = parsed.textContent
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
