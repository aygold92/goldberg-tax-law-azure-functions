package com.goldberg.law.util

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.apache.pdfbox.pdmodel.PDDocument
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.NumberFormat
import java.util.*

val OBJECT_MAPPER = ObjectMapper().apply {
    enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    registerModule(SimpleModule().apply { addSerializer(DocumentField::class.java, DocumentFieldSerializer()) })
    registerModule(SimpleModule().apply { addSerializer(AnalyzedDocument::class.java, AnalyzedDocumentSerializer()) })
}
private val logger = KotlinLogging.logger {}

val ZERO: BigDecimal = 0.asCurrency()

fun Any.toStringDetailed(): String {
    return OBJECT_MAPPER.writeValueAsString(this)
}

// TODO: if the file ends with ".json.pdf" it will remove both
fun String.withoutExtension() = removeSuffix(".pdf").removeSuffix(".json").removeSuffix(".csv")

// without extension
fun String.getDocumentName() = this.substringAfterLast("/").substringBeforeLast(".")

fun BigDecimal.toCurrency(): String = "%.2f".format(this)

fun Double.asCurrency(): BigDecimal = BigDecimal(this).setScale(2, RoundingMode.HALF_UP)
fun Int.asCurrency(): BigDecimal = BigDecimal(this).setScale(2, RoundingMode.HALF_UP)
fun String.asCurrency(): BigDecimal? = try {
    BigDecimal(this).setScale(2, RoundingMode.HALF_UP)
} catch (ex: NumberFormatException) {
    null
}

fun Number.asCurrency(): BigDecimal? = BigDecimal(this.toString()).setScale(2, RoundingMode.HALF_UP)

fun Map<String?, *>.valueNotNull(key: String): Boolean = this.containsKey(key) && this.get(key) != null

fun String.hackToNumber(): String = this.trim()
    .filterIndexed { idx, ch -> ch.isLetterOrDigit() || (ch == '-' && idx != 1) ||  listOf('.', '$').contains(ch) }

fun DocumentField.valueAsInt(): Int? = try {
    this.valueAsLong.toInt()
} catch (ex: Exception) {
    (this.value as? Number)?.toInt()
}

fun String.parseCurrency(): BigDecimal? = this.asCurrency()
    ?: this.hackToNumber().let {
        it.asCurrency() ?: try {
            NumberFormat.getCurrencyInstance(Locale.US).parse(it).asCurrency()
        } catch (e: Exception) {
            null.also { logger.error { "Unable to parse $this to currency value: $e" } }
        }
    }


fun DocumentField.currencyValue(): BigDecimal? = this.content?.parseCurrency()?.let { contentValue ->
        val doubleValue = try {
            this.valueAsDouble.asCurrency()
        } catch (ex: ClassCastException) {
            contentValue.also { logger.error { "value ${this.toStringDetailed()} cannot be processed as a double" } }
        }
        // fixes bug where sometimes randomly the value becomes off by .01 -- in this case the content is accurate
        if ((contentValue.abs() - doubleValue.abs()).abs() == .01.asCurrency()) {
            contentValue
        }
        // fixes bug where "- $5,000.00" misses the subtraction sign
        else if (contentValue < ZERO && contentValue.abs() == doubleValue.abs()) {
            contentValue
        } else doubleValue
    }


// some values are always positive (such as interest charged).  If a negative is read, it would be a mistake
fun DocumentField.positiveCurrencyValue(): BigDecimal? = this.currencyValue()?.abs()

fun String.addQuotes() = '"' + this + '"'

fun <T> Collection<T>.breakIntoGroups(numGroups: Int): Map<Int, List<T>> {
    return this.mapIndexed { idx, it -> idx % numGroups to it }.groupBy({ it.first }, { it.second })
}

fun PDDocument.docForPage(pageNum: Int) = PDDocument().apply { addPage(this@docForPage.getPage(pageNum - 1)) }

fun <T, R> Collection<T>.mapAsync(dispatcher: CoroutineDispatcher = Dispatchers.IO, block: (T) -> R): List<R> = runBlocking {
    this@mapAsync.map {
        async(dispatcher) {
            block(it)
        }
    }.awaitAll()
}

@OptIn(ExperimentalStdlibApi::class)
fun Any.md5Hash(): String {
    val digest = MessageDigest.getInstance("MD5")
    val hashBytes = digest.digest(this.toStringDetailed().toByteArray(charset = Charsets.US_ASCII))
    return hashBytes.toHexString()
}