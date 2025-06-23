package com.goldberg.law.document.model.pdf

import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.B_OF_A
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.EAGLE_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.NFCU_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK_JOINT
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.B_OF_A_CHECK
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.EAGLE_BANK_CHECK
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.MISC_CHECK
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.NFCU_CHECK
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.AMEX_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.B_OF_A_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.C1_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.CITI_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.NFCU_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.WF_CC
import com.goldberg.law.document.model.pdf.DocumentType.IrrelevantTypes.EXTRA_PAGES

enum class DocumentType(val docTypes: List<String> = listOf()) {
    CREDIT_CARD(listOf(AMEX_CC, C1_CC, CITI_CC, WF_CC, B_OF_A_CC, NFCU_CC)),
    BANK(listOf(EAGLE_BANK, WF_BANK, WF_BANK_JOINT, B_OF_A, NFCU_BANK)),
    CHECK(listOf(EAGLE_BANK_CHECK, B_OF_A_CHECK, MISC_CHECK, NFCU_CHECK)),
    IRRELEVANT(listOf(EXTRA_PAGES)),
    TRANSACTIONS(listOf(TransactionTypes.TRANSACTIONS_TYPE)),
    UNKNOWN;

    fun isCheck() = this == CHECK
    fun isRelevant() = this != IRRELEVANT
    fun isStatement() = !isCheck() && isRelevant()

    fun isStatementPage() = this == CREDIT_CARD || this == BANK
    fun isTransactionPage() = this == TRANSACTIONS

    object CreditCardTypes {
        const val AMEX_CC = "AMEX CC"
        const val C1_CC = "C1 CC"
        const val CITI_CC = "CITI CC"
        const val WF_CC = "WF CC"
        const val B_OF_A_CC = "BofA CC"
        const val NFCU_CC = "NFCU CC"
    }

    object BankTypes {
        const val EAGLE_BANK = "Eagle Bank"
        const val WF_BANK = "WF Bank"
        const val WF_BANK_JOINT = "WF Bank Joint"
        const val B_OF_A = "BofA"
        const val NFCU_BANK = "NFCU Bank"
    }

    object CheckTypes {
        const val EAGLE_BANK_CHECK = "Eagle Bank Check"
        const val B_OF_A_CHECK = "BofA Check"
        const val MISC_CHECK = "Misc Check"
        const val NFCU_CHECK = "NFCU Check"
    }

    object TransactionTypes {
        const val TRANSACTIONS_TYPE = "Transactions"
    }

    object IrrelevantTypes {
        const val EXTRA_PAGES = "Extra Pages"
    }

    companion object {
        fun getBankType(docType: String): DocumentType = DocumentType.entries.find { it.docTypes.contains(docType) } ?: UNKNOWN
    }
}