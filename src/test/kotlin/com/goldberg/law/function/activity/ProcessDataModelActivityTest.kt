package com.goldberg.law.function.activity

import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.datamanager.Extension
import com.goldberg.law.datamanager.StorageLocation
import com.goldberg.law.document.*
import com.goldberg.law.document.model.StatementModelValues.REQUEST_ID
import com.goldberg.law.document.model.StatementModelValues.newCheckDataModel
import com.goldberg.law.document.model.StatementModelValues.newCheckEntriesTableRow
import com.goldberg.law.document.model.StatementModelValues.newPdfDocument
import com.goldberg.law.document.model.StatementModelValues.newStatementModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.entity.EntityValues.CHECK_ID
import com.goldberg.law.entity.EntityValues.CHECK_ID_2
import com.goldberg.law.entity.EntityValues.CLASSFN_ID
import com.goldberg.law.entity.EntityValues.DEFAULT_CLASSIFICATION
import com.goldberg.law.entity.EntityValues.DEFAULT_FILE
import com.goldberg.law.entity.EntityValues.DEFAULT_STATEMENT
import com.goldberg.law.entity.EntityValues.FILE_ID
import com.goldberg.law.entity.EntityValues.STMT_ID
import com.goldberg.law.entity.EntityValues.STMT_ID_2
import com.goldberg.law.entity.EntityValues.entityCompare
import com.goldberg.law.entity.EntityValues.newCheckDetails
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newInputFile
import com.goldberg.law.entity.EntityValues.newStatement
import com.goldberg.law.entity.EntityValues.newStatementDetails
import com.goldberg.law.function.activity.model.ProcessDataModelActivityInput
import com.goldberg.law.function.activity.model.ProcessDataModelActivityOutput
import com.goldberg.law.function.api.model.ClassificationProcessingOptions
import com.goldberg.law.function.model.ExtractedDocumentIds
import com.microsoft.azure.functions.ExecutionContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class ProcessDataModelActivityTest {
    @Mock
    private val dataManager: AzureStorageDataManager = mock()
    @Mock
    private val dataExtractor: DocumentDataExtractor = mock()
    @Mock
    private val classificationService: ClassificationService = mock()
    @Mock
    private val statementService: StatementService = mock()
    @Mock
    private val checkService: CheckService = mock()
    @Mock
    private val documentStatementCreator: DocumentStatementCreator = mock()
    @Mock
    val context: ExecutionContext = mock()

    val activity = ProcessDataModelActivity(dataExtractor, dataManager, classificationService, statementService, checkService, documentStatementCreator)

    @AfterEach
    fun verify() {
        verify(context, atLeastOnce()).invocationId
        verifyNoMoreInteractions(dataManager, dataExtractor, classificationService, statementService, checkService, documentStatementCreator, context)
    }

    @Test
    fun testCheckModel() {
        val classification = newClassification(type = DocumentType.CheckTypes.CHECKS)
        val checkDataModel = newCheckDataModel(classification = classification)
        whenever(dataManager.loadInputPdfDocument(any())).thenReturn(newPdfDocument())
        whenever(dataExtractor.extractCheckData(any())).thenReturn(checkDataModel)
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, classification, PROCESSING_OPTIONS), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).hasSize(1)
        assertThat(output.extractedDocumentIds.statementIds).isEmpty()

        verify(dataManager).loadInputPdfDocument(classification.inputFile)
        verify(dataExtractor).extractCheckData(any())
        verify(dataManager).saveModel(classification, checkDataModel)
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
        val checkCaptor = argumentCaptor<CheckDetails>()
        verify(checkService).insertCheck(eq(classification), checkCaptor.capture())
        assertThat(checkCaptor.firstValue).entityCompare().isEqualTo(newCheckDetails())
    }

    @Test
    fun testCheckModelMultiple() {
        val classification = newClassification(type = DocumentType.CheckTypes.CHECKS_RAW)
        val model = newCheckDataModel(checkEntries = arrayOf(
            newCheckEntriesTableRow(),
            newCheckEntriesTableRow(checkNumber = 1001),
        ))
        whenever(dataManager.loadInputPdfDocument(any())).thenReturn(newPdfDocument())
        whenever(dataExtractor.extractCheckData(any())).thenReturn(model)
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, classification, PROCESSING_OPTIONS), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).hasSize(2)
        assertThat(output.extractedDocumentIds.statementIds).isEmpty()

        verify(dataManager).loadInputPdfDocument(classification.inputFile)
        verify(dataExtractor).extractCheckData(any())
        verify(dataManager).saveModel(classification, model)
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
        val checkCaptor = argumentCaptor<CheckDetails>()
        verify(checkService, times(2)).insertCheck(eq(classification), checkCaptor.capture())
        assertThat(checkCaptor.allValues).satisfiesExactlyInAnyOrder(
            { assertThat(it).entityCompare().isEqualTo(newCheckDetails()) },
            { assertThat(it).entityCompare().isEqualTo(newCheckDetails(checkNumber = 1001)) },
        )
    }

    @Test
    fun testStatementModel() {
        whenever(dataManager.loadInputPdfDocument(any())).thenReturn(newPdfDocument())
        whenever(dataExtractor.extractStatementData(any())).thenReturn(newStatementModel())
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)
        whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(DEFAULT_STATEMENT))

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, DEFAULT_CLASSIFICATION, PROCESSING_OPTIONS), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).isEmpty()
        assertThat(output.extractedDocumentIds.statementIds).hasSize(1)

        verify(dataManager).loadInputPdfDocument(DEFAULT_FILE)
        verify(dataExtractor).extractStatementData(any())
        verify(dataManager).saveModel(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
        verify(documentStatementCreator).createBankStatements(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(statementService).insertBankStatementWithTransactions(DEFAULT_STATEMENT)
    }

    @Test
    fun testStatementModelMultiple() {
        whenever(dataManager.loadInputPdfDocument(any())).thenReturn(newPdfDocument())
        whenever(dataExtractor.extractStatementData(any())).thenReturn(newStatementModel())
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)
        val stmt1 = newStatement()
        val stmt2 = newStatement(statementDetails = newStatementDetails(statementId = STMT_ID_2))
        whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(
            stmt1, stmt2
        ))

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, DEFAULT_CLASSIFICATION, PROCESSING_OPTIONS), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).isEmpty()
        assertThat(output.extractedDocumentIds.statementIds).hasSize(2)

        verify(dataManager).loadInputPdfDocument(DEFAULT_FILE)
        verify(dataExtractor).extractStatementData(any())
        verify(dataManager).saveModel(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
        verify(documentStatementCreator).createBankStatements(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(statementService).insertBankStatementWithTransactions(stmt1)
        verify(statementService).insertBankStatementWithTransactions(stmt2)
    }

    @Test
    fun testExtraPageModel() {
        val classification = newClassification(type = DocumentType.ExtraPageTypes.TEXT)
        whenever(dataManager.loadInputPdfDocument(any())).thenReturn(newPdfDocument(file = classification.inputFile))
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)

        assertThatThrownBy { activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, classification, PROCESSING_OPTIONS), context) }
            .hasMessageContaining("extra page model")

        verify(dataManager).loadInputPdfDocument(classification.inputFile)
        verify(dataManager).saveModel(classification, ExtraPageDataModel(classification))
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
    }

    // ── ProcessingOptions behavior ──────────────────────────────────────────────────────────────
    // The 2-step pipeline: (1) AI analysis → model blob, (2) model → DB records.
    // Baseline rule: if a step isn't done yet, always complete it regardless of options.
    // Options only affect behavior when the step has already been completed.

    @Nested
    inner class WhenAlreadyAnalyzed {

        private val analyzedBank = newClassification(FILE_ID, CLASSFN_ID, WF_BANK, analyzed = true)
        private val analyzedCheck = newClassification(FILE_ID, CLASSFN_ID, DocumentType.CheckTypes.CHECKS, analyzed = true)

        // ── Bank statement cases ─────────────────────────────────────────────────────────────

        @Test
        fun `analyzed, no existing statements - loads saved model without re-running AI, creates statements`() {
            whenever(dataManager.loadModel(analyzedBank)).thenReturn(newStatementModel())
            whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(DEFAULT_STATEMENT))
            whenever(statementService.loadStatementIdsForClassification(any())).thenReturn(emptySet())

            val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, analyzedBank, PROCESSING_OPTIONS), context)

            assertThat(output.extractedDocumentIds.statementIds).containsExactly(DEFAULT_STATEMENT.statementId)
            verify(statementService).loadStatementIdsForClassification(CLASSFN_ID)
            verify(dataManager).loadModel(analyzedBank)
            verify(documentStatementCreator).createBankStatements(analyzedBank, newStatementModel())
            verify(statementService).insertBankStatementWithTransactions(DEFAULT_STATEMENT)
        }

        @Test
        fun `analyzed, has statements, default options - skips entirely, returns existing statement IDs`() {
            whenever(statementService.loadStatementIdsForClassification(CLASSFN_ID)).thenReturn(setOf(STMT_ID))

            val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, analyzedBank, PROCESSING_OPTIONS), context)

            assertThat(output.extractedDocumentIds.statementIds).containsExactly(STMT_ID)
            verify(statementService).loadStatementIdsForClassification(CLASSFN_ID)
            // no AI, no model load, no DB writes
        }

        @Test
        fun `analyzed, has statements, forceRecreate - loads model, inserts new statements without deleting existing`() {
            whenever(statementService.loadStatementIdsForClassification(any())).thenReturn(setOf(STMT_ID))
            whenever(dataManager.loadModel(any())).thenReturn(newStatementModel())
            whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(DEFAULT_STATEMENT))

            val output = activity.processDataModel(
                ProcessDataModelActivityInput(REQUEST_ID, analyzedBank,
                    processingOptions = ClassificationProcessingOptions(forceRecreate = true)),
                context,
            )

            assertThat(output.extractedDocumentIds.statementIds).containsExactly(DEFAULT_STATEMENT.statementId)
            verify(statementService).loadStatementIdsForClassification(CLASSFN_ID)
            verify(dataManager).loadModel(analyzedBank)
            verify(documentStatementCreator).createBankStatements(analyzedBank, newStatementModel())
            verify(statementService).insertBankStatementWithTransactions(DEFAULT_STATEMENT)
        }

        @Test
        fun `analyzed, has statements, forceRecreate + replaceOnRecreate - atomically deletes old statements and inserts new`() {
            whenever(statementService.loadStatementIdsForClassification(any())).thenReturn(setOf(STMT_ID))
            whenever(dataManager.loadModel(any())).thenReturn(newStatementModel())
            whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(DEFAULT_STATEMENT))

            val output = activity.processDataModel(
                ProcessDataModelActivityInput(REQUEST_ID, analyzedBank,
                    processingOptions = ClassificationProcessingOptions(forceRecreate = true, replaceOnRecreate = true)),
                context,
            )

            assertThat(output.extractedDocumentIds.statementIds).containsExactly(DEFAULT_STATEMENT.statementId)
            verify(statementService).loadStatementIdsForClassification(CLASSFN_ID)
            verify(dataManager).loadModel(analyzedBank)
            verify(documentStatementCreator).createBankStatements(analyzedBank, newStatementModel())
            verify(statementService).replaceStatements(CLASSFN_ID, listOf(DEFAULT_STATEMENT))
        }

        @Test
        fun `forceReanalysis - re-runs AI even when model already exists, saves new model, creates statements`() {
            whenever(dataManager.loadInputPdfDocument(any())).thenReturn(newPdfDocument())
            whenever(dataExtractor.extractStatementData(any())).thenReturn(newStatementModel())
            whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)
            whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(DEFAULT_STATEMENT))

            val output = activity.processDataModel(
                ProcessDataModelActivityInput(REQUEST_ID, analyzedBank,
                    processingOptions = ClassificationProcessingOptions(forceReanalysis = true)),
                context,
            )

            assertThat(output.extractedDocumentIds.statementIds).containsExactly(DEFAULT_STATEMENT.statementId)
            verify(dataManager).loadInputPdfDocument(analyzedBank.inputFile)
            verify(dataExtractor).extractStatementData(any())
            verify(dataManager).saveModel(analyzedBank, newStatementModel())
            verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
            verify(documentStatementCreator).createBankStatements(analyzedBank, newStatementModel())
            verify(statementService).insertBankStatementWithTransactions(DEFAULT_STATEMENT)
        }

        @Test
        fun `forceReanalysis + replaceOnRecreate - re-runs AI and atomically replaces existing statements`() {
            whenever(dataManager.loadInputPdfDocument(any())).thenReturn(newPdfDocument())
            whenever(dataExtractor.extractStatementData(any())).thenReturn(newStatementModel())
            whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)
            whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(DEFAULT_STATEMENT))

            val output = activity.processDataModel(
                ProcessDataModelActivityInput(REQUEST_ID, analyzedBank,
                    processingOptions = ClassificationProcessingOptions(forceReanalysis = true, replaceOnRecreate = true)),
                context,
            )

            assertThat(output.extractedDocumentIds.statementIds).containsExactly(DEFAULT_STATEMENT.statementId)
            verify(dataManager).loadInputPdfDocument(analyzedBank.inputFile)
            verify(dataExtractor).extractStatementData(any())
            verify(dataManager).saveModel(analyzedBank, newStatementModel())
            verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
            verify(documentStatementCreator).createBankStatements(analyzedBank, newStatementModel())
            verify(statementService).replaceStatements(CLASSFN_ID, listOf(DEFAULT_STATEMENT))
        }

        // ── Check cases ──────────────────────────────────────────────────────────────────────

        @Test
        fun `analyzed check, no existing checks - loads saved model without re-running AI, creates checks`() {
            val checkDataModel = newCheckDataModel(classification = analyzedCheck)
            whenever(dataManager.loadModel(analyzedCheck)).thenReturn(checkDataModel)
            whenever(checkService.loadCheckIdsForClassification(CLASSFN_ID)).thenReturn(emptySet())

            val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, analyzedCheck, PROCESSING_OPTIONS), context)

            assertThat(output.extractedDocumentIds.checkIds).hasSize(1)
            verify(checkService).loadCheckIdsForClassification(CLASSFN_ID)
            verify(dataManager).loadModel(analyzedCheck)
            val checkCaptor = argumentCaptor<CheckDetails>()
            verify(checkService).insertCheck(eq(analyzedCheck), checkCaptor.capture())
            assertThat(checkCaptor.firstValue).entityCompare().isEqualTo(newCheckDetails())
        }

        @Test
        fun `analyzed check, has checks, default options - skips entirely, returns existing check IDs`() {
            whenever(checkService.loadCheckIdsForClassification(CLASSFN_ID)).thenReturn(setOf(CHECK_ID))

            val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, analyzedCheck, PROCESSING_OPTIONS), context)

            assertThat(output.extractedDocumentIds.checkIds).containsExactly(CHECK_ID)
            verify(checkService).loadCheckIdsForClassification(CLASSFN_ID)
            // no AI, no model load, no DB writes
        }

        @Test
        fun `analyzed check, has checks, forceRecreate + replaceOnRecreate - atomically replaces checks`() {
            val checkDataModel = newCheckDataModel(classification = analyzedCheck)
            whenever(checkService.loadCheckIdsForClassification(CLASSFN_ID)).thenReturn(setOf(CHECK_ID))
            whenever(dataManager.loadModel(analyzedCheck)).thenReturn(checkDataModel)

            val output = activity.processDataModel(
                ProcessDataModelActivityInput(REQUEST_ID, analyzedCheck,
                    processingOptions = ClassificationProcessingOptions(forceRecreate = true, replaceOnRecreate = true)),
                context,
            )

            assertThat(output.extractedDocumentIds.checkIds).hasSize(1)
            verify(checkService).loadCheckIdsForClassification(CLASSFN_ID)
            verify(dataManager).loadModel(analyzedCheck)
            val checkCaptor = argumentCaptor<List<CheckDetails>>()
            verify(checkService).replaceChecks(eq(analyzedCheck), checkCaptor.capture())
            assertThat(checkCaptor.firstValue).hasSize(1)
            assertThat(checkCaptor.firstValue.first()).entityCompare().isEqualTo(newCheckDetails())
        }
    }

    companion object {
        private val STORAGE_LOCATION = StorageLocation("test", "file/path", Extension.JSON)
        private val PROCESSING_OPTIONS = ClassificationProcessingOptions()
    }
}