package com.goldberg.law.util

import com.fasterxml.jackson.databind.ObjectMapper

private val OBJECT_MAPPER = ObjectMapper()

fun Any.toStringDetailed(): String {
    return OBJECT_MAPPER.writeValueAsString(this)
}

fun Double.toCurrency() = "%.2f".format(this)

fun String.addQuotes() = '"' + this + '"'
