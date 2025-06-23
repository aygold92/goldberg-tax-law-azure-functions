package com.goldberg.law.function.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.input.*
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.function.model.activity.ProcessStatementsActivityOutput
import com.goldberg.law.util.GSON
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.asCurrency
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DocumentDataModelContainerTest {
    @Test
    fun testStatementDataModelSerializableJackson() {
        val model = DocumentDataModelContainer(statementDataModel = STATEMENT_DATA_MODEL)
        val otherModel = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(model), DocumentDataModelContainer::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testStatementDataModelSerializableGson() {
        val model = DocumentDataModelContainer(statementDataModel = STATEMENT_DATA_MODEL)
        val otherModel = GSON.fromJson(GSON.toJson(model), DocumentDataModelContainer::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testCheckDataModelSerializableJackson() {
        val model = DocumentDataModelContainer(checkDataModel = CHECK_DATA_MODEL_CHECK_ENTRIES)
        val otherModel = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(model), DocumentDataModelContainer::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testCheckDataModelSerializableGson() {
        val model = DocumentDataModelContainer(checkDataModel = CHECK_DATA_MODEL_CHECK_ENTRIES)
        val otherModel = GSON.fromJson(GSON.toJson(model), DocumentDataModelContainer::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testProcessStatementAndChecksOutputSerializableGson() {
        val model = ProcessStatementsActivityOutput(mapOf("test" to setOf("st1", "st2", "test2"), "test2" to setOf("st3", "st4")))
        val otherModel = GSON.fromJson(GSON.toJson(model), ProcessStatementsActivityOutput::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testOneOf(){
        assertThrows<IllegalArgumentException> { DocumentDataModelContainer(STATEMENT_DATA_MODEL, CHECK_DATA_MODEL, EXTRA_PAGE_DATA_MODEL).getDocumentDataModel() }
        assertThrows<IllegalArgumentException> { DocumentDataModelContainer(STATEMENT_DATA_MODEL, null, EXTRA_PAGE_DATA_MODEL).getDocumentDataModel() }
        assertThrows<IllegalArgumentException> { DocumentDataModelContainer(STATEMENT_DATA_MODEL, CHECK_DATA_MODEL, null).getDocumentDataModel() }
        assertThrows<IllegalArgumentException> { DocumentDataModelContainer(null, CHECK_DATA_MODEL, EXTRA_PAGE_DATA_MODEL).getDocumentDataModel() }
    }

    @Test
    fun testCast() {
        assertThat(DocumentDataModelContainer(statementDataModel = STATEMENT_DATA_MODEL).getDocumentDataModel() as StatementDataModel)
            .isEqualTo(STATEMENT_DATA_MODEL)
        assertThat(DocumentDataModelContainer(checkDataModel = CHECK_DATA_MODEL).getDocumentDataModel() as CheckDataModel)
            .isEqualTo(CHECK_DATA_MODEL)
        assertThat(DocumentDataModelContainer(extraPageDataModel = EXTRA_PAGE_DATA_MODEL).getDocumentDataModel() as ExtraPageDataModel)
            .isEqualTo(EXTRA_PAGE_DATA_MODEL)
    }

    @Test
    fun testSecondaryConstructor() {
        val documentDataModel: DocumentDataModel = STATEMENT_DATA_MODEL
        assertThat(DocumentDataModelContainer(documentDataModel).statementDataModel).isEqualTo(STATEMENT_DATA_MODEL)

        val checkDataModel: DocumentDataModel = CHECK_DATA_MODEL
        assertThat(DocumentDataModelContainer(checkDataModel).checkDataModel).isEqualTo(CHECK_DATA_MODEL)

        val extraPageDataModel: DocumentDataModel = EXTRA_PAGE_DATA_MODEL
        assertThat(DocumentDataModelContainer(extraPageDataModel).extraPageDataModel).isEqualTo(EXTRA_PAGE_DATA_MODEL)
        assertThat(DocumentDataModelContainer(extraPageDataModel).getDocumentDataModel()).isEqualTo(EXTRA_PAGE_DATA_MODEL)
    }

    companion object {
        val STATEMENT_DATA_MODEL = StatementDataModel(
            date = FIXED_STATEMENT_DATE,
            summaryOfAccountsTable = SummaryOfAccountsTable(listOf(SummaryOfAccountsTableRecord("", 4.asCurrency(), 5.asCurrency()))),
            transactionTableDepositWithdrawal = null,
            batesStamps = BatesStampTable(listOf(BatesStampTableRow(BATES_STAMP, 1))),
            accountNumber = "",
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = TransactionTableAmount(records = listOf(TransactionTableAmountRecord("test", "test", 1.asCurrency(), page = 1))),
            transactionTableCreditsCharges = TransactionTableCreditsCharges(records = listOf(
                TransactionTableCreditsChargesRecord("test", "test", 1.asCurrency(), 2.asCurrency(), page = 1)
            )),
            transactionTableDebits = TransactionTableDebits(records = listOf(TransactionTableDebitsRecord("test", "test", 1.asCurrency(), page = 1))),
            transactionTableCredits = TransactionTableCredits(records = listOf(TransactionTableCreditsRecord("test", "test", 1.asCurrency(), page = 1))),
            transactionTableChecks = TransactionTableChecks(records = listOf(TransactionTableChecksRecord("test", 1, 1.asCurrency(), page = 1))),
            interestCharged = 3.asCurrency(),
            feesCharged = 4.asCurrency(),
            pageMetadata = ClassifiedPdfMetadata(FILENAME, 1, DocumentType.BankTypes.WF_BANK)
        )

        val CHECK_DATA_MODEL = CheckDataModel(
            accountNumber = ACCOUNT_NUMBER,
            checkNumber = 1234,
            to = "",
            description = "",
            date = FIXED_STATEMENT_DATE,
            amount = null,
            checkEntries = null,
            batesStamp = BATES_STAMP,
            pageMetadata = ClassifiedPdfMetadata(FILENAME, 1, DocumentType.BankTypes.WF_BANK)
        )

        val CHECK_DATA_MODEL_CHECK_ENTRIES = CheckDataModel(
            accountNumber = ACCOUNT_NUMBER,
            checkNumber = null,
            to = null,
            description = null,
            date = null,
            amount = null,
            checkEntries = CheckEntriesTable(images = listOf(
                CheckEntriesTableRow(
                    checkNumber = 1234,
                    to = "test",
                    description = "desc",
                    date = FIXED_STATEMENT_DATE,
                    amount = 65.60.asCurrency(),
                    accountNumber = null,
                    page = 1
            )
            )),
            batesStamp = BATES_STAMP,
            pageMetadata = ClassifiedPdfMetadata(FILENAME, 1, DocumentType.CheckTypes.B_OF_A_CHECK)
        )

        val EXTRA_PAGE_DATA_MODEL = ExtraPageDataModel(
            pageMetadata = ClassifiedPdfMetadata(FILENAME, 1, DocumentType.IrrelevantTypes.EXTRA_PAGES)
        )
    }
}