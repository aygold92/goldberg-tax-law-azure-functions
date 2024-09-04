package com.goldberg.law.pdf.model

import com.goldberg.law.pdf.model.DocumentType.BankTypes.EAGLE_BANK
import com.goldberg.law.pdf.model.DocumentType.BankTypes.WF_BANK
import com.goldberg.law.pdf.model.DocumentType.CheckTypes.EAGLE_BANK_CHECK
import com.goldberg.law.pdf.model.DocumentType.CreditCardTypes.AMEX_CC
import com.goldberg.law.pdf.model.DocumentType.CreditCardTypes.C1_CC
import com.goldberg.law.pdf.model.DocumentType.CreditCardTypes.CITI_CC
import com.goldberg.law.pdf.model.DocumentType.CreditCardTypes.WF_CC

enum class DocumentType(val docTypes: List<String> = listOf()) {
    CREDIT_CARD(listOf(AMEX_CC, C1_CC, CITI_CC, WF_CC)),
    BANK(listOf(EAGLE_BANK, WF_BANK)),
    CHECK(listOf(EAGLE_BANK_CHECK)),
    MIXED,
    UNKNOWN;

    object CreditCardTypes {
        const val AMEX_CC = "AMEX CC"
        const val C1_CC = "C1 CC"
        const val CITI_CC = "CITI CC"
        const val WF_CC = "WF CC"
    }

    object BankTypes {
        const val EAGLE_BANK = "Eagle Bank"
        const val WF_BANK = "WF Bank"
    }

    object CheckTypes {
        const val EAGLE_BANK_CHECK = "Eagle Bank Check"
    }

    companion object {
        fun getBankType(docType: String): DocumentType = DocumentType.entries.find { it.docTypes.contains(docType) } ?: UNKNOWN
    }
}