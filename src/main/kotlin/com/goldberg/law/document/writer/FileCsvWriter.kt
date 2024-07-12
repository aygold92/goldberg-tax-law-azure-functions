package com.goldberg.law.document.writer

import java.io.File

class FileCsvWriter: CsvWriter() {
    override fun storeCsv(fileName: String, content: String) {
        // TODO: write to file
        println("Writing csv to file $fileName.csv")
        println(content)
        File("$fileName.csv").writeText(content)
    }
}