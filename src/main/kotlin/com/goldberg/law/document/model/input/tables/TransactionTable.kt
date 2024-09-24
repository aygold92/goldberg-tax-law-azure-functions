package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import kotlin.collections.LinkedHashMap

abstract class TransactionTable(@JsonIgnore @Transient open val records: List<TransactionRecord>) {
    fun createHistoryRecords(statementDate: Date?, metadata: TransactionHistoryPageMetadata): List<TransactionHistoryRecord> = records.map {
        it.toTransactionHistoryRecord(statementDate, metadata)
    }
}
abstract class TransactionRecord {
    @JsonIgnore @Transient
    val logger = KotlinLogging.logger {}
    abstract fun toTransactionHistoryRecord(statementDate: Date?, metadata: TransactionHistoryPageMetadata): TransactionHistoryRecord

    // TODO: need the full statement date to check if it's a january statement
    fun fromWrittenDateStatementDateOverride(monthDay: String?, statementDate: Date?): String? {
        val date = fromWrittenDate(monthDay, statementDate?.getYearSafe())
        return (if (date != null && date.getMonthInt() == 11 && statementDate?.getMonthInt() == 0) {
            Calendar.getInstance().apply {
                time = date
                add(Calendar.YEAR, -1)
            }.time
        } else date)?.toTransactionDate()
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
