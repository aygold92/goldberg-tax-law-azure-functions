package com.goldberg.law.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

fun Date.toTransactionDate(): String = SimpleDateFormat("M/d/YYYY").format(this)
fun Date.getYearSafe(): String = Calendar.getInstance().apply { time = this@getYearSafe }.get(Calendar.YEAR).toString()

fun fromStatementDate(monthDay: String?, year: String?): Date? {
    val dateString = "$monthDay $year".replace(",", "")
        .replace("\\s+".toRegex(), " ").trim()
    try {
        return SimpleDateFormat("MMM dd yyyy").parse(dateString)
    } catch (e: ParseException) {
        // Continue to the next format
        println("Unable to parse date: $dateString ")
        return null
    }
}

fun fromTransactionDate(monthDay: String?, year: String?): Date? {
    val dateString = "$monthDay/$year"
    listOf("M/d/yyyy", "MM/dd/yyyy").forEach { format ->
        try {
            return SimpleDateFormat(format).parse(dateString)
        } catch (e: ParseException) {
            // Continue to the next format
        }
    }
    println("Unable to parse date: $dateString ")
    return null
}