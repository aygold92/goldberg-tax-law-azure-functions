package com.goldberg.law.datamanager

import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.exception.InvalidArgumentException
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.document.PdfSplitter
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.toStringDetailed
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths

class FileDataManager(pdfSplitter: PdfSplitter): DataManager(pdfSplitter) {
    private fun writeFile(fileName: String, content: String): String {
        logger.info { "Writing data to file $fileName" }
        logger.debug { content }
        Files.createDirectories(Paths.get(fileName).parent)
        File(fileName).writeText(content)
        return fileName
    }

    override fun saveBankStatement(bankStatement: BankStatement, outputDirectory: String?): String = writeFile(
        listOfNotNull(outputDirectory, bankStatement.fileName()).joinToString("/"),
        bankStatement.toStringDetailed()
    )

    override fun saveModel(fileName: String, content: String): String = writeFile(fileName, content)

    override fun saveCsvOutput(fileName: String, content: String): String = writeFile(fileName, content)

    override fun savePdfPage(document: PdfDocumentPage, overwrite: Boolean, outputDirectory: String?) {
        val fileName = listOfNotNull(outputDirectory, document.splitPageFilePath()).joinToString("/")
        document.saveToFile(fileName).also { logger.info { "Saved to file $fileName" } }
    }

    private fun loadFile(fileName: String) = File(fileName).let {
        if (it.exists()) it.readBytes() else throw FileNotFoundException("File $fileName not found")
    }

    override fun loadInputFile(fileName: String): ByteArray = loadFile(fileName)
    override fun loadSplitPdfDocumentFile(fileName: String): ByteArray = loadFile(fileName)
    override fun loadBankStatement(bankStatementKey: String, outputDirectory: String?): BankStatement {
        val fileName = listOfNotNull(outputDirectory, "${bankStatementKey}.json").joinToString("/")
        return OBJECT_MAPPER.readValue(loadFile(fileName), BankStatement::class.java)
    }

    override fun findMatchingFiles(fileName: String): Set<String> {
        val input = File(fileName).let { if (it.exists()) it else throw FileNotFoundException("File $fileName not found") }

        return if (input.isPdfFile()) {
            setOf(fileName)
        } else if (input.isDirectory) {
            logger.debug { "reading directory ${input.absolutePath}" }
            // note: not recursive
            input.listFiles()!!.mapNotNull { file ->
                if (file.isDirectory) {
                    null.also { logger.debug { "skipping ${file.name} as it is a directory" } }
                } else if (file.isPdfFile()) {
                    fileName + "/" + file.name
                } else {
                    null.also { logger.debug { "skipping ${file.name} as it is not a PDF" } }
                }
            }.toSet().ifEmpty { throw InvalidArgumentException("No PDF files found for $fileName") }
        } else {
            throw InvalidArgumentException("File $input is neither a PDF file nor directory")
        }
    }

    fun File.isPdfFile(): Boolean {
        return this.isFile && FileInputStream(this).use { inputStream ->
            val headerBytes = ByteArray(5)
            inputStream.read(headerBytes) == 5 && String(headerBytes) == "%PDF-"
        }
    }

    override fun checkFilesExist(requestedFileNames: Set<String>): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getProcessedFiles(fileNames: Set<String>): Set<String> {
        TODO("Not yet implemented")
    }
}