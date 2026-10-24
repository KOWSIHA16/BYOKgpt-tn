package com.example.byokgpt.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.InputStream

data class Attachment(
    val uriString: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String = ""
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isPdf: Boolean
        get() = mimeType == "application/pdf"
}

sealed class AttachmentValidationResult {
    data object Valid : AttachmentValidationResult()
    data class FileTooLarge(val sizeBytes: Long, val maxBytes: Long) : AttachmentValidationResult()
    data class UnsupportedType(val mimeType: String) : AttachmentValidationResult()
    data class Error(val message: String) : AttachmentValidationResult()
}

object AttachmentValidator {
    const val MAX_SIZE_BYTES: Long = 10 * 1024 * 1024 // 10 MB

    val SUPPORTED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/pdf"
    )

    const val MAX_FILES: Int = 5

    fun validate(mimeType: String?, sizeBytes: Long): AttachmentValidationResult {
        if (mimeType == null || !SUPPORTED_MIME_TYPES.contains(mimeType.lowercase())) {
            return AttachmentValidationResult.UnsupportedType(mimeType ?: "unknown")
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            return AttachmentValidationResult.FileTooLarge(sizeBytes, MAX_SIZE_BYTES)
        }
        return AttachmentValidationResult.Valid
    }

    fun validateCumulative(existing: List<Attachment>, newAttachment: Attachment): AttachmentValidationResult {
        if (existing.size >= MAX_FILES) {
            return AttachmentValidationResult.Error("Maximum $MAX_FILES attachments allowed per message.")
        }
        val singleVal = validate(newAttachment.mimeType, newAttachment.sizeBytes)
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
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val validation = validate(mimeType, fileSize)
            when (validation) {
                is AttachmentValidationResult.FileTooLarge -> {
                    return Result.failure(IllegalArgumentException("File size exceeds 10MB limit."))
                }
                is AttachmentValidationResult.UnsupportedType -> {
                    return Result.failure(IllegalArgumentException("Unsupported file type: $mimeType. Supported formats: JPEG, PNG, WEBP, PDF."))
                }
                is AttachmentValidationResult.Error -> {
                    return Result.failure(IllegalArgumentException(validation.message))
                }
                is AttachmentValidationResult.Valid -> {
                    // Read file bytes and encode to base64
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return Result.failure(IllegalArgumentException("Unable to read attachment data."))
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    Result.success(
                        Attachment(
                            uriString = uri.toString(),
                            fileName = fileName,
                            mimeType = mimeType,
                            sizeBytes = bytes.size.toLong(),
                            base64Data = base64
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
