package com.goldberg.law.function

import com.goldberg.law.document.model.ModelValues
import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.REQUEST_ID
import com.goldberg.law.document.model.ModelValues.newPdfMetadata
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_1
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_2
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_3
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_4
import com.goldberg.law.document.model.StatementModelValues.Companion.OTHER_FILENAME
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_1
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_2
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_3
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_4
import com.goldberg.law.document.model.StatementModelValues.Companion.newStatementModel
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.function.activity.*
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.activity.*
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.AzureAnalyzeDocumentsRequest
import com.goldberg.law.function.model.tracking.OrchestrationStage
import com.goldberg.law.function.model.tracking.OrchestrationStatus
import com.goldberg.law.function.model.tracking.OrchestrationStatusFactory
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskOrchestrationContext
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class PdfDataExtractorOrchestratorFunctionTest {

    private val numWorkers = 2

    @Mock
    private val mockContext: TaskOrchestrationContext = mock()

    @Mock
    private val orchestrationStatusFactory: OrchestrationStatusFactory = mock()

    @Mock
    private val orchestrationStatus: OrchestrationStatus = mock()

    @Mock
    private val concurrentExecutionOrchestrator: ConcurrentExecutionOrchestrator = mock()

    @Mock
    private val getFilesToProcessTask: Task<GetFilesToProcessActivityOutput> = mock()

    @Mock
    private val loadDocumentClassificationsTask: Task<LoadDocumentClassificationsActivityOutput> = mock()

    @Mock
    private val updateMetadataTask: Task<Void> = mock()

    @Mock
    private val updateMetadataTasks: Task<List<Void>> = mock()
    @Mock
    private val loadAnalyzedModelsTask: Task<LoadAnalyzedModelsActivityOutput> = mock()

    @Mock
    private val processStatementsTask: Task<ProcessStatementsActivityOutput> = mock()

    private val dataExtractorOrchestratorFunction: PdfDataExtractorOrchestratorFunction = PdfDataExtractorOrchestratorFunction(numWorkers, concurrentExecutionOrchestrator, orchestrationStatusFactory)

    @BeforeEach
    fun setup() {
        whenever(mockContext.instanceId).thenReturn(REQUEST_ID)
        whenever(mockContext.isReplaying).thenReturn(false)

        whenever(mockContext.callActivity(eq(GetFilesToProcessActivity.FUNCTION_NAME), any(), eq(GetFilesToProcessActivityOutput::class.java)))
            .thenReturn(getFilesToProcessTask)

        whenever(orchestrationStatusFactory.new(any(), any(), any()))
            .thenReturn(orchestrationStatus)

        whenever(orchestrationStatus.updateStage(any())).thenReturn(orchestrationStatus)
        whenever(orchestrationStatus.updateDoc(any(), any())).thenReturn(orchestrationStatus)
        whenever(orchestrationStatus.save()).thenReturn(orchestrationStatus)

//        whenever(mockContext.allOf(argThat { list: List<Task<ClassifyDocumentActivityOutput>>? -> list?.contains(classifyPdfTask) == true} as List<Task<ClassifyDocumentActivityOutput>>?))
//            .thenReturn(classifyPdfTasks)
//
//        whenever(mockContext.callActivity(eq(ProcessDataModelActivity.FUNCTION_NAME), any(), eq(DocumentDataModelContainer::class.java)))
//            .thenReturn(processDataModelTask)
//        whenever(mockContext.allOf(argThat { list: List<Task<DocumentDataModelContainer>>? -> list?.contains(processDataModelTask) == true} as List<Task<DocumentDataModelContainer>>?))
//            .thenReturn(processDataModelTasks)

        whenever(mockContext.callActivity(eq(LoadDocumentClassificationsActivity.FUNCTION_NAME), any(), eq(LoadDocumentClassificationsActivityOutput::class.java)))
            .thenReturn(loadDocumentClassificationsTask)

        whenever(mockContext.callActivity(eq(UpdateMetadataActivity.FUNCTION_NAME), any<UpdateMetadataActivityInput>()))
            .thenReturn(updateMetadataTask)
        whenever(mockContext.allOf(argThat { list: List<Task<Void>>? -> list?.contains(updateMetadataTask) == true } as List<Task<Void>>?))
            .thenReturn(updateMetadataTasks)

        whenever(mockContext.callActivity(eq(LoadAnalyzedModelsActivity.FUNCTION_NAME), any(), eq(LoadAnalyzedModelsActivityOutput::class.java)))
            .thenReturn(loadAnalyzedModelsTask)
        whenever(mockContext.callActivity(eq(ProcessStatementsActivity.FUNCTION_NAME), any(), eq(ProcessStatementsActivityOutput::class.java)))
            .thenReturn(processStatementsTask)

        whenever(mockContext.allOf(any<List<Task<Void>>>()))
            .thenReturn(updateMetadataTasks)
    }

    @Test
    fun testBasicNewDocuments() {
        whenever(mockContext.getInput(AzureAnalyzeDocumentsRequest::class.java)).thenReturn(INPUT)
        whenever(getFilesToProcessTask.await()).thenReturn(GetFilesToProcessActivityOutput(
            mapOf(FILENAME to InputFileMetadata(), OTHER_FILENAME to InputFileMetadata())
        ))

        whenever(orchestrationStatus.getNumStatementsForDoc(any())).thenReturn(3)

        val metadataMap = mapOf(
            FILENAME to InputFileMetadata(3, true, true),
            OTHER_FILENAME to InputFileMetadata(3, true, true),
        )
        whenever(orchestrationStatus.documentMetadataMap()).thenReturn(metadataMap)

        whenever(concurrentExecutionOrchestrator.execClassifyDocuments(any(), any(), any(), any(), any()))
            .thenReturn(mapOf(FILENAME to listOf(METADATA_1, METADATA_2), OTHER_FILENAME to listOf(METADATA_3, METADATA_4)))

        whenever(concurrentExecutionOrchestrator.execProcessDataModels(any(), any(), any(), any(), any(), any()))
            .thenReturn(mutableSetOf(
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_1),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_2),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_3),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_4),
            ))

        whenever(processStatementsTask.await()).thenReturn(
            ProcessStatementsActivityOutput(mapOf(
                FILENAME to setOf(STATEMENT_1, STATEMENT_2),
                OTHER_FILENAME to setOf(STATEMENT_3, STATEMENT_4),
            )))

        val result = dataExtractorOrchestratorFunction.pdfDataExtractorOrchestrator(mockContext)

        assertThat(result).isEqualTo(AnalyzeDocumentResult(AnalyzeDocumentResult.Status.SUCCESS, mapOf(
            FILENAME to setOf(STATEMENT_1, STATEMENT_2),
            OTHER_FILENAME to setOf(STATEMENT_3, STATEMENT_4)
        ), null))

        verify(mockContext).getInput(AzureAnalyzeDocumentsRequest::class.java)

        verify(mockContext, atLeastOnce()).isReplaying
        verify(mockContext, atLeastOnce()).instanceId

        verify(mockContext).callActivity(GetFilesToProcessActivity.FUNCTION_NAME, GetFilesToProcessActivityInput(REQUEST_ID, INPUT), GetFilesToProcessActivityOutput::class.java)

        verify(concurrentExecutionOrchestrator).execClassifyDocuments(
            eq(mockContext),
            argThat<List<String>> { arg -> arg.toSet() == setOf(FILENAME, OTHER_FILENAME) },
            eq(orchestrationStatus),
            eq(numWorkers),
            eq(CLIENT_NAME),
        )

        verify(concurrentExecutionOrchestrator).execProcessDataModels(
            eq(mockContext),
            argThat<List<ClassifiedPdfMetadata>> { arg -> arg.toSet() == setOf(METADATA_1, METADATA_2, METADATA_3, METADATA_4) },
            eq(orchestrationStatus),
            eq(numWorkers),
            eq(CLIENT_NAME),
            any()
        )

        verify(processStatementsTask).await()

        verify(mockContext).allOf(emptyList<Task<Void>>())

        verify(mockContext).callActivity(ProcessStatementsActivity.FUNCTION_NAME, ProcessStatementsActivityInput(REQUEST_ID, CLIENT_NAME,
            setOf(
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_1),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_2),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_3),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_4),
            ), metadataMap), ProcessStatementsActivityOutput::class.java)

        verify(orchestrationStatusFactory).new(mockContext, OrchestrationStage.VERIFYING_DOCUMENTS, setOf(FILENAME, OTHER_FILENAME))
        verify(orchestrationStatus).updateStage(OrchestrationStage.CLASSIFYING_DOCUMENTS)
        verify(orchestrationStatus).updateStage(OrchestrationStage.EXTRACTING_DATA)
        verify(orchestrationStatus).updateStage(OrchestrationStage.CREATING_BANK_STATEMENTS)
        verify(orchestrationStatus).updateDoc(eq(FILENAME), any())
        verify(orchestrationStatus).updateDoc(eq(OTHER_FILENAME), any())
        verify(orchestrationStatus).documentMetadataMap()
        verify(orchestrationStatus, times(4)).save()

        verifyNoMoreInteractions(mockContext, concurrentExecutionOrchestrator, orchestrationStatusFactory, orchestrationStatus)
    }

    @Test
    fun testDifferentMetadata() {
        val input = AzureAnalyzeDocumentsRequest(CLIENT_NAME, setOf(FILENAME, OTHER_FILENAME, FILENAME_PRECLASSIFIED, FILENAME_PREANALYZED, FILENAME_PRECOMPLETED))
        whenever(mockContext.getInput(AzureAnalyzeDocumentsRequest::class.java)).thenReturn(input)
        whenever(getFilesToProcessTask.await()).thenReturn(GetFilesToProcessActivityOutput(
            mapOf(
                FILENAME to InputFileMetadata(),
                OTHER_FILENAME to InputFileMetadata(),
                FILENAME_PRECLASSIFIED to InputFileMetadata(4, true),
                FILENAME_PREANALYZED to InputFileMetadata(5, true, true),
                FILENAME_PRECOMPLETED to InputFileMetadata(6, true, true, setOf(STATEMENT_5, STATEMENT_6)),
        )))

        whenever(orchestrationStatus.getNumStatementsForDoc(any())).thenReturn(3)

        val metadataMap = mapOf(
            FILENAME to InputFileMetadata(3, true, true),
            OTHER_FILENAME to InputFileMetadata(3, true, true),
            FILENAME_PRECLASSIFIED to InputFileMetadata(4, true, true),
            FILENAME_PREANALYZED to InputFileMetadata(5, true, true),
        )
        whenever(orchestrationStatus.documentMetadataMap()).thenReturn(metadataMap)

        whenever(concurrentExecutionOrchestrator.execClassifyDocuments(any(), any(), any(), any(), any()))
            .thenReturn(mapOf(
                FILENAME to listOf(METADATA_1, METADATA_2),
                OTHER_FILENAME to listOf(METADATA_3, METADATA_4),
            ))

        whenever(loadDocumentClassificationsTask.await())
            .thenReturn(LoadDocumentClassificationsActivityOutput(mapOf(FILENAME_PRECLASSIFIED to listOf(METADATA_PRECLASSIFIED))))

        whenever(concurrentExecutionOrchestrator.execProcessDataModels(any(), any(), any(), any(), any(), any()))
            .thenReturn(mutableSetOf(
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_1),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_2),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_3),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_4),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_PRECLASSIFIED),
            ))

        whenever(loadAnalyzedModelsTask.await()).thenReturn(
            LoadAnalyzedModelsActivityOutput(listOf(
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_PREANALYZED),
            ))
        )

        whenever(processStatementsTask.await()).thenReturn(
            ProcessStatementsActivityOutput(mapOf(
                FILENAME to setOf(STATEMENT_1, STATEMENT_2),
                OTHER_FILENAME to setOf(STATEMENT_3, STATEMENT_4),
                FILENAME_PRECLASSIFIED to setOf("PC"),
                FILENAME_PREANALYZED to setOf("PA"),
                FILENAME_PRECOMPLETED to setOf("PCOM"),
            )))

        val result = dataExtractorOrchestratorFunction.pdfDataExtractorOrchestrator(mockContext)

        assertThat(result).isEqualTo(AnalyzeDocumentResult(AnalyzeDocumentResult.Status.SUCCESS, mapOf(
            FILENAME to setOf(STATEMENT_1, STATEMENT_2),
            OTHER_FILENAME to setOf(STATEMENT_3, STATEMENT_4),
            FILENAME_PRECLASSIFIED to setOf("PC"),
            FILENAME_PREANALYZED to setOf("PA"),
            FILENAME_PRECOMPLETED to setOf(STATEMENT_5, STATEMENT_6),
        ), null))

        verify(mockContext).getInput(AzureAnalyzeDocumentsRequest::class.java)

        verify(mockContext, atLeastOnce()).isReplaying
        verify(mockContext, atLeastOnce()).instanceId

        verify(mockContext).callActivity(GetFilesToProcessActivity.FUNCTION_NAME, GetFilesToProcessActivityInput(REQUEST_ID, input), GetFilesToProcessActivityOutput::class.java)

        verify(concurrentExecutionOrchestrator).execClassifyDocuments(
            eq(mockContext),
            argThat<List<String>> { arg -> arg.toSet() == setOf(FILENAME, OTHER_FILENAME) },
            eq(orchestrationStatus),
            eq(numWorkers),
            eq(CLIENT_NAME),
        )

        verify(mockContext).callActivity(
            LoadDocumentClassificationsActivity.FUNCTION_NAME,
            LoadDocumentClassificationsActivityInput(REQUEST_ID, CLIENT_NAME, setOf(FILENAME_PRECLASSIFIED)),
            LoadDocumentClassificationsActivityOutput::class.java
        )

        verify(concurrentExecutionOrchestrator).execProcessDataModels(
            eq(mockContext),
            argThat<List<ClassifiedPdfMetadata>> { arg -> arg.toSet() == setOf(METADATA_1, METADATA_2, METADATA_3, METADATA_4, METADATA_PRECLASSIFIED) },
            eq(orchestrationStatus),
            eq(numWorkers),
            eq(CLIENT_NAME),
            any()
        )

        verify(mockContext).callActivity(
            LoadAnalyzedModelsActivity.FUNCTION_NAME,
            LoadAnalyzedModelsActivityInput(REQUEST_ID, CLIENT_NAME, setOf(FILENAME_PREANALYZED)),
            LoadAnalyzedModelsActivityOutput::class.java
        )

        verify(processStatementsTask).await()

        verify(mockContext).allOf(emptyList<Task<Void>>())

        verify(mockContext).callActivity(ProcessStatementsActivity.FUNCTION_NAME, ProcessStatementsActivityInput(REQUEST_ID, CLIENT_NAME,
            setOf(
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_1),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_2),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_3),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_4),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_PRECLASSIFIED),
                DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_PREANALYZED),
            ), metadataMap), ProcessStatementsActivityOutput::class.java)

        verify(orchestrationStatusFactory).new(mockContext, OrchestrationStage.VERIFYING_DOCUMENTS, setOf(FILENAME, OTHER_FILENAME, FILENAME_PRECLASSIFIED, FILENAME_PREANALYZED, FILENAME_PRECOMPLETED))
        verify(orchestrationStatus).updateStage(OrchestrationStage.CLASSIFYING_DOCUMENTS)
        verify(orchestrationStatus).updateStage(OrchestrationStage.EXTRACTING_DATA)
        verify(orchestrationStatus).updateStage(OrchestrationStage.CREATING_BANK_STATEMENTS)
        verify(orchestrationStatus).updateDoc(eq(FILENAME), any())
        verify(orchestrationStatus).updateDoc(eq(OTHER_FILENAME), any())
        verify(orchestrationStatus).updateDoc(eq(FILENAME_PRECLASSIFIED), any())
        verify(orchestrationStatus).updateDoc(eq(FILENAME_PREANALYZED), any())
        verify(orchestrationStatus).updateDoc(eq(FILENAME_PRECOMPLETED), any())
        verify(orchestrationStatus).documentMetadataMap()
        verify(orchestrationStatus, times(4)).save()

        verifyNoMoreInteractions(mockContext, concurrentExecutionOrchestrator, orchestrationStatusFactory, orchestrationStatus)
    }

    companion object {

        private val CHECK_DATA_MODELS = listOf(ModelValues.newCheckData(1000))

        private val INPUT = AzureAnalyzeDocumentsRequest(CLIENT_NAME, setOf(FILENAME, OTHER_FILENAME))

        const val FILENAME_PRECLASSIFIED = "preclassified"
        const val FILENAME_PREANALYZED = "preanalyzed"
        const val FILENAME_PRECOMPLETED = "precompleted"

        val METADATA_PRECLASSIFIED = newPdfMetadata(filename = FILENAME_PRECLASSIFIED, pages = setOf(5))
        val METADATA_PREANALYZED = newPdfMetadata(filename = FILENAME_PREANALYZED, pages = setOf(5))
        val METADATA_PRECOMPLETED = newPdfMetadata(filename = FILENAME_PRECOMPLETED, pages = setOf(5))

        val STATEMENT_MODEL_PRECLASSIFIED = newStatementModel(METADATA_PRECLASSIFIED)
        val STATEMENT_MODEL_PREANALYZED = newStatementModel(METADATA_PREANALYZED)
        val STATEMENT_MODEL_PRECOMPLETED = newStatementModel(METADATA_PRECOMPLETED)

        private const val STATEMENT_1 = "Statement1"
        private const val STATEMENT_2 = "Statement2"
        private const val STATEMENT_3 = "Statement3"
        private const val STATEMENT_4 = "Statement4"
        private const val STATEMENT_5 = "Statement5"
        private const val STATEMENT_6 = "Statement6"
    }
}