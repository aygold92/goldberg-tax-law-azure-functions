package com.goldberg.law.pdf.model

import com.goldberg.law.pdf.model.StatementType.BankTypes.EAGLE_BANK
import com.goldberg.law.pdf.model.StatementType.BankTypes.WF_BANK
import com.goldberg.law.pdf.model.StatementType.CreditCardTypes.AMEX_PLATINUM
import com.goldberg.law.pdf.model.StatementType.CreditCardTypes.C1_SIGNATURE
import com.goldberg.law.pdf.model.StatementType.CreditCardTypes.C1_VENTURE
import com.goldberg.law.pdf.model.StatementType.CreditCardTypes.CITI_DOUBLE_CASH
import com.goldberg.law.pdf.model.StatementType.CreditCardTypes.WF_VISA

enum class StatementType(val docTypes: List<String> = listOf()) {
    CREDIT_CARD(listOf(AMEX_PLATINUM, C1_SIGNATURE, C1_VENTURE, CITI_DOUBLE_CASH, WF_VISA)),
    BANK(listOf(EAGLE_BANK, WF_BANK)),
    MIXED,
    UNKNOWN;

    object CreditCardTypes {
        const val AMEX_PLATINUM = "AMEX Platinum"
        const val C1_SIGNATURE = "C1 Signature"
        const val C1_VENTURE = "C1 Venture"
        const val CITI_DOUBLE_CASH = "CITI DoubleCash"
        const val WF_VISA = "WF Visa"
    }

    object BankTypes {
        const val EAGLE_BANK = "Eagle Bank"
        const val WF_BANK = "WF BANK"
    }

    companion object {
        fun getBankType(docType: String): StatementType = StatementType.entries.find { it.docTypes.contains(docType) } ?: UNKNOWN
    }
}