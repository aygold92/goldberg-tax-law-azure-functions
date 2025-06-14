package com.goldberg.law.document

import com.goldberg.law.categorization.TransactionCategorizer
import com.goldberg.law.categorization.chatgbt.ChatGBTClient
import com.goldberg.law.categorization.model.TransactionCategorization
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionCategorizerTest {
    private val chatGBTClient: ChatGBTClient = mock()
    private val transactionCategorizer = TransactionCategorizer(chatGBTClient)
    @Test
    fun testBatches() {
        whenever(chatGBTClient.categorizeTransactions(any())).thenAnswer { invocation ->
            val inputDescriptions = invocation.getArgument<List<String>>(0)
            inputDescriptions.map { desc ->
                TransactionCategorization(
                    description = desc,
                    category = "test",
                    subcategory = "test",
                    vendor = "test",
                    categorizationConfidence = 10,
                    vendorConfidence = 10
                )
            }
        }

        val descriptions = listOf(
            "POS Debit- Debit Card 0553 04-22-24 Starbucks Store 61 Gaithersburg MD",
            "Withdrawal by Check",
            "POS Debit- Debit Card 0553 04-22-24 Shell Oil 57542185 Olney MD",
            "POS Debit- Debit Card 0553 04-22-24 Habit Gaithersburg Gaithersburg MD",
            "POS Debit- Debit Card 0553 04-22-24 Shell Oil 57542185 Olney MD",
            "Withdrawal by Check",
            "POS Debit - Debit Card 0553 Transaction 04-23-24 7-Eleven Olney",
            "POS Debit- Debit Card 0553 04-23-24 P & C Complete Car Montgomery VI MD",
            "BESTBUYCOM806932638276 888BESTBUY MN",
            "AMZN Mktp US*WS7VY4EX3 Amzn.com/billWA",
            "AMZN Mktp US*J10861XK3 Amzn.com/billWA",
            "POS Debit - Debit Card 0553 Transaction 04-25-24 7-Eleven Olney",

            "POS Debit- Debit Card 0553 04-24-24 Convenient Beer An Gaithersburg MD",
            "POS Debit- Debit Card 0553 04-23-24 Starbucks Store 61 Gaithersburg MD",
            "POS Debit- Debit Card 0553 04-23-24 Starbucks Store 61 Gaithersburg MD",
            "POS Debit - Debit Card 0553 Transaction 04-24-24 7-Eleven Olney",
            "POS Debit- Debit Card 0553 04-24-24 Wegmans Germantown Germantown MD",
            "POS Debit - Debit Card 0553 Transaction 04-24-24 At Home Store 2 Gaithersburg",
            "POS Debit - Debit Card 0553 Transaction 04-24-24 Snouffer School Gaithersburg",
            "Amazon.com*KI76A6KJ3 Amzn.com/billWA",
            "Amazon.com*1B0VZ6N53 Amzn.com/billWA",
            "AMZN Mktp US*5U6L48VC3 Amzn.com/billWA",

            "AMZN Mktp US*A18K020O3 Amzn.com/billWA",
            "Deposit - ACH Paid From C26459 Veracity Dir Dep 01Afd4",
            "POS Debit- Debit Card 0553 04-24-24 Starbucks Store 07 Germantown MD",
            "POS Debit - Debit Card 0553 Transaction 04-26-24 The Home Depot #2560 Germantown MD",
            "POS Debit - Debit Card 0553 Transaction 04-26-24 Wal-Mart #2357 Germantown",
            "POS Debit - Debit Card 0553 Transaction 04-26-24 7-Eleven Olney",
            "POS Debit- Debit Card 0553 04-25-24 Tj Maxx #277 Olney MD",
            "Transfer To Credit Card",
            "POS Debit- Debit Card 0553 04-25-24 Big Lots Stores - Gaithersburg MD",
            "PAYMENT RECEIVED",
            "JOSEPH GREENWALD AND LAAK301-220-2200 MD",
        )

        val response = transactionCategorizer.batchCategorizeTransactions(descriptions, 10)

        assertThat(response).isEqualTo(listOf(
            TransactionCategorization("POS Debit- Debit Card 0553 04-22-24 Starbucks Store 61 Gaithersburg MD", "test", "test", "test", 10, 10),
            TransactionCategorization("Withdrawal by Check", "", "Unclassified", "", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-22-24 Shell Oil 57542185 Olney MD", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-22-24 Habit Gaithersburg Gaithersburg MD", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-22-24 Shell Oil 57542185 Olney MD", "test", "test", "test", 10, 10),
            TransactionCategorization("Withdrawal by Check", "", "Unclassified", "", 10, 10),
            TransactionCategorization("POS Debit - Debit Card 0553 Transaction 04-23-24 7-Eleven Olney", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-23-24 P & C Complete Car Montgomery VI MD", "test", "test", "test", 10, 10),
            TransactionCategorization("BESTBUYCOM806932638276 888BESTBUY MN", "test", "test", "test", 10, 10),
            TransactionCategorization("AMZN Mktp US*WS7VY4EX3 Amzn.com/billWA", "test", "test", "test", 10, 10),
            TransactionCategorization("AMZN Mktp US*J10861XK3 Amzn.com/billWA", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit - Debit Card 0553 Transaction 04-25-24 7-Eleven Olney", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-24-24 Convenient Beer An Gaithersburg MD", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-23-24 Starbucks Store 61 Gaithersburg MD", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-23-24 Starbucks Store 61 Gaithersburg MD", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit - Debit Card 0553 Transaction 04-24-24 7-Eleven Olney", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-24-24 Wegmans Germantown Germantown MD", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit - Debit Card 0553 Transaction 04-24-24 At Home Store 2 Gaithersburg", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit - Debit Card 0553 Transaction 04-24-24 Snouffer School Gaithersburg", "test", "test", "test", 10, 10),
            TransactionCategorization("Amazon.com*KI76A6KJ3 Amzn.com/billWA", "test", "test", "test", 10, 10),
            TransactionCategorization("Amazon.com*1B0VZ6N53 Amzn.com/billWA", "test", "test", "test", 10, 10),
            TransactionCategorization("AMZN Mktp US*5U6L48VC3 Amzn.com/billWA", "test", "test", "test", 10, 10),
            TransactionCategorization("AMZN Mktp US*A18K020O3 Amzn.com/billWA", "test", "test", "test", 10, 10),
            TransactionCategorization("Deposit - ACH Paid From C26459 Veracity Dir Dep 01Afd4", "", "Unclassified", "", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-24-24 Starbucks Store 07 Germantown MD", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit - Debit Card 0553 Transaction 04-26-24 The Home Depot #2560 Germantown MD", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit - Debit Card 0553 Transaction 04-26-24 Wal-Mart #2357 Germantown", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit - Debit Card 0553 Transaction 04-26-24 7-Eleven Olney", "test", "test", "test", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-25-24 Tj Maxx #277 Olney MD", "test", "test", "test", 10, 10),
            TransactionCategorization("Transfer To Credit Card", "", "Unclassified", "", 10, 10),
            TransactionCategorization("POS Debit- Debit Card 0553 04-25-24 Big Lots Stores - Gaithersburg MD", "test", "test", "test", 10, 10),
            TransactionCategorization("PAYMENT RECEIVED", "", "Unclassified", "", 10, 10),
            TransactionCategorization("JOSEPH GREENWALD AND LAAK301-220-2200 MD", "test", "test", "test", 10, 10),
        ))

        verify(chatGBTClient).categorizeTransactions(listOf(
            "POS Debit- Debit Card 0553 04-22-24 Starbucks Store 61 Gaithersburg MD",
            "POS Debit- Debit Card 0553 04-22-24 Shell Oil 57542185 Olney MD",
            "POS Debit- Debit Card 0553 04-22-24 Habit Gaithersburg Gaithersburg MD",
            "POS Debit- Debit Card 0553 04-22-24 Shell Oil 57542185 Olney MD",
            "POS Debit - Debit Card 0553 Transaction 04-23-24 7-Eleven Olney",
            "POS Debit- Debit Card 0553 04-23-24 P & C Complete Car Montgomery VI MD",
            "BESTBUYCOM806932638276 888BESTBUY MN",
            "AMZN Mktp US*WS7VY4EX3 Amzn.com/billWA",
            "AMZN Mktp US*J10861XK3 Amzn.com/billWA",
            "POS Debit - Debit Card 0553 Transaction 04-25-24 7-Eleven Olney",
        ))
        verify(chatGBTClient).categorizeTransactions(listOf(
            "POS Debit- Debit Card 0553 04-24-24 Convenient Beer An Gaithersburg MD",
            "POS Debit- Debit Card 0553 04-23-24 Starbucks Store 61 Gaithersburg MD",
            "POS Debit- Debit Card 0553 04-23-24 Starbucks Store 61 Gaithersburg MD",
            "POS Debit - Debit Card 0553 Transaction 04-24-24 7-Eleven Olney",
            "POS Debit- Debit Card 0553 04-24-24 Wegmans Germantown Germantown MD",
            "POS Debit - Debit Card 0553 Transaction 04-24-24 At Home Store 2 Gaithersburg",
            "POS Debit - Debit Card 0553 Transaction 04-24-24 Snouffer School Gaithersburg",
            "Amazon.com*KI76A6KJ3 Amzn.com/billWA",
            "Amazon.com*1B0VZ6N53 Amzn.com/billWA",
            "AMZN Mktp US*5U6L48VC3 Amzn.com/billWA",
        ))

        verify(chatGBTClient).categorizeTransactions(listOf(
            "AMZN Mktp US*A18K020O3 Amzn.com/billWA",
            "POS Debit- Debit Card 0553 04-24-24 Starbucks Store 07 Germantown MD",
            "POS Debit - Debit Card 0553 Transaction 04-26-24 The Home Depot #2560 Germantown MD",
            "POS Debit - Debit Card 0553 Transaction 04-26-24 Wal-Mart #2357 Germantown",
            "POS Debit - Debit Card 0553 Transaction 04-26-24 7-Eleven Olney",
            "POS Debit- Debit Card 0553 04-25-24 Tj Maxx #277 Olney MD",
            "POS Debit- Debit Card 0553 04-25-24 Big Lots Stores - Gaithersburg MD",
            "JOSEPH GREENWALD AND LAAK301-220-2200 MD",
        ))

        verifyNoMoreInteractions(chatGBTClient)

    }

    @Test
    fun test() {
        val chatGBTClient = ChatGBTClient("[REDACTED]",)

        val transactionCategorizer = TransactionCategorizer(chatGBTClient)

        val transactions = listOf("check", "transfer to")
        val result = transactionCategorizer.batchCategorizeTransactions(transactions)

        println(result.joinToString("\n") { listOf(it.description, it.vendor, it.category, it.subcategory, it.categorizationConfidence).joinToString(", ") })
    }
}