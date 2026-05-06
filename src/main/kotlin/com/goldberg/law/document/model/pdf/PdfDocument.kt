package com.goldberg.law.document.model.pdf

import com.azure.core.util.BinaryData
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.ClassificationInfo
import com.goldberg.law.entity.IInputFile
import com.goldberg.law.entity.InputFile
import com.goldberg.law.util.docForPages
import com.goldberg.law.util.sha256
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.*

open class PdfDocument(val inputFile: InputFile, private val document: PDDocument): IInputFile by inputFile {
    val totalPages = document.numberOfPages

    // some sharing inside PDFBox requires this to be synchronized
    fun toBinaryData(): BinaryData = BinaryData.fromBytes(document.toByteArray())

    /**
     * Generate a deterministic UUID from the PDF content using SHA-256 hash
     */
    fun contentHash(): UUID = document.toByteArray().sha256()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        if (other !is PdfDocument) return false

        return other.inputFile == this.inputFile && isDocEqual(other)
    }

    fun asClassifiedDocument(classificationInfo: ClassificationInfo) = ClassifiedPdfDocument(
            Classification(inputFile, classificationInfo),
            document.docForPages(classificationInfo.pages)
        )

    // for unit testing
    fun isDocEqual(other: PdfDocument) = document.pages.map { it } == other.document.pages.map { it }

    override fun hashCode(): Int {
        var result = inputFile.hashCode()
        result = 31 * result + document.hashCode()
        return result
    }

    companion object {
        const val LOCK = "Lock"
        fun PDDocument.toByteArray() = synchronized(LOCK) {
            val outputStream = ByteArrayOutputStream()
            this.save(outputStream)
            outputStream.toByteArray()
        }
    }

}