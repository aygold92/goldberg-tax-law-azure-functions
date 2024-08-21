package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.document.model.input.tables.TransactionRecord.Companion.OBJECT_MAPPER
import com.goldberg.law.document.model.output.TransactionHistoryRecord

abstract class TransactionTable(val records: List<TransactionRecord>) {
    fun getHistoryRecords(statementYear: String?): List<TransactionHistoryRecord> = records.map {
        it.toTransactionHistoryRecord(statementYear)
    }

    companion object {
        val OBJECT_MAPPER = ObjectMapper()
    }
}
abstract class TransactionRecord {
    abstract fun toTransactionHistoryRecord(statementYear: String?): TransactionHistoryRecord
    companion object {
        val OBJECT_MAPPER = ObjectMapper()
    }
}

// hack for unit tests needed
fun DocumentField.getFieldMapHack(): Map<String, DocumentField> = (this.value as Map<*, *>).let { recordMap ->
    if (recordMap.values.first() is LinkedHashMap<*, *>) {
        recordMap.map { entry ->
            entry.key as String to entry.value!!.asDocumentField()
        }.toMap()
    } else {
        recordMap as Map<String, DocumentField>
    }
}

// hack for unit tests
fun Any.asDocumentField(): DocumentField =
    if (this is LinkedHashMap<*,*>)
        OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(this), DocumentField::class.java)
    else
        this as DocumentField
