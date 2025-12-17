package com.goldberg.law.document.model.pdf

import com.azure.core.util.BinaryData
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.ClassificationInfo
import com.goldberg.law.entity.IInputFile
import com.goldberg.law.entity.InputFile
import com.goldberg.law.entity.ClassifiedPages
import com.goldberg.law.util.docForPages
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.*

open class PdfDocument(val fileInfo: InputFile, private val document: PDDocument): IInputFile by fileInfo {
    val totalPages = document.numberOfPages

    init {
        if (totalPages != fileInfo.numPages) {
            throw InvalidPdfException("Pdf document has $totalPages pages but is initialized with ${fileInfo.numPages}")
        }
    }

    // some sharing inside PDFBox requires this to be synchronized
    fun toBinaryData(): BinaryData = synchronized(LOCK) {
        val outputStream = ByteArrayOutputStream()
        document.save(outputStream)
        BinaryData.fromBytes(outputStream.toByteArray())
    }

    /**
     * Generate a deterministic UUID from the PDF content using SHA-256 hash
     */
    fun contentHash(): UUID = synchronized(LOCK) {
        val pdfBytes = toBinaryData().toBytes()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pdfBytes)
        UUID.nameUUIDFromBytes(hashBytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        if (other !is PdfDocument) return false

        return other.fileInfo == this.fileInfo && isDocEqual(other)
    }

    fun asClassifiedDocument(classificationInfo: ClassificationInfo) = ClassifiedPdfDocument(Classification(fileInfo, classificationInfo), document.docForPages(classificationInfo.pages))

    // for unit testing
    fun isDocEqual(other: PdfDocument) = document.pages.map { it } == other.document.pages.map { it }

    override fun hashCode(): Int {
        var result = fileInfo.hashCode()
        result = 31 * result + document.hashCode()
        return result
    }

    companion object {
        const val LOCK = "Lock"
    }

}