package com.goldberg.law.document.writer

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

class FileCsvWriter: CsvWriter() {
    private val logger = KotlinLogging.logger {}

    override fun storeCsv(fileName: String, content: String) {
        logger.info { "Writing csv to file $fileName.csv" }
        logger.debug { content }
        File("$fileName.csv").writeText(content)
    }
}