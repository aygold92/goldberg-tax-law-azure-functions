package com.goldberg.law.categorization

class VendorNormalizer {
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    fun normalizeVendor(description: String): String {
        val desc = description.lowercase().trim()

        vendorKeywords.forEach { (keyword, vendor) ->
            if (desc.contains(keyword)) return vendor
        }

        val squarePattern = Regex("sq \\*\\s*([\\w &'-]+)")
        val toastPattern = Regex("tst\\*\\s*([\\w &'-]+)")
        val match = squarePattern.find(desc) ?: toastPattern.find(desc)
        if (match != null) {
            return match.groupValues[1].trim().split(" ")[0].replaceFirstChar { it.uppercase() }
        }

        val cleaned = desc
            .replace(Regex("pos debit.*?\\d{2}-\\d{2}-\\d{2}"), "")
            .replace(Regex("debit card \\d+"), "")
            .replace(Regex("\\d{2}-\\d{2}-\\d{2}"), "")
            .replace(Regex("[0-9]{5}"), "")
            .replace(Regex("\\d{4,}"), "")
            .replace(Regex("\\b(md|va|wa|ny|fl|dc|de|pa|ca|tx)\\b"), "")
            .replace(Regex("[^a-zA-Z '&]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Try fuzzy match
        val bestMatch = vendorKeywords.keys.minByOrNull { levenshtein(cleaned, it) }
        val distance = bestMatch?.let { levenshtein(cleaned, it) } ?: Int.MAX_VALUE
        if (distance <= 3) {
            return vendorKeywords[bestMatch] ?: cleaned.replaceFirstChar { it.uppercase() }
        }

        return cleaned.split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    private val vendorKeywords = mapOf(
        "amzn" to "Amazon",
        "amzn mktp" to "Amazon",
        "amazon.com" to "Amazon",
        "amazon music" to "Amazon",
        "cvs/pharmacy" to "CVS",
        "cvs pharmacy" to "CVS",
        "cvs pharm" to "CVS",
        "starbucks" to "Starbucks",
        "7-eleven" to "7-Eleven",
        "7 eleven" to "7-Eleven",
        "mcdonald" to "McDonald's",
        "dunkin" to "Dunkin’",
        "aldi" to "Aldi",
        "homegoods" to "HomeGoods",
        "verizon" to "Verizon",
        "chewy" to "Chewy",
        "shell" to "Shell",
        "exxon" to "Exxon",
        "panera" to "Panera Bread",
        "target" to "Target",
        "banfield" to "Banfield",
        "five below" to "Five Below",
        "habit burger" to "Habit Burger",
        "baskin" to "Baskin-Robbins",
        "sweet frog" to "Sweet Frog",
        "big lots" to "Big Lots",
        "bp " to "BP",
        "wawa" to "Wawa",
        "domino" to "Domino’s",
        "pizza hut" to "Pizza Hut",
        "petco" to "Petco",
        "petsmart" to "PetSmart",
        "kohl" to "Kohl’s",
        "lidl" to "Lidl",
        "walgreens" to "Walgreens",
        "usps" to "USPS",
        "spotify" to "Spotify",
        "roku" to "Roku",
        "hulu" to "Hulu",
        "netflix" to "Netflix",
        "audible" to "Audible",
        "apple.com" to "Apple",
        "itunes" to "Apple",
        "google" to "Google",
        "venmo" to "Venmo",
        "paypal" to "PayPal"
    )
}