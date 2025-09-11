package com.goldberg.law.chatgbt

import com.goldberg.law.categorization.chatgbt.ChatGBTClient
import com.goldberg.law.categorization.TransactionCategorizer
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Ignore

class ChatGBTClientTest {
    @Test
    @Ignore
    fun test() {
        val chatGBTClient = ChatGBTClient("",)

        val transactionCategorizer = TransactionCategorizer(chatGBTClient)

        val transactions = readFileRelative("/com/goldberg/law/script/descriptions.txt").split("\n")
        val result = transactionCategorizer.batchCategorizeTransactions(transactions)

        println(result.joinToString("\n") { listOf(it.description, it.vendor, it.category, it.subcategory, it.categorizationConfidence).joinToString(", ") })
    }

    @Test
    @Ignore
    fun testVendors() {
        val chatGBTClient = ChatGBTClient("",)

        val transactionCategorizer = TransactionCategorizer(chatGBTClient)

        val transactions = readFileRelative("/com/goldberg/law/script/descriptions.txt").split("\n")
        println(transactions)
        val result = transactionCategorizer.batchCategorizeWithVendor(transactions)

        println(result.joinToString("\n") { listOf(it.description, it.vendor, it.category, it.subcategory, it.categorizationConfidence).joinToString(", ") })
    }

    fun readFileRelative(filename: String): String =
        Files.readString(Paths.get(javaClass.getResource(filename)!!.toURI()))
}