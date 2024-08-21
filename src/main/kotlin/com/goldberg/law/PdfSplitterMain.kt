package com.goldberg.law

import com.goldberg.law.document.AnalyzeDocumentRequest
import com.goldberg.law.pdf.PdfSplitter
import com.goldberg.law.pdf.loader.FilePdfLoader
import com.goldberg.law.pdf.loader.PdfLoader
import com.goldberg.law.pdf.writer.FilePdfWriter
import com.goldberg.law.pdf.writer.PdfWriter
import javax.inject.Inject

class PdfSplitterMain@Inject constructor(
    private val pdfLoader: PdfLoader,
    private val splitter: PdfSplitter,
    private val pdfWriter: PdfWriter
) {
    fun run(req: AnalyzeDocumentRequest) {
        val inputDocument = pdfLoader.loadPdf(req.inputFile)

        val splitDocuments = splitter.splitPdf(inputDocument, req.getDesiredPages(), req.isSeparate)

        splitDocuments.forEach {
            pdfWriter.writePdf(it, req.outputDirectory)
        }
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val req = AnalyzeDocumentRequest().parseArgs(args)

            PdfSplitterMain(FilePdfLoader(), PdfSplitter(), FilePdfWriter()).run(req)
        }
    }
}