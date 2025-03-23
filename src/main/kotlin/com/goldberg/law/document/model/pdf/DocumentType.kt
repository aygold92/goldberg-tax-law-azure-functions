package com.goldberg.law.document.model.pdf

import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.B_OF_A
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.EAGLE_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.B_OF_A_CHECK
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.EAGLE_BANK_CHECK
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.AMEX_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.C1_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.CITI_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.WF_CC
import com.goldberg.law.document.model.pdf.DocumentType.IrrelevantTypes.EXTRA_PAGES

enum class DocumentType(val docTypes: List<String> = listOf()) {
    CREDIT_CARD(listOf(AMEX_CC, C1_CC, CITI_CC, WF_CC)),
    BANK(listOf(EAGLE_BANK, WF_BANK, B_OF_A)),
    CHECK(listOf(EAGLE_BANK_CHECK, B_OF_A_CHECK)),
    IRRELEVANT(listOf(EXTRA_PAGES)),
    MIXED,
    UNKNOWN;

    fun isCheck() = this == CHECK
    fun isRelevant() = this != IRRELEVANT
    fun isStatement() = !isCheck() && isRelevant()

    object CreditCardTypes {
        const val AMEX_CC = "AMEX CC"
        const val C1_CC = "C1 CC"
        const val CITI_CC = "CITI CC"
        const val WF_CC = "WF CC"
        const val B_OF_A_CC = "BofA CC"
    }

    object BankTypes {
        const val EAGLE_BANK = "Eagle Bank"
        const val WF_BANK = "WF Bank"
        const val B_OF_A = "BofA"
        const val NFCU_BANK = "NFCU Bank"
    }

    object CheckTypes {
        const val EAGLE_BANK_CHECK = "Eagle Bank Check"
        const val B_OF_A_CHECK = "BofA Check"
        const val MISC_CHECK = "Misc Check"
        const val NFCU_CHECK = "NFCU Check"
    }

    object IrrelevantTypes {
        const val EXTRA_PAGES = "Extra Pages"
    }

    companion object {
        fun getBankType(docType: String): DocumentType = DocumentType.entries.find { it.docTypes.contains(docType) } ?: UNKNOWN
    }
}