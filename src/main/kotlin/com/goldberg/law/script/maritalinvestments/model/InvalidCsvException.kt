package com.goldberg.law.script.maritalinvestments.model

import org.apache.commons.csv.CSVRecord

open class InvalidCsvException(
    val lineNumber: Int,
    message: String,
    val line: String,
    cause: Throwable? = null
): RuntimeException("[$lineNumber] $message: $line", cause) {

    constructor(csvRecord: CSVRecord, message: String): this(csvRecord.recordNumber.toInt(), message, csvRecord.values().joinToString())
}

class InvalidCsvFieldException(
    lineNumber: Int,
    val field: String,
    line: String,
): InvalidCsvException(lineNumber, "Invalid field $field", line) {
    constructor(csvRecord: CSVRecord, field: String): this(csvRecord.recordNumber.toInt(), field, csvRecord.values().joinToString())
}