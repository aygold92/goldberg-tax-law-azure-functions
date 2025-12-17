package com.goldberg.law.function.model

import com.goldberg.law.document.model.StatementModelValues
import com.goldberg.law.document.model.StatementModelValues.newCheckDataModel
import com.goldberg.law.document.model.input.*
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_2
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_3
import com.goldberg.law.entity.EntityValues.DEFAULT_ACCOUNT_NUMBER
import com.goldberg.law.entity.EntityValues.DEFAULT_BATES_STAMP
import com.goldberg.law.entity.EntityValues.DEFAULT_DATE
import com.goldberg.law.entity.EntityValues.FILE_ID_2
import com.goldberg.law.entity.EntityValues.FILE_ID_3
import com.goldberg.law.entity.EntityValues.newClassification
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
        val STATEMENT_DATA_MODEL = StatementModelValues.newStatementModel(
            summaryOfAccountsTable = SummaryOfAccountsTable(listOf(SummaryOfAccountsTableRecord("", 4.asCurrency(), 5.asCurrency()))),
            batesStampsTable = BatesStampTable(listOf(BatesStampTableRow(DEFAULT_BATES_STAMP, 1))),
            transactionTableAmount = TransactionTableAmount(records = listOf(TransactionTableAmountRecord("test", "test", 1.asCurrency(), page = 1))),
            transactionTableCreditsCharges = TransactionTableCreditsCharges(records = listOf(
                TransactionTableCreditsChargesRecord("test", "test", 1.asCurrency(), 2.asCurrency(), page = 1)
            )),
            transactionTableDebits = TransactionTableDebits(records = listOf(TransactionTableDebitsRecord("test", "test", 1.asCurrency(), page = 1))),
            transactionTableCredits = TransactionTableCredits(records = listOf(TransactionTableCreditsRecord("test", "test", 1.asCurrency(), page = 1))),
            transactionTableChecks = TransactionTableChecks(records = listOf(TransactionTableChecksRecord("test", 1, 1.asCurrency(), page = 1))),
            interestCharged = 3.asCurrency(),
            feesCharged = 4.asCurrency(),
            classification = newClassification()
        )

        val CHECK_DATA_MODEL = newCheckDataModel()

        val CHECK_DATA_MODEL_CHECK_ENTRIES = CheckDataModel(
            accountNumber = DEFAULT_ACCOUNT_NUMBER,
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
                    date = DEFAULT_DATE,
                    amount = 65.60.asCurrency(),
                    accountNumber = null,
                    page = 1
            )
            )),
            batesStamp = DEFAULT_BATES_STAMP,
            classification = newClassification(FILE_ID_2, CLASSFN_ID_2, DocumentType.CheckTypes.CHECKS)
        )

        val EXTRA_PAGE_DATA_MODEL = ExtraPageDataModel(
            classification = newClassification(FILE_ID_3, CLASSFN_ID_3, DocumentType.ExtraPageTypes.TEXT)
        )
    }
}