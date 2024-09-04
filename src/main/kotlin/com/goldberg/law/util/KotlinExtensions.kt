package com.goldberg.law.util

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

private val OBJECT_MAPPER = ObjectMapper().apply {
    registerModule(SimpleModule().apply { addSerializer(DocumentField::class.java, DocumentFieldSerializer()) })
    registerModule(SimpleModule().apply { addSerializer(AnalyzedDocument::class.java, AnalyzedDocumentSerializer()) })
}
private val logger = KotlinLogging.logger {}

// TODO: can I remove boundingRegions and spans properties?
fun Any.toStringDetailed(): String {
    return OBJECT_MAPPER.writeValueAsString(this)
}

fun String.withoutPdfExtension() = removeSuffix(".pdf")

fun String.getFilename() = File(this).name.withoutPdfExtension()

fun Double.toCurrency() = "%.2f".format(this)

fun Double.roundToTwoDecimalPlaces(): Double {
    return BigDecimal(this).setScale(2, RoundingMode.HALF_UP).toDouble()
}

fun Map<String?, *>.valueNotNull(key: String): Boolean = this.containsKey(key) && this.get(key) != null

fun String.hackToNumber(): String = this.trim()
    .filterIndexed { idx, ch -> ch.isLetterOrDigit() || (ch == '-' && idx != 1) ||  listOf('.', '$').contains(ch) }

fun DocumentField.valueAsInt(): Int? = try {
    this.valueAsLong.toInt()
} catch (cce: ClassCastException) {
    this.value as? Int
}

fun String.parseCurrency(): Double? {
    return this.toDoubleOrNull() ?: this.hackToNumber().let {
        it.toDoubleOrNull() ?: try {
            NumberFormat.getCurrencyInstance(Locale.US).parse(it)?.toDouble()?.roundToTwoDecimalPlaces()
        } catch (e: Exception) {
            null.also { logger.error { "Unable to parse $this to currency value: $e" } }
        }
    }
}

fun String.addQuotes() = '"' + this + '"'

fun Double.plusSafe(other: Double) = plus(other).roundToTwoDecimalPlaces()
fun Double.minusSafe(other: Double) = minus(other).roundToTwoDecimalPlaces()
