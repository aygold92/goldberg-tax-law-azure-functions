package com.goldberg.law.categorization

import com.goldberg.law.categorization.chatgbt.ChatGBTClient
import com.goldberg.law.categorization.model.TransactionCategorization
import com.goldberg.law.categorization.model.TransactionVendor
import com.goldberg.law.categorization.model.VendorCategorization
import io.github.oshai.kotlinlogging.KotlinLogging

class TransactionCategorizer(private val chatGBTClient: ChatGBTClient) {
    private val logger = KotlinLogging.logger {}
    // standard bank transactions that don't need to be categorized
    private fun isIgnorable(description: String): Boolean {
        val ignoreDescriptions = listOf(
            "dividend",
            "payment received",
            "nfo payment received",
        )

        val startsWithDescriptions = listOf(
            "transfer to",
            "transfer from",
            "interest charge",
            "check",
            "deposit -",
            "withdrawal by",
            "atm withdrawal", "atm fee",
        )
        val text = description.lowercase();
        return text.isBlank() || ignoreDescriptions.contains(text) || startsWithDescriptions.any { text.startsWith(it) };
    }

    fun batchCategorizeTransactions(descriptions: List<String>, batchSize: Int = 50): List<TransactionCategorization> {
        // 1. Attach original indices
        val indexedDescriptions = descriptions.mapIndexed { index, desc -> IndexedValue(index, desc) }

        // 2. Partition into ignorable and classifiable
        val (ignorable, toClassify) = indexedDescriptions.partition { isIgnorable(it.value) }

        // 3. Prepare result list with Unclassified placeholders
        val result = MutableList(descriptions.size) { idx ->
            TransactionCategorization(descriptions[idx], "", "Unclassified", "", 10, 10)
        }

        // 4. Chunk non-ignorable into batches of 10
        toClassify
            .chunked(batchSize)
            .forEach { batch ->
                val texts = batch.map { it.value }
                logger.info { "categorizing ${texts.size} transactions..." }
                val categorizations = chatGBTClient.categorizeTransactions(texts)
                logger.info { "categorized transactions: $categorizations" }

                // 5. Write each result into correct original position
                batch.zip(categorizations).forEach { (indexed, cat) ->
                    result[indexed.index] = cat
                }
            }

        return result
    }

    fun batchCategorizeWithVendor(descriptions: List<String>, vendorBatchSize: Int = 100, categoryBatchSize: Int = 50): List<TransactionCategorization> {
        // 1. Attach original indices
        val indexedDescriptions = descriptions.mapIndexed { index, desc -> IndexedValue(index, desc) }

        // 2. Partition into ignorable and classifiable
        val (ignorable, toClassify) = indexedDescriptions.partition { isIgnorable(it.value) }

        // 3. Prepare result list with Unclassified placeholders
        val result = MutableList(descriptions.size) { idx ->
            TransactionCategorization(descriptions[idx], "", "Unclassified", "", 10,10)
        }

        val descriptionToVendor: MutableMap<String, TransactionVendor> = mutableMapOf()
        val vendorToCategorization: MutableMap<String, VendorCategorization> = mutableMapOf()

        // 4. Chunk non-ignorable into batches
        toClassify
            .chunked(vendorBatchSize)
            .forEach { batch ->
                val texts = batch.map { it.value }
                logger.info { "extracting vendor from ${texts.size} transactions..." }
                val vendors = chatGBTClient.extractVendors(texts)
                logger.info { "extracted vendors: $vendors" }
                descriptionToVendor.putAll(vendors)
            }

        descriptionToVendor.map { it.value.vendor }.distinct()
            .chunked(categoryBatchSize)
            .forEach { batch ->
                logger.info { "categorizing ${batch.size} vendors..." }
                val categorizations = chatGBTClient.categorizeFromVendorName(batch)
                logger.info { "categorized vendors: $categorizations" }
                vendorToCategorization.putAll(categorizations)
            }

        indexedDescriptions.forEach {
            val vendor = descriptionToVendor[it.value]!!
            val categorization = vendorToCategorization[vendor.vendor]!!
            result[it.index] = TransactionCategorization(
                description = it.value,
                vendor = vendor.vendor,
                category = categorization.category,
                subcategory = categorization.subcategory,
                categorizationConfidence = categorization.confidence,
                vendorConfidence = vendor.confidence
            ) }

        return result
    }
}