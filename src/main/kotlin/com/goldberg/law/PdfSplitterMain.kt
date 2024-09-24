package com.goldberg.law

import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.datamanager.FileDataManager
import com.goldberg.law.document.AnalyzeDocumentRequest
import com.goldberg.law.document.PdfSplitter
import javax.inject.Inject

class PdfSplitterMain@Inject constructor(
    private val dataManager: DataManager,
) {
    fun run(req: AnalyzeDocumentRequest) {
        val splitDocuments = dataManager.loadInputPdfDocumentPages(req.inputFile, req.getDesiredPages())

        splitDocuments.forEach {
            dataManager.savePdfPage(it, true, req.outputDirectory)
        }
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val req = AnalyzeDocumentRequest().parseArgs(args)

            PdfSplitterMain(FileDataManager(PdfSplitter())).run(req)
        }
    }
}