package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.AccountNormalizer
import com.goldberg.law.document.CheckToStatementMatcher
import com.goldberg.law.document.DocumentStatementCreator
import com.goldberg.law.document.model.ModelValues
import com.goldberg.law.document.model.StatementModelValues
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.activity.ProcessStatementsActivityInput
import com.goldberg.law.util.normalizeDate
import com.microsoft.azure.functions.ExecutionContext
import com.nhaarman.mockitokotlin2.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class ProcessStatementsActivityTest {
    @Mock
    private val statementCreator: DocumentStatementCreator = mock()
    @Mock
    private val accountNormalizer: AccountNormalizer = mock()
    @Mock
    private val checkToStatementMatcher: CheckToStatementMatcher = mock()
    @Mock
    private val dataManager: AzureStorageDataManager = mock()

    @Mock
    private val context: ExecutionContext = mock()
    private val activity = ProcessStatementsActivity(statementCreator, accountNormalizer, checkToStatementMatcher, dataManager)

    @BeforeEach
    fun setup() {
        whenever(accountNormalizer.normalizeAccounts(any(), any())).thenReturn(Pair(STATEMENT_DATA_MODELS, CHECK_DATA_MODELS))
        whenever(statementCreator.createBankStatements(any())).thenReturn(BANK_STATEMENTS)
        whenever(checkToStatementMatcher.matchChecksWithStatements(any(), any())).thenReturn(BANK_STATEMENTS)
        whenever(dataManager.saveBankStatement(any())).thenReturn(SAVED_FILE_1, SAVED_FILE_2, SAVED_FILE_3, SAVED_FILE_4)
    }

    @Test
    fun testMetadata() {
        activity.processStatementsAndChecks(ProcessStatementsActivityInput(
            REQUEST_ID,
            setOf(
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_0),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_1),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_2),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_3)
            ),
            mapOf(
                FILE_10 to InputFileMetadata(true, 10, true),
                FILE_20 to InputFileMetadata(true, 20, true),
                FILE_30 to InputFileMetadata(true, 30, true),
            )
        ), context)

        verify(dataManager).saveBankStatement(STATEMENT_1)
        verify(dataManager).saveBankStatement(STATEMENT_2)
        verify(dataManager).saveBankStatement(STATEMENT_3)
        verify(dataManager).saveBankStatement(STATEMENT_4)

        verify(dataManager).updateInputPdfMetadata(FILE_10, InputFileMetadata(true, 10, true, setOf(STATEMENT_1.azureFileName())))
        verify(dataManager).updateInputPdfMetadata(FILE_20, InputFileMetadata(true, 20, true, setOf(STATEMENT_2.azureFileName())))
        verify(dataManager).updateInputPdfMetadata(FILE_30, InputFileMetadata(true, 30, true, setOf(STATEMENT_3.azureFileName(), STATEMENT_4.azureFileName())))
        verifyNoMoreInteractions(dataManager)
    }

    companion object {
        private val REQUEST_ID = "RequestId"
        private val STATEMENT_DATA_MODELS = listOf(
            StatementModelValues.STATEMENT_MODEL_WF_BANK_0,
            StatementModelValues.STATEMENT_MODEL_WF_BANK_1,
            StatementModelValues.STATEMENT_MODEL_WF_BANK_2,
            StatementModelValues.STATEMENT_MODEL_WF_BANK_3,
        )
        private val CHECK_DATA_MODELS = listOf(ModelValues.newCheckData(1000))
        private const val FILE_10 = "File10"
        private const val FILE_20 = "File20"
        private const val FILE_30 = "File30"
        private val STATEMENT_DATE_1 = normalizeDate("4/7 2020")!!

        private val STATEMENT_DATE_2 = normalizeDate("5/7 2020")!!
        private val STATEMENT_DATE_3 = normalizeDate("6/7 2020")!!
        private val STATEMENT_DATE_4 = normalizeDate("7/7 2020")!!
        private val STATEMENT_1 = ModelValues.newBankStatement(filename = FILE_10, statementDate = STATEMENT_DATE_1)
            .update(pageMetadata = ModelValues.newTransactionPage(1, FILE_10))
            .update(pageMetadata = ModelValues.newTransactionPage(2, FILE_10))

        private val STATEMENT_2 = ModelValues.newBankStatement(filename = FILE_20, statementDate = STATEMENT_DATE_2)
            .update(pageMetadata = ModelValues.newTransactionPage(1, FILE_20))
            .update(pageMetadata = ModelValues.newTransactionPage(2, FILE_20))

        private val STATEMENT_3 = ModelValues.newBankStatement(filename = FILE_30, statementDate = STATEMENT_DATE_3)
            .update(pageMetadata = ModelValues.newTransactionPage(1, FILE_30))
            .update(pageMetadata = ModelValues.newTransactionPage(2, FILE_30))

        private val STATEMENT_4 = ModelValues.newBankStatement(filename = FILE_30, statementDate = STATEMENT_DATE_4)
            .update(pageMetadata = ModelValues.newTransactionPage(3, FILE_30))
            .update(pageMetadata = ModelValues.newTransactionPage(4, FILE_30))

        private val BANK_STATEMENTS = listOf(STATEMENT_1, STATEMENT_2, STATEMENT_3, STATEMENT_4)

        private const val SAVED_FILE_1 = "File1"
        private const val SAVED_FILE_2 = "File2"
        private const val SAVED_FILE_3 = "File3"
        private const val SAVED_FILE_4 = "File4"
    }

}