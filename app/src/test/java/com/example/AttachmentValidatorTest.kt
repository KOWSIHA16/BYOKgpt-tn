package com.example

import com.example.byokgpt.model.AttachmentValidationResult
import com.example.byokgpt.model.AttachmentValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentValidatorTest {

    @Test
    fun testValidImageTypesUnderLimit() {
        val resultJpeg = AttachmentValidator.validate("image/jpeg", 5 * 1024 * 1024L)
        val resultPng = AttachmentValidator.validate("image/png", 1 * 1024 * 1024L)
        val resultWebp = AttachmentValidator.validate("image/webp", 9 * 1024 * 1024L)

        assertEquals(AttachmentValidationResult.Valid, resultJpeg)
        assertEquals(AttachmentValidationResult.Valid, resultPng)
        assertEquals(AttachmentValidationResult.Valid, resultWebp)
    }

    @Test
    fun testValidPdfUnderLimit() {
        val resultPdf = AttachmentValidator.validate("application/pdf", 8 * 1024 * 1024L)
        assertEquals(AttachmentValidationResult.Valid, resultPdf)
    }

    @Test
    fun testOversizedFileRejected() {
        val oversizedBytes = 11 * 1024 * 1024L // 11MB > 10MB
        val result = AttachmentValidator.validate("image/png", oversizedBytes)

        assertTrue(result is AttachmentValidationResult.FileTooLarge)
        val error = result as AttachmentValidationResult.FileTooLarge
        assertEquals(oversizedBytes, error.sizeBytes)
        assertEquals(10 * 1024 * 1024L, error.maxBytes)
    }

    @Test
    fun testUnsupportedMimeTypeRejected() {
        val resultVideo = AttachmentValidator.validate("video/mp4", 1024L)
        val resultExe = AttachmentValidator.validate("application/x-msdownload", 1024L)
        val resultNull = AttachmentValidator.validate(null, 1024L)

        assertTrue(resultVideo is AttachmentValidationResult.UnsupportedType)
        assertTrue(resultExe is AttachmentValidationResult.UnsupportedType)
        assertTrue(resultNull is AttachmentValidationResult.UnsupportedType)
    }
}
