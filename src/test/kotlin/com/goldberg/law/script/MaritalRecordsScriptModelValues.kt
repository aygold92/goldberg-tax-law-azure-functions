package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.model.*
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import java.math.BigDecimal
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

fun newTransaction(date: Date, symbol: InstrumentKey, type: VanguardTransactionType, amount: Number, quantity: BigDecimal? = null) = VanguardTransaction(
    date, date, symbol, NAME_0, type, "CASH", quantity, null, null, amount.asCurrency()
)