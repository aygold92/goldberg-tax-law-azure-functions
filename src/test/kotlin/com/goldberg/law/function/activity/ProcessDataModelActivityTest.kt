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
import com.goldberg.law.document.model.StatementModelValues.newClassifiedPdfDocument
import com.goldberg.law.document.model.StatementModelValues.newPdfDocument
import com.goldberg.law.document.model.StatementModelValues.newStatementModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.entity.EntityValues.CLASSFN_ID
import com.goldberg.law.entity.EntityValues.DEFAULT_CLASSIFICATION
import com.goldberg.law.entity.EntityValues.DEFAULT_FILE
import com.goldberg.law.entity.EntityValues.DEFAULT_STATEMENT
import com.goldberg.law.entity.EntityValues.FILE_ID
import com.goldberg.law.entity.EntityValues.STMT_ID_2
import com.goldberg.law.entity.EntityValues.entityCompare
import com.goldberg.law.entity.EntityValues.newCheckDetails
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newInputFile
import com.goldberg.law.entity.EntityValues.newStatement
import com.goldberg.law.entity.EntityValues.newStatementDetails
import com.goldberg.law.function.activity.model.ProcessDataModelActivityInput
import com.goldberg.law.function.activity.model.ProcessDataModelActivityOutput
import com.goldberg.law.function.model.ExtractedDocumentIds
import com.microsoft.azure.functions.ExecutionContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
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
        verifyNoMoreInteractions(dataManager, dataExtractor, classificationService, statementService, checkService, documentStatementCreator, context)
    }

    @Test
    fun testCheckModel() {
        val classification = newClassification(type = DocumentType.CheckTypes.CHECKS)
        val classifiedPdfDocument = newClassifiedPdfDocument(classification = classification)
        val checkDataModel = newCheckDataModel(classification = classification)
        whenever(dataManager.loadSplitPdfDocument(any())).thenReturn(classifiedPdfDocument)
        whenever(dataExtractor.extractCheckData(any())).thenReturn(checkDataModel)
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, classification), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).hasSize(1)
        assertThat(output.extractedDocumentIds.statementIds).isEmpty()

        verify(dataManager).loadSplitPdfDocument(classification)
        verify(dataExtractor).extractCheckData(classifiedPdfDocument)
        verify(dataManager).saveModel(classification, checkDataModel)
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
        val checkCaptor = argumentCaptor<CheckDetails>()
        verify(checkService).insertCheck(eq(classification), checkCaptor.capture())
        assertThat(checkCaptor.firstValue).entityCompare().isEqualTo(newCheckDetails())
    }

    @Test
    fun testCheckModelMultiple() {
        val classification = newClassification(type = DocumentType.CheckTypes.CHECKS_RAW)
        val classifiedPdfDocument = newClassifiedPdfDocument(classification = classification)
        val model = newCheckDataModel(checkEntries = arrayOf(
            newCheckEntriesTableRow(),
            newCheckEntriesTableRow(checkNumber = 1001),
        ))
        whenever(dataManager.loadSplitPdfDocument(any())).thenReturn(classifiedPdfDocument)
        whenever(dataExtractor.extractCheckData(any())).thenReturn(model)
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, classification), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).hasSize(2)
        assertThat(output.extractedDocumentIds.statementIds).isEmpty()

        verify(dataManager).loadSplitPdfDocument(classification)
        verify(dataExtractor).extractCheckData(classifiedPdfDocument)
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
        val classifiedPdfDocument = newClassifiedPdfDocument()
        whenever(dataManager.loadSplitPdfDocument(any())).thenReturn(classifiedPdfDocument)
        whenever(dataExtractor.extractStatementData(any())).thenReturn(newStatementModel())
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)
        whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(DEFAULT_STATEMENT))

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, DEFAULT_CLASSIFICATION), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).isEmpty()
        assertThat(output.extractedDocumentIds.statementIds).hasSize(1)

        verify(dataManager).loadSplitPdfDocument(DEFAULT_CLASSIFICATION)
        verify(dataExtractor).extractStatementData(classifiedPdfDocument)
        verify(dataManager).saveModel(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
        verify(documentStatementCreator).createBankStatements(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(statementService).insertBankStatementWithTransactions(DEFAULT_STATEMENT)
    }

    @Test
    fun testStatementModelMultiple() {
        val classifiedPdfDocument = newClassifiedPdfDocument(classification = DEFAULT_CLASSIFICATION)
        whenever(dataManager.loadSplitPdfDocument(any())).thenReturn(classifiedPdfDocument)
        whenever(dataExtractor.extractStatementData(any())).thenReturn(newStatementModel())
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)
        val stmt1 = newStatement()
        val stmt2 = newStatement(statementDetails = newStatementDetails(statementId = STMT_ID_2))
        whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(
            stmt1, stmt2
        ))

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, DEFAULT_CLASSIFICATION), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).isEmpty()
        assertThat(output.extractedDocumentIds.statementIds).hasSize(2)

        verify(dataManager).loadSplitPdfDocument(DEFAULT_CLASSIFICATION)
        verify(dataExtractor).extractStatementData(classifiedPdfDocument)
        verify(dataManager).saveModel(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
        verify(documentStatementCreator).createBankStatements(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(statementService).insertBankStatementWithTransactions(stmt1)
        verify(statementService).insertBankStatementWithTransactions(stmt2)
    }

    @Test
    fun testUseFullDocument() {
        val pdfDocument = newClassifiedPdfDocument()
        whenever(dataManager.loadInputPdfDocument(any())).thenReturn(pdfDocument)
        whenever(dataExtractor.extractStatementData(any())).thenReturn(newStatementModel())
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)
        whenever(documentStatementCreator.createBankStatements(any(), any())).thenReturn(listOf(DEFAULT_STATEMENT))

        val output = activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, DEFAULT_CLASSIFICATION, true), context)

        assertThat(output.fileId).isEqualTo(FILE_ID)
        assertThat(output.extractedDocumentIds.checkIds).isEmpty()
        assertThat(output.extractedDocumentIds.statementIds).hasSize(1)

        verify(dataManager).loadInputPdfDocument(DEFAULT_FILE)
        verify(dataExtractor).extractStatementData(pdfDocument)
        verify(dataManager).saveModel(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
        verify(documentStatementCreator).createBankStatements(DEFAULT_CLASSIFICATION, newStatementModel())
        verify(statementService).insertBankStatementWithTransactions(DEFAULT_STATEMENT)
    }

    @Test
    fun testExtraPageModel() {
        val classification = newClassification(type = DocumentType.ExtraPageTypes.TEXT)
        val classifiedPdfDocument = newClassifiedPdfDocument(classification = classification)
        whenever(dataManager.loadSplitPdfDocument(any())).thenReturn(classifiedPdfDocument)
        whenever(dataManager.saveModel(any(), any())).thenReturn(STORAGE_LOCATION)

        assertThatThrownBy { activity.processDataModel(ProcessDataModelActivityInput(REQUEST_ID, classification), context) }
            .hasMessageContaining("extra page model")

        verify(dataManager).loadSplitPdfDocument(classification)
        verify(dataManager).saveModel(classification, ExtraPageDataModel(classification))
        verify(classificationService).updateModelLocation(CLASSFN_ID, STORAGE_LOCATION)
    }

    companion object {
        private val STORAGE_LOCATION = StorageLocation("test", "file/path", Extension.JSON)
    }
}