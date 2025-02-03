package com.goldberg.law.function

import com.goldberg.law.document.model.ModelValues
import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.StatementModelValues
import com.goldberg.law.document.model.pdf.PdfDocumentMetadata
import com.goldberg.law.function.activity.*
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.function.model.activity.*
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.AzureAnalyzeDocumentsRequest
import com.goldberg.law.function.model.tracking.DocumentOrchestrationStatus
import com.goldberg.law.function.model.tracking.OrchestrationStage
import com.goldberg.law.function.model.tracking.OrchestrationStatus
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskOrchestrationContext
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class PdfDataExtractorOrchestratorFunctionTest {

    private val dataExtractorOrchestratorFunction: PdfDataExtractorOrchestratorFunction = PdfDataExtractorOrchestratorFunction(2)

    @Mock
    private val mockContext: TaskOrchestrationContext = mock()

    @Mock
    private val getFilesToProcessTask: Task<GetFilesToProcessActivityOutput> = mock()
    @Mock
    private val splitPdfTask: Task<SplitPdfActivityOutput> = mock()
    @Mock
    private val splitPdfTasks: Task<List<SplitPdfActivityOutput>> = mock()

    @Mock
    private val processDataModelTask: Task<DocumentDataModelContainer> = mock()
    @Mock
    private val processDataModelTasks: Task<List<DocumentDataModelContainer>> = mock()

    @Mock
    private val updateMetadataTask: Task<Void> = mock()
    @Mock
    private val updateMetadataTasks: Task<List<Void>> = mock()

    @Mock
    private val loadAnalyzedModelsTask: Task<LoadAnalyzedModelsActivityOutput> = mock()
    @Mock
    private val processStatementsTask: Task<ProcessStatementsActivityOutput> = mock()

    @BeforeEach
    fun setup() {
        whenever(mockContext.instanceId).thenReturn(REQUEST_ID)
        whenever(mockContext.isReplaying).thenReturn(false)

        whenever(mockContext.callActivity(eq(GetFilesToProcessActivity.FUNCTION_NAME), any(), eq(GetFilesToProcessActivityOutput::class.java)))
            .thenReturn(getFilesToProcessTask)

        whenever(mockContext.callActivity(eq(SplitPdfActivity.FUNCTION_NAME), any(), eq(SplitPdfActivityOutput::class.java)))
            .thenReturn(splitPdfTask)
        whenever(mockContext.allOf(argThat { list: List<Task<SplitPdfActivityOutput>>? -> list?.contains(splitPdfTask) == true} as List<Task<SplitPdfActivityOutput>>?))
            .thenReturn(splitPdfTasks)

        whenever(mockContext.callActivity(eq(ProcessDataModelActivity.FUNCTION_NAME), any(), eq(DocumentDataModelContainer::class.java)))
            .thenReturn(processDataModelTask)
        whenever(mockContext.allOf(argThat { list: List<Task<DocumentDataModelContainer>>? -> list?.contains(processDataModelTask) == true} as List<Task<DocumentDataModelContainer>>?))
            .thenReturn(processDataModelTasks)

        whenever(mockContext.callActivity(eq(UpdateMetadataActivity.FUNCTION_NAME), any<UpdateMetadataActivityInput>()))
            .thenReturn(updateMetadataTask)
        whenever(mockContext.allOf(argThat { list: List<Task<Void>>? -> list?.contains(updateMetadataTask) == true } as List<Task<Void>>?))
            .thenReturn(updateMetadataTasks)

        whenever(mockContext.callActivity(eq(LoadAnalyzedModelsActivity.FUNCTION_NAME), any(), eq(LoadAnalyzedModelsActivityOutput::class.java)))
            .thenReturn(loadAnalyzedModelsTask)
        whenever(mockContext.callActivity(eq(ProcessStatementsActivity.FUNCTION_NAME), any(), eq(ProcessStatementsActivityOutput::class.java)))
            .thenReturn(processStatementsTask)
    }

    @Test
    fun testBasicNewDocuments() {
        val metadata1 = PdfDocumentMetadata(FILE_10, InputFileMetadata())
        val metadata2 = PdfDocumentMetadata(FILE_20, InputFileMetadata())
        val metadata3 = PdfDocumentMetadata(FILE_30, InputFileMetadata())
        val metadata4 = PdfDocumentMetadata(FILE_40, InputFileMetadata())

        whenever(mockContext.getInput(AzureAnalyzeDocumentsRequest::class.java)).thenReturn(INPUT)
        whenever(getFilesToProcessTask.await()).thenReturn(GetFilesToProcessActivityOutput(
            setOf(metadata1, metadata2, metadata3, metadata4))
        )

        whenever(splitPdfTasks.await()).thenReturn(
            listOf(
                SplitPdfActivityOutput(setOf(PDF_PAGE_10_1, PDF_PAGE_10_2, PDF_PAGE_10_3)),
                SplitPdfActivityOutput(setOf(PDF_PAGE_20_1, PDF_PAGE_20_2, PDF_PAGE_20_3)),
                SplitPdfActivityOutput(setOf(PDF_PAGE_30_1, PDF_PAGE_30_2)),
                SplitPdfActivityOutput(setOf(PDF_PAGE_40_1)),
            )
        )

        whenever(processDataModelTasks.await())
            .thenReturn(
                listOf(
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_0),
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_1),
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.EAGLE_BANK_1),
                ),
                listOf(
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_2),
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_3),
                ),
                listOf(
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_4),
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.C1_VENTURE_0),
                ),
                listOf(
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.C1_VENTURE_1),
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.EAGLE_BANK_0),
                )
            )

        whenever(processStatementsTask.await()).thenReturn(
            ProcessStatementsActivityOutput(mapOf(
                FILE_10 to setOf(STATEMENT_1),
                FILE_20 to setOf(STATEMENT_2),
                FILE_30 to setOf(STATEMENT_3)
            ))
        )

        val result = dataExtractorOrchestratorFunction.pdfDataExtractorOrchestrator(mockContext)

        assertThat(result).isEqualTo(AnalyzeDocumentResult(AnalyzeDocumentResult.Status.SUCCESS, mapOf(
            FILE_10 to setOf(STATEMENT_1),
            FILE_20 to setOf(STATEMENT_2),
            FILE_30 to setOf(STATEMENT_3)
        ), null))

        verify(mockContext).getInput(AzureAnalyzeDocumentsRequest::class.java)
        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.VERIFYING_DOCUMENTS, listOf(
            DocumentOrchestrationStatus(FILE_10, null, null, null),
            DocumentOrchestrationStatus(FILE_20, null, null, null),
            DocumentOrchestrationStatus(FILE_30, null, null, null),
            DocumentOrchestrationStatus(FILE_40, null, null, null),
        ), null, null))
        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 0, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_20, 3, 0, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_30, 2, 0, InputFileMetadata(true, 2)),
            DocumentOrchestrationStatus(FILE_40, 1, 0, InputFileMetadata(true, 1)),
        ), 9, 0))

        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 1, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_20, 3, 1, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_30, 2, 0, InputFileMetadata(true, 2)),
            DocumentOrchestrationStatus(FILE_40, 1, 1, InputFileMetadata(true, 1, true)),
        ), 9, 3))

        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 2, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_20, 3, 2, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_30, 2, 0, InputFileMetadata(true, 2)),
            DocumentOrchestrationStatus(FILE_40, 1, 1, InputFileMetadata(true, 1, true)),
        ), 9, 5))

        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 3, InputFileMetadata(true, 3, true)),
            DocumentOrchestrationStatus(FILE_20, 3, 2, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_30, 2, 1, InputFileMetadata(true, 2)),
            DocumentOrchestrationStatus(FILE_40, 1, 1, InputFileMetadata(true, 1, true)),
        ), 9, 7))

        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 3, InputFileMetadata(true, 3, true)),
            DocumentOrchestrationStatus(FILE_20, 3, 3, InputFileMetadata(true, 3, true)),
            DocumentOrchestrationStatus(FILE_30, 2, 2, InputFileMetadata(true, 2, true)),
            DocumentOrchestrationStatus(FILE_40, 1, 1, InputFileMetadata(true, 1, true)),
        ), 9, 9))

        verify(mockContext, atLeastOnce()).isReplaying
        verify(mockContext, atLeastOnce()).instanceId
        verify(mockContext).allOf(argThat { list: List<Task<SplitPdfActivityOutput>>? -> list?.contains(splitPdfTask) == true} as List<Task<SplitPdfActivityOutput>>?)
        verify(mockContext, times(4)).allOf(argThat { list: List<Task<DocumentDataModelContainer>>? -> list?.contains(processDataModelTask) == true} as List<Task<DocumentDataModelContainer>>?)
        verify(mockContext).allOf(argThat { list: List<Task<Void>>? -> list?.contains(updateMetadataTask) == true } as List<Task<Void>>?)

        verify(mockContext).callActivity(GetFilesToProcessActivity.FUNCTION_NAME, GetFilesToProcessActivityInput(REQUEST_ID, INPUT), GetFilesToProcessActivityOutput::class.java)

        verify(mockContext).callActivity(SplitPdfActivity.FUNCTION_NAME, SplitPdfActivityInput(REQUEST_ID, CLIENT_NAME, FILE_10, false), SplitPdfActivityOutput::class.java)
        verify(mockContext).callActivity(SplitPdfActivity.FUNCTION_NAME, SplitPdfActivityInput(REQUEST_ID, CLIENT_NAME, FILE_20, false), SplitPdfActivityOutput::class.java)
        verify(mockContext).callActivity(SplitPdfActivity.FUNCTION_NAME, SplitPdfActivityInput(REQUEST_ID, CLIENT_NAME, FILE_30, false), SplitPdfActivityOutput::class.java)
        verify(mockContext).callActivity(SplitPdfActivity.FUNCTION_NAME, SplitPdfActivityInput(REQUEST_ID, CLIENT_NAME, FILE_40, false), SplitPdfActivityOutput::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_10_1, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_10_2, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_10_3, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_20_1, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_20_2, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_20_3, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_30_1, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_30_2, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_40_1, null), DocumentDataModelContainer::class.java)

        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, FILE_10, InputFileMetadata(true,3, true)))
        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, FILE_20, InputFileMetadata(true,3, true)))
        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, FILE_30, InputFileMetadata(true,2, true)))
        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, FILE_40, InputFileMetadata(true,1, true)))

        val metadataMap = mapOf(
            FILE_10 to InputFileMetadata(true, 3, true),
            FILE_20 to InputFileMetadata(true, 3, true),
            FILE_30 to InputFileMetadata(true, 2, true),
            FILE_40 to InputFileMetadata(true, 1, true),
        )
        verify(mockContext).callActivity(ProcessStatementsActivity.FUNCTION_NAME, ProcessStatementsActivityInput(REQUEST_ID, CLIENT_NAME,
            setOf(
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_0),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_1),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_2),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_3),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_4),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.C1_VENTURE_0),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.C1_VENTURE_1),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.EAGLE_BANK_0),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.EAGLE_BANK_1),
            ), metadataMap), ProcessStatementsActivityOutput::class.java)

        verifyNoMoreInteractions(mockContext)
    }

    @Test
    fun testAllTypesOfMetadata() {
        whenever(mockContext.getInput(AzureAnalyzeDocumentsRequest::class.java)).thenReturn(INPUT)
        whenever(getFilesToProcessTask.await()).thenReturn(GetFilesToProcessActivityOutput(
            setOf(PDF_METADATA_10, PDF_METADATA_20, PDF_METADATA_30, PDF_METADATA_40))
        )

        whenever(splitPdfTasks.await()).thenReturn(
            listOf(SplitPdfActivityOutput(setOf(PDF_PAGE_10_1, PDF_PAGE_10_2, PDF_PAGE_10_3)))
        )

        whenever(processDataModelTasks.await())
            .thenReturn(
                listOf(
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_0),
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_1),
                ),
                listOf(
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_2),
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_3),
                ),
                listOf(
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_4),
                    DocumentDataModelContainer(statementDataModel = StatementModelValues.C1_VENTURE_0),
                )
            )

        whenever(loadAnalyzedModelsTask.await()).thenReturn(LoadAnalyzedModelsActivityOutput(models = listOf(
            DocumentDataModelContainer(statementDataModel = StatementModelValues.DOUBLE_CASH_0),
            DocumentDataModelContainer(statementDataModel = StatementModelValues.DOUBLE_CASH_1),
        )))

        whenever(processStatementsTask.await()).thenReturn(
            ProcessStatementsActivityOutput(mapOf(
                FILE_10 to setOf(STATEMENT_1),
                FILE_20 to setOf(STATEMENT_2),
                FILE_30 to setOf(STATEMENT_3, STATEMENT_4),
            ))
        )

        val result = dataExtractorOrchestratorFunction.pdfDataExtractorOrchestrator(mockContext)

        assertThat(result).isEqualTo(AnalyzeDocumentResult(AnalyzeDocumentResult.Status.SUCCESS, mapOf(
            FILE_10 to setOf(STATEMENT_1),
            FILE_20 to setOf(STATEMENT_2),
            FILE_30 to setOf(STATEMENT_3, STATEMENT_4),
            FILE_40 to setOf(STATEMENT_5, STATEMENT_6)
        ), null))

        verify(mockContext).getInput(AzureAnalyzeDocumentsRequest::class.java)
        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.VERIFYING_DOCUMENTS, listOf(
            DocumentOrchestrationStatus(FILE_10, null, null, null),
            DocumentOrchestrationStatus(FILE_20, null, null, null),
            DocumentOrchestrationStatus(FILE_30, null, null, null),
            DocumentOrchestrationStatus(FILE_40, null, null, null),
        ), null, null))
        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 0, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_20, 3, 0, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_30, 2, 2, InputFileMetadata(true, 2, true)),
            DocumentOrchestrationStatus(FILE_40, 40, 40, InputFileMetadata(true, 40, true, setOf(STATEMENT_5, STATEMENT_6))),
        ), 6, 0))

        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 1, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_20, 3, 1, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_30, 2, 2, InputFileMetadata(true, 2, true)),
            DocumentOrchestrationStatus(FILE_40, 40, 40, InputFileMetadata(true, 40, true, setOf(STATEMENT_5, STATEMENT_6))),
        ), 6, 2))

        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 2, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_20, 3, 2, InputFileMetadata(true, 3)),
            DocumentOrchestrationStatus(FILE_30, 2, 2, InputFileMetadata(true, 2, true)),
            DocumentOrchestrationStatus(FILE_40, 40, 40, InputFileMetadata(true, 40, true, setOf(STATEMENT_5, STATEMENT_6))),
        ), 6, 4))

        verify(mockContext).setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, listOf(
            DocumentOrchestrationStatus(FILE_10, 3, 3, InputFileMetadata(true, 3, true)),
            DocumentOrchestrationStatus(FILE_20, 3, 3, InputFileMetadata(true, 3, true)),
            DocumentOrchestrationStatus(FILE_30, 2, 2, InputFileMetadata(true, 2, true)),
            DocumentOrchestrationStatus(FILE_40, 40, 40, InputFileMetadata(true, 40, true, setOf(STATEMENT_5, STATEMENT_6))),
        ), 6, 6))

        verify(mockContext, atLeastOnce()).isReplaying
        verify(mockContext, atLeastOnce()).instanceId
        verify(mockContext).allOf(argThat { list: List<Task<SplitPdfActivityOutput>>? -> list?.contains(splitPdfTask) == true} as List<Task<SplitPdfActivityOutput>>?)
        verify(mockContext, times(3)).allOf(argThat { list: List<Task<DocumentDataModelContainer>>? -> list?.contains(processDataModelTask) == true} as List<Task<DocumentDataModelContainer>>?)
        verify(mockContext).allOf(argThat { list: List<Task<Void>>? -> list?.contains(updateMetadataTask) == true } as List<Task<Void>>?)

        verify(mockContext).callActivity(GetFilesToProcessActivity.FUNCTION_NAME, GetFilesToProcessActivityInput(REQUEST_ID, INPUT), GetFilesToProcessActivityOutput::class.java)

        verify(mockContext).callActivity(SplitPdfActivity.FUNCTION_NAME, SplitPdfActivityInput(REQUEST_ID, CLIENT_NAME, FILE_10, false), SplitPdfActivityOutput::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_10_1, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_10_2, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_10_3, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_20_1, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_20_2, null), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, PDF_PAGE_20_3, null), DocumentDataModelContainer::class.java)

        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, FILE_10, InputFileMetadata(true,3, true)))
        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, FILE_20, InputFileMetadata(true,3, true)))

        verify(mockContext).callActivity(LoadAnalyzedModelsActivity.FUNCTION_NAME, LoadAnalyzedModelsActivityInput(
            REQUEST_ID, CLIENT_NAME, setOf(PDF_PAGE_30_1, PDF_PAGE_30_2)), LoadAnalyzedModelsActivityOutput::class.java
        )

        val metadataMap = mapOf(
            FILE_10 to InputFileMetadata(true, 3, true),
            FILE_20 to InputFileMetadata(true, 3, true),
            FILE_30 to InputFileMetadata(true, 2, true),
            FILE_40 to InputFileMetadata(true, 40, true, setOf(STATEMENT_5, STATEMENT_6)),
        )
        verify(mockContext).callActivity(ProcessStatementsActivity.FUNCTION_NAME, ProcessStatementsActivityInput(REQUEST_ID, CLIENT_NAME,
            setOf(
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_0),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_1),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_2),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_3),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.STATEMENT_MODEL_WF_BANK_4),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.C1_VENTURE_0),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.DOUBLE_CASH_0),
                DocumentDataModelContainer(statementDataModel = StatementModelValues.DOUBLE_CASH_1),
            ), metadataMap), ProcessStatementsActivityOutput::class.java)

        verifyNoMoreInteractions(mockContext)
    }

    companion object {
        private const val REQUEST_ID = "a12345"

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
        private const val FILE_40 = "File40"

        private val INPUT = AzureAnalyzeDocumentsRequest(CLIENT_NAME, setOf(FILE_10, FILE_20, FILE_30, FILE_40))

        private const val SAVED_FILE_1 = "File1"
        private const val SAVED_FILE_2 = "File2"
        private const val SAVED_FILE_3 = "File3"
        private const val SAVED_FILE_4 = "File4"

        private const val STATEMENT_1 = "Statement1"
        private const val STATEMENT_2 = "Statement2"
        private const val STATEMENT_3 = "Statement3"
        private const val STATEMENT_4 = "Statement4"
        private const val STATEMENT_5 = "Statement5"
        private const val STATEMENT_6 = "Statement6"

        private val PDF_METADATA_10 = PdfDocumentMetadata(FILE_10, InputFileMetadata())
        private val PDF_METADATA_20 = PdfDocumentMetadata(FILE_20, InputFileMetadata(true, 3))
        private val PDF_METADATA_30 = PdfDocumentMetadata(FILE_30, InputFileMetadata(true, 2, true))
        private val PDF_METADATA_40 = PdfDocumentMetadata(FILE_40, InputFileMetadata(true, 40, true, setOf(STATEMENT_5, STATEMENT_6)))

        private val PDF_PAGE_10_1 = PdfPageData(FILE_10, 1)
        private val PDF_PAGE_10_2 = PdfPageData(FILE_10, 2)
        private val PDF_PAGE_10_3 = PdfPageData(FILE_10, 3)

        private val PDF_PAGE_20_1 = PdfPageData(FILE_20, 1)
        private val PDF_PAGE_20_2 = PdfPageData(FILE_20, 2)
        private val PDF_PAGE_20_3 = PdfPageData(FILE_20, 3)

        private val PDF_PAGE_30_1 = PdfPageData(FILE_30, 1)
        private val PDF_PAGE_30_2 = PdfPageData(FILE_30, 2)

        private val PDF_PAGE_40_1 = PdfPageData(FILE_40, 1)
    }
}