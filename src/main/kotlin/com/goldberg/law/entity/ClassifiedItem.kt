package com.goldberg.law.entity

import org.jetbrains.exposed.sql.ResultRow

sealed class ClassifiedItem {
    abstract val classification: Classification
}

data class ClassifiedStatement(
    override val classification: Classification,
    val statementDetails: StatementDetails,
) : ClassifiedItem() {
    companion object {
        fun fromRow(row: ResultRow) = ClassifiedStatement(
            classification = Classification.fromRow(row),
            statementDetails = StatementDetails.fromRow(row)
        )
    }
}

data class ClassifiedCheck(
    override val classification: Classification,
    val checkDetails: CheckDetails,
) : ClassifiedItem() {
    companion object {
        fun fromRow(row: ResultRow) = ClassifiedCheck(
            classification = Classification.fromRow(row),
            checkDetails = CheckDetails.fromRow(row)
        )
    }
}


