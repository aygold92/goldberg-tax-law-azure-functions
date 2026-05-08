package com.goldberg.law.document.model.pdf

import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.ATLANTIC_UNION
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.B_OF_A
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.EAGLE_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.NFCU_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK_JOINT
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.TRUIST
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.CAPITAL_ONE_JOINT
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.M_T_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.SANDY_SPRING
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.TFCU_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.TFCU_BANK_OLD
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.CHECKS
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.CHECKS_RAW
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.ALLY_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.AMEX_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.B_OF_A_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.B_OF_A_CC_BUSINESS
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.C1_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.CITI_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.NFCU_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.TFCU_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.WF_CC
import com.goldberg.law.document.model.pdf.DocumentType.ExtraPageTypes.BACK_OF_CHECK
import com.goldberg.law.document.model.pdf.DocumentType.ExtraPageTypes.BLANK
import com.goldberg.law.document.model.pdf.DocumentType.ExtraPageTypes.B_OF_A_JOINT_OVERVIEW
import com.goldberg.law.document.model.pdf.DocumentType.ExtraPageTypes.TEXT
import com.goldberg.law.document.model.pdf.DocumentType.ExtraPageTypes.TFCU_CHECK_SCANS
import com.goldberg.law.document.model.pdf.DocumentType.ExtraPageTypes.TRANSACTION_LIKE
import com.goldberg.law.document.model.pdf.DocumentType.ExtraPageTypes.WF_JOINT_OVERVIEW
import com.goldberg.law.document.model.pdf.DocumentType.TransactionTypes.TRANSACTIONS_TFCU
import com.goldberg.law.document.model.pdf.DocumentType.TransactionTypes.TRANSACTIONS_TYPE
import com.goldberg.law.document.model.pdf.DocumentType.TransactionTypes.TRANSACTIONS_WF_SINGLE

enum class DocumentType(val docTypes: List<String> = listOf()) {
    CREDIT_CARD(listOf(AMEX_CC, C1_CC, CITI_CC, WF_CC, B_OF_A_CC, B_OF_A_CC_BUSINESS, NFCU_CC, ALLY_CC, TFCU_CC)),
    BANK(listOf(EAGLE_BANK, WF_BANK, WF_BANK_JOINT, B_OF_A, NFCU_BANK, TRUIST, CAPITAL_ONE_JOINT, SANDY_SPRING, ATLANTIC_UNION, M_T_BANK, TFCU_BANK, TFCU_BANK_OLD)),
    CHECK(listOf(CHECKS, CHECKS_RAW)),
    EXTRA_PAGES(listOf(TEXT, BLANK, BACK_OF_CHECK, TRANSACTION_LIKE, B_OF_A_JOINT_OVERVIEW, WF_JOINT_OVERVIEW, TFCU_CHECK_SCANS)),
    TRANSACTIONS(listOf(TRANSACTIONS_TYPE, TRANSACTIONS_TFCU, TRANSACTIONS_WF_SINGLE)),
    UNKNOWN;

    fun isCheck() = this == CHECK
    fun isRelevant() = this != EXTRA_PAGES
    fun isStatement() = !isCheck() && isRelevant()

    fun isStatementPage() = this == CREDIT_CARD || this == BANK
    fun isTransactionPage() = this == TRANSACTIONS

    object CreditCardTypes {
        const val AMEX_CC = "AMEX CC"
        const val C1_CC = "C1 CC"
        const val CITI_CC = "CITI CC"
        const val WF_CC = "WF CC"
        const val B_OF_A_CC = "BofA CC"
        const val B_OF_A_CC_BUSINESS = "BofA CC Business"
        const val NFCU_CC = "NFCU CC"
        const val ALLY_CC = "Ally CC"
        const val TFCU_CC = "TFCU CC"
    }

    object BankTypes {
        const val EAGLE_BANK = "Eagle Bank"
        const val WF_BANK = "WF Bank"
        const val WF_BANK_JOINT = "WF Bank Joint"
        const val B_OF_A = "BofA"
        const val NFCU_BANK = "NFCU Bank"
        const val TRUIST = "Truist"
        const val CAPITAL_ONE_JOINT = "Capital One Joint"
        const val SANDY_SPRING = "Sandy Spring"
        const val ATLANTIC_UNION = "Atlantic Union"
        const val M_T_BANK = "M&T Bank"
        const val TFCU_BANK = "TFCU Bank"
        const val TFCU_BANK_OLD = "TFCU Bank (Old)"
    }

    object CheckTypes {
        const val CHECKS = "Checks"
        const val CHECKS_RAW = "Checks - Raw"
    }

    object TransactionTypes {
        const val TRANSACTIONS_TYPE = "Transactions"
        const val TRANSACTIONS_TFCU = "Transactions - TFCU"
        const val TRANSACTIONS_WF_SINGLE = "Transactions - WF Single"
    }

    object ExtraPageTypes {
        const val TEXT = "Extra Pages - Text"
        const val BLANK = "Extra Pages - Blank"
        // backs of checks that should be discarded
        const val BACK_OF_CHECK = "Extra Pages - Back of Check"
        // contains tables and account information but no relevant transaction data
        const val TRANSACTION_LIKE = "Extra Pages - Transaction Like"
        // special category that could be mistaken for checks
        const val TFCU_CHECK_SCANS = "Extra Pages - TFCU Check Scans"
        // special category that could be mistaken for a BofA start page
        const val B_OF_A_JOINT_OVERVIEW = "Extra Pages - BofA Joint Overview"
        // special category that could be mistaken for Wells Fargo joint pages
        const val WF_JOINT_OVERVIEW = "Extra Pages - WF Joint Overview"
    }

    companion object {
        fun getBankType(docType: String): DocumentType = DocumentType.entries.find { it.docTypes.contains(docType) } ?: UNKNOWN
        // this handles the fact that some bank types have multiple statements in the same page
        fun hasMultipleStatements(classification: String) = classification in DOC_TYPES_MULTIPLE_STATEMENTS

        private val DOC_TYPES_MULTIPLE_STATEMENTS = listOf(NFCU_BANK, CAPITAL_ONE_JOINT, ATLANTIC_UNION, TFCU_BANK, TFCU_BANK_OLD)
    }
}