package com.goldberg.law.document.writer

import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.document.PdfSplitter
import com.goldberg.law.function.model.InputFileMetadata

class TestDataManager: DataManager(PdfSplitter()) {
    // can capture these values to check what's being passed
    var fileName: String? = null
    var content: String? = null
    override fun saveCsvOutput(fileName: String, content: String): String {
        this.fileName = fileName
        this.content = content
        return fileName
    }

    override fun saveModel(fileName: String, content: String): String {
        TODO("Not yet implemented")
    }

    override fun saveBankStatement(bankStatement: BankStatement, outputDirectory: String?): String {
        TODO("Not yet implemented")
    }

    override fun savePdfPage(document: PdfDocumentPage, overwrite: Boolean, outputDirectory: String?) {
        TODO("Not yet implemented")
    }

    override fun loadSplitPdfDocumentFile(fileName: String): ByteArray {
        TODO("Not yet implemented")
    }
    override fun loadInputFile(fileName: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun loadBankStatement(bankStatementKey: String, outputDirectory: String?): BankStatement {
        TODO("Not yet implemented")
    }

    override fun updateInputPdfMetadata(fileName: String, metadata: InputFileMetadata) {
        TODO("Not yet implemented")
    }

    override fun findMatchingFiles(fileName: String): Set<String> {
        TODO("Not yet implemented")
    }

    override fun checkFilesExist(requestedFileNames: Set<String>): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getProcessedFiles(fileNames: Set<String>): Set<String> {
        TODO("Not yet implemented")
    }

    companion object {
        const val FILE_THAT_EXISTS = "TestFile.pdf"
    }
}