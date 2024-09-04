package com.goldberg.law.document.writer

class TestCsvWriter: CsvWriter() {
    // can capture these values to check what's being passed
    var fileName: String? = null
    var content: String? = null
    override fun storeCsv(fileName: String, content: String) {
        this.fileName = fileName
        this.content = content
    }
}