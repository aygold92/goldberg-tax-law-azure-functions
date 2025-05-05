package com.goldberg.law.splitpdftool

import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.util.getDocumentName
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
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

        if (req.isSeparate) {
            val splitDocuments = pdfSplitter.splitPdf(req.inputFile, fullPdf, req.getDesiredPages() ?: emptyList())
                .also { logger.debug { "Successfully loaded file ${req.inputFile}, pages ${req.getDesiredPages() ?: "[All]"}" } }

            // TODO: add ability to choose whether to process into one document or a single document with all selected pages
            splitDocuments.forEach { document ->
                val fileName = listOfNotNull(req.outputDirectory, document.splitPageFilePath()).joinToString("/")
                Files.createDirectories(Paths.get(fileName.substringBeforeLast("/")))
                document.saveToFile(fileName)
                    .also { logger.info { "Saved to file $fileName" } }
            }
        } else {
            val newDoc = pdfSplitter.splitPdfOneDocument(req.inputFile, fullPdf, req.getDesiredPages() ?: emptyList())
            val fileWithPages = "${req.inputFile.getDocumentName()}[${req.getDesiredPages()?.first()}-${req.getDesiredPages()?.last()}].pdf"
            val fileName = listOfNotNull(req.outputDirectory, req.inputFile.getDocumentName(), fileWithPages).joinToString("/")
            Files.createDirectories(Paths.get(fileName.substringBeforeLast("/")))
            newDoc.save(fileName)
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