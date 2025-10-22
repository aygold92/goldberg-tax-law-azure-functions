package com.goldberg.law.util

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.azure.ai.documentintelligence.models.DocumentField
import com.azure.ai.documentintelligence.models.DocumentFieldType
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.nimbusds.jose.shaded.gson.GsonBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.apache.pdfbox.pdmodel.PDDocument
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.NumberFormat
import java.time.LocalDate
import java.util.*

val OBJECT_MAPPER = ObjectMapper().apply {
    enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    registerModule(SimpleModule().apply { addSerializer(DocumentField::class.java, DocumentFieldSerializer()) })
    registerModule(SimpleModule().apply { addSerializer(AnalyzedDocument::class.java, AnalyzedDocumentSerializer()) })
    registerModule(JavaTimeModule())

}

val GSON = GsonBuilder()
    .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
    .registerTypeAdapter(DocumentFieldType::class.java, DocumentFieldTypeAdaptor())
    .create()

fun <T> String.readJson(clazz: Class<T>): T = GSON.fromJson(this, clazz)

private val logger = KotlinLogging.logger {}

val ZERO: BigDecimal = 0.asCurrency()

fun Any.toStringDetailed(): String {
    return OBJECT_MAPPER.writeValueAsString(this)
}

fun DocumentField.toStringDetailed(): String = GSON.toJson(this)

// TODO: if the file ends with ".json.pdf" it will remove both
fun String.withoutExtension() = removeSuffix(".pdf").removeSuffix(".json").removeSuffix(".csv")
fun String.withExtension(ext: String) = removeSuffix(ext) + ext

// without extension
fun String.getDocumentName() = this.substringAfterLast("/").substringBeforeLast(".")

fun String.last4Digits() = this.filter { str ->
    str.isDigit() }.let {
    if (it.length > 4) it.substring(it.length - 4) else it
}

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
    .filterIndexed { idx, ch -> ch.isLetterOrDigit() || (ch == '-' && idx != 1) ||  listOf('.', '$').contains(ch) }.let { value ->
        if (value.trim().endsWith("-")) {
            "-${value.trim().dropLast(1)}" // Move the minus sign to the front
        } else {
            value
        }
    }

fun DocumentField.valueAsInt(): Int? = this.valueInteger?.toInt()

fun String.parseCurrency(): BigDecimal? = this.asCurrency()
    ?: this.hackToNumber().let {
        it.asCurrency() ?: try {
            NumberFormat.getCurrencyInstance(Locale.US).parse(it).asCurrency()
        } catch (e: Exception) {
            null.also { logger.error { "Unable to parse $this to currency value: $e" } }
        }
    }

fun String.removeNonDigitOrSlash(): String {
    return this.replace(Regex("[^0-9/]"), "")
}

fun DocumentField.currencyValue(): BigDecimal? = this.content?.parseCurrency()?.let { contentValue ->
        val doubleValue = try {
            // TODO: how does this behave?
            this.valueNumber?.asCurrency() ?: this.valueCurrency?.amount?.asCurrency() ?: throw NullPointerException("Cannot process ${this.toStringDetailed()} as double")
        } catch (ex: Throwable) {
            if (ex is ClassCastException || ex is NullPointerException)
                contentValue.also { logger.error { "value ${this.toStringDetailed()} cannot be processed as a double" } }
            else throw ex
        }
        // fixes bug where sometimes randomly the value becomes off by some very small amount -- in this case the content is accurate.
        // for example, content says "$534,446.46 and the double value randomly is 53446.4375
        if ((contentValue.abs() - doubleValue.abs()).abs() < .05.asCurrency()) {
            contentValue
        }
        // fixes bug where "- $5,000.00" misses the subtraction sign
        else if (contentValue < ZERO && contentValue.abs() == doubleValue.abs()) {
            contentValue
        } else doubleValue
    }


// some values are always positive (such as interest charged).  If a negative is read, it would be a mistake
fun DocumentField.positiveCurrencyValue(): BigDecimal? = this.currencyValue()?.abs()

fun DocumentField.pageNumber(): Int = this.boundingRegions[0]?.pageNumber!!

fun Map<String, DocumentField>.pageNumber(): Int = this.values.first().pageNumber()

fun String.addQuotes() = '"' + this + '"'

fun <T> Collection<T>.breakIntoGroups(numGroups: Int): Map<Int, List<T>> {
    return if (numGroups == 0) mapOf(0 to this.toList())
        else this.mapIndexed { idx, it -> idx % numGroups to it }.groupBy({ it.first }, { it.second })
}

fun PDDocument.docForPage(pageNum: Int) = PDDocument().apply { addPage(this@docForPage.getPage(pageNum - 1)) }
fun PDDocument.docForPages(pages: Set<Int>) = PDDocument().apply {
    pages.sorted().forEach {
        addPage(this@docForPages.getPage(it - 1))
    }
}

fun <T, R> Collection<T>.mapAsync(dispatcher: CoroutineDispatcher = Dispatchers.IO, block: (T) -> R): List<R> = runBlocking {
    this@mapAsync.map {
        async(dispatcher) {
            block(it)
        }
    }.awaitAll()
}

fun <K, V, R> Map<K, V>.mapAsync(dispatcher: CoroutineDispatcher = Dispatchers.IO, block: (K, V) -> R): List<R> = runBlocking {
    this@mapAsync.map {
        async(dispatcher) {
            block(it.key, it.value)
        }
    }.awaitAll()
}

fun <K, V, NK, NV> Map<K, V>.associate(block: (key: K, value: V) -> Pair<NK, NV> ) = this.map { block(it.key, it.value) }
    .associate { it.first to it.second }

@OptIn(ExperimentalStdlibApi::class)
fun Any.md5Hash(): String {
    val digest = MessageDigest.getInstance("MD5")
    val hashBytes = digest.digest(this.toStringDetailed().toByteArray(charset = Charsets.US_ASCII))
    return hashBytes.toHexString()
}