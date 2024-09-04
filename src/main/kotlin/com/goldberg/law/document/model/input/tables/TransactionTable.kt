package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.document.model.input.tables.TransactionRecord.Companion.OBJECT_MAPPER
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.getMonthInt
import com.goldberg.law.util.getYearInt
import com.goldberg.law.util.getYearSafe
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import kotlin.collections.LinkedHashMap

abstract class TransactionTable(val records: List<TransactionRecord>) {
    fun getHistoryRecords(statementYear: String?, metadata: TransactionHistoryPageMetadata): List<TransactionHistoryRecord> = records.map {
        it.toTransactionHistoryRecord(statementYear, metadata)
    }
}
abstract class TransactionRecord {
    val logger = KotlinLogging.logger {}
    abstract fun toTransactionHistoryRecord(statementYear: String?, metadata: TransactionHistoryPageMetadata): TransactionHistoryRecord

    fun fromWrittenDateStatementYearOverride(monthDay: String?, statementYear: String?): Date? {
        val date = fromWrittenDate(monthDay, statementYear)
        return if (date != null && date.getMonthInt() == 11 && date.getYearSafe() == statementYear) {
            Calendar.getInstance().apply {
                time = date
                add(Calendar.YEAR, -1)
            }.time
        } else date
    }
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
