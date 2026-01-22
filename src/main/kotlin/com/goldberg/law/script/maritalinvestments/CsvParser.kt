package com.goldberg.law.script.maritalinvestments

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.StringReader

class CsvParser {
    fun parse(csvString: String): List<CSVRecord> = StringReader(csvString).use { reader ->
        CSV_PARSER.parse(reader).records
    }

    companion object {
        private val CSV_PARSER = CSVFormat.DEFAULT
    }
}