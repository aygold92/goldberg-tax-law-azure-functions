package com.goldberg.law.splitpdftool

import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.exception.InvalidPdfException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.io.File
import javax.inject.Inject

/**
 * Local command line utility for splitting a PDF document into separate single page documents
 */
class PdfSplitterMain@Inject constructor(
    private val pdfSplitter: PdfSplitter,
) {
    private val logger = KotlinLogging.logger {}

    fun run(req: SplitDocumentRequest) {
        val bytes = File(req.inputFile).let {
            if (it.exists()) it.readBytes() else throw FileNotFoundException("File ${req.inputFile} not found")
        }

        val fullPdf = try {
            Loader.loadPDF(bytes)
        } catch (ex: Exception) {
            throw InvalidPdfException("Unable to load PDF ${req.inputFile}: $ex")
        }

        val splitDocuments = pdfSplitter.splitPdf(req.inputFile, fullPdf, req.getDesiredPages())
            .also { logger.debug { "Successfully loaded file ${req.inputFile}, pages ${req.getDesiredPages() ?: "[All]"}" } }

        // TODO: add ability to choose whether to process into one document or a single document with all selected pages
        splitDocuments.forEach { document ->
            val fileName = listOfNotNull(req.outputDirectory, document.splitPageFilePath()).joinToString("/")
            document.saveToFile(fileName)
                .also { logger.info { "Saved to file $fileName" } }
        }
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val req = SplitDocumentRequest().parseArgs(args)

            PdfSplitterMain(PdfSplitter()).run(req)
        }
    }
}