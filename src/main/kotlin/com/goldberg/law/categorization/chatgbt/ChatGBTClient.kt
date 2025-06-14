package com.goldberg.law.categorization.chatgbt

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.categorization.model.TransactionCategorization
import com.goldberg.law.categorization.model.TransactionVendor
import com.goldberg.law.categorization.model.VendorCategorization
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ChatGBTClient(private val apiKey: String) {
    private fun makeRequest(request: ChatGBTClientRequest): String {
        val requestBody = mapOf(
            "model" to "gpt-4",
            "messages" to listOf(
                mapOf("role" to "system", "content" to request.systemMessage),
                mapOf("role" to "user", "content" to request.userMessage)
            ),
            "temperature" to request.temperature
        )
        val url = URL(ENDPOINT)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        connection.outputStream.use { it.write(MAPPER.writeValueAsBytes(requestBody)) }

        val response = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        val root = MAPPER.readTree(response)

        // TODO: Handle errors if (root["error"])
        return root["choices"]?.get(0)?.get("message")?.get("content")?.asText()?.trim()
            ?: throw RuntimeException("Error in response from chatGBT: $response")
    }

    fun extractVendors(descriptions: List<String>): Map<String, TransactionVendor> {
        val prompt = """
        Extract the vendor name from the following transactions.
        Remove any location-specific info, card numbers, codes, or extra details.
        Use all your knowledge and internet searching if necessary, not just a simple string manipulation.
        For banking transactions (i.e. ATM Withdrawal) or if the vendor isn't clear, return empty string
        Normalize and deduplicate vendor names (e.g., "Amazon Mktpl", "AMZN Mktplace" => "Amazon")
        If the name is partially provided, please use the full name ("Harris Tee" => "Harris Teeter", Sq the Sweeties C
        The output list must be the same size and same order as the input list.
        
        Other example:
        POS Debit- Debit Card 0553 04-04-24 Starbucks Store 61 Gaithersburg MD --> Starbucks
        
        Return only an array of JSON objects with keys:
        - "v" (vendor without location information)
        - "l" (location information)
        - "c" (Confidence of your answer from 1-10)
        
        Transactions:
        ${descriptions.joinToString("\n")}   
    """.trimIndent()

        val request = ChatGBTClientRequest(
            systemMessage = "You are a financial assistant that extracts the vendor name for personal transactions from bank/credit card statements.",
            userMessage = prompt,
            temperature = 0.1  // low temperature means it won't make stuff up
        )

        val response = makeRequest(request)
        val typedResponse: List<ExtractVendorResponse> = MAPPER.readValue(response, object: TypeReference<List<ExtractVendorResponse>>(){})

        return typedResponse.mapIndexed {idx, resp -> TransactionVendor(
            description = descriptions[idx],
            vendor = resp.v,
            confidence = resp.c
        ) }.associateBy(TransactionVendor::description)
    }

    fun categorizeFromVendorName(vendors: List<String>): Map<String, VendorCategorization> {
        val prompt = """
        Categorize these businesses:
        ${vendors.joinToString("\n")}

        Return only an array of JSON objects with keys:
        - "catA" (category)
        - "catB" (subcategory)
        - "c" (Confidence your answer from 1-10)
        
        The output list must be the same size and same order as the input list 

        Use only the following categories/subcategories:
        $CATEGORIES
    """.trimIndent()

        val request = ChatGBTClientRequest(
            systemMessage = "You are a financial assistant that categorizes personal transactions from bank/credit card statements.",
            userMessage = prompt,
            temperature = .1  // low temperature means it won't make stuff up
        )

        val response = makeRequest(request)
        val typedResponse: List<CategorizeVendorResponse> = MAPPER.readValue(response, object: TypeReference<List<CategorizeVendorResponse>>(){})

        return typedResponse.mapIndexed {idx, resp -> VendorCategorization(
            vendor = vendors[idx],
            category = resp.catA,
            subcategory = resp.catB,
            confidence = resp.c,
        ) }.associateBy(VendorCategorization::vendor)
    }

    fun categorizeTransactions(descriptions: List<String>): List<TransactionCategorization> {
        val prompt = """
        Categorize these transactions:
        ${descriptions.joinToString("\n")}

        Return only an array of JSON objects with keys:
        - "v" (vendor: the name of the business without location specific terms. Use "" if not clear)
        - "catA" (category)
        - "catB" (subcategory)
        - "cc" (Confidence of the categorization from 1-10)
        - "cv" (Confidence of the vendor from 1-10)
        
        The output list must be the same size and same order as the input list 

        Use only the following categories/subcategories:
        $CATEGORIES
    """.trimIndent()

        val request = ChatGBTClientRequest(
            systemMessage = "You are a financial assistant that categorizes personal transactions from bank/credit card statements.",
            userMessage = prompt,
            temperature = .1  // low temperature means it won't make stuff up
        )

        val response = makeRequest(request)
        val typedResponse: List<CategorizeTransactionResponse> = MAPPER.readValue(response, object: TypeReference<List<CategorizeTransactionResponse>>(){})

        return typedResponse.mapIndexed {idx, resp -> TransactionCategorization(
            description = descriptions[idx],
            vendor = resp.v,
            category = resp.catA,
            subcategory = resp.catB,
            categorizationConfidence = resp.cc,
            vendorConfidence = resp.cv
        ) }
    }

    companion object {
        val MAPPER = ObjectMapper()

        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"

        private val CATEGORIES: String = mapOf(
            "PRIMARY RESIDENCE" to listOf(
                "Mortgage", "Insurance (homeowners)", "Rent/Ground Rent", "Taxes", "Gas & Electric",
                "Electric Only", "Heat (oil)", "Telephone", "Trash Removal", "Water Bill",
                "Cell Phone/Pager", "Repairs", "Lawn & Yard Care (snow removal)",
                "Replacement Furnishings/Appliances", "Condominium Fee (not included elsewhere)",
                "Painting/Wallpapering", "Carpet Cleaning", "Domestic Assistance/Housekeeper",
                "Pool", "Other"
            ),
            "OTHER HOUSEHOLD NECESSITIES" to listOf(
                "Food", "Drug Store Items", "Household Supplies", "Other"
            ),
            "MEDICAL/DENTAL" to listOf(
                "Health Insurance", "Therapist/Counselor", "Extraordinary Medical",
                "Dental/Orthodontia", "Ophthalmologist/Glasses", "Other"
            ),
            "SCHOOL EXPENSES" to listOf(
                "Tuition/Books", "School lunch", "Extracurricular activities",
                "Clothing/Uniforms", "Room & Board", "Daycare/Nursery School", "Other"
            ),
            "RECREATION & ENTERTAINMENT" to listOf(
                "Vacations", "Videos/Theater", "Dining Out", "Cable TV/Internet",
                "Allowance", "Camp", "Memberships", "Dance/Music Lessons etc.",
                "Horseback Riding", "Other"
            ),
            "TRANSPORTATION EXPENSE" to listOf(
                "Automobile Payment", "Automobile Repairs", "Maintenance/Tags/Tires/etc.",
                "Oil/Gas", "Automobile Insurance", "Parking Fees", "Bus/Taxi", "Other"
            ),
            "GIFTS" to listOf(
                "Holiday Gifts", "Birthdays", "Gifts to Others", "Charities"
            ),
            "CLOTHING" to listOf(
                "Purchasing", "Laundry", "Alterations/Dry Cleaning", "Other"
            ),
            "INCIDENTALS" to listOf(
                "Books & Magazines", "Newspapers", "Stamps/Stationery", "Banking Expense", "Other"
            ),
            "MISCELLANEOUS/OTHER" to listOf(
                "Alimony/Child Support (from a previous Order)", "Religious Contributions",
                "Hairdresser/Haircuts", "Manicure/Pedicure", "Pets/Boarding", "Life Insurance",
                "Other: Legal", "Other: Work Expenses", "Other: Personal Care", "Other"
            )
        ).entries.joinToString("\n") { (category, subcategories) ->
            "$category: ${subcategories.joinToString(", ")}"
        }.trim()
    }
}