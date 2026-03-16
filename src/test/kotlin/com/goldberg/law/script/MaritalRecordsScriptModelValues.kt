package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.model.*
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.ObjectAssert
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

val SYMBOL_MMF = HoldingSymbol("VMFXX")
const val NAME_0 = "Money Market Fund"

val SYMBOL_1 = HoldingSymbol("TEST")
const val NAME_1 = "TEST SYMBOL"

val SYMBOL_2 = TreasuryCoupon("test", null, "test", "tesT")
const val NAME_2 = "TREASURY NAME"

const val DEPOSIT_NAME = "deposit"

val DATE_BEFORE = fromWrittenDate("10/7/2010")!!
val DATE_0 = fromWrittenDate("10/8/2020")!!
val DATE_1 = fromWrittenDate("10/9/2020")!!
val DATE_2 = fromWrittenDate("10/10/2020")!!
val DATE_3 = fromWrittenDate("10/11/2020")!!

fun newTransaction(date: Date, symbol: InstrumentKey, type: VanguardTransactionType, amount: Number, quantity: BigDecimal? = null, override: TransactionClassification? = null) = VanguardTransaction(
    date, date, symbol, NAME_0, type, "CASH", quantity, null, null, amount.asCurrency(), override
)

private val BIG_DECIMAL_COMPARATOR = kotlin.Comparator<BigDecimal> { a, b ->
    if (a == b) 0
    else if (a == null) -1
    else if (b == null) 1
    else a.setScale(10, RoundingMode.HALF_EVEN)
        .compareTo(b.setScale(10, RoundingMode.HALF_EVEN))
}
fun <T> ObjectAssert<T>.bigDecimalCompare() = this.usingRecursiveComparison().ignoringFieldsMatchingRegexes(".*\\\$delegate").withComparatorForType(BIG_DECIMAL_COMPARATOR, BigDecimal::class.java)
