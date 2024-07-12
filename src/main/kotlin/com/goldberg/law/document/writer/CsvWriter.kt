package com.goldberg.law.document.writer

import com.goldberg.law.document.model.TransactionHistoryPage

abstract class CsvWriter {
    fun writeRecordsToCsv(fileName: String, pages: List<TransactionHistoryPage>) {
        val content = pages.joinToString("\n") { it.toCsv() }
        storeCsv(fileName, content)
    }

    // TODO: what should content be?
    protected abstract fun storeCsv(fileName: String, content: String)
}