package com.goldberg.law

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder
import com.azure.core.credential.AzureKeyCredential
import com.goldberg.law.document.DocumentClassifier
import com.goldberg.law.document.DocumentDataExtractor
import com.goldberg.law.document.AccountSummaryCreator
import com.goldberg.law.document.CheckToStatementMatcher
import com.goldberg.law.document.writer.AzureStorageCsvWriter
import com.goldberg.law.document.writer.CsvWriter
import com.goldberg.law.document.writer.FileCsvWriter
import com.goldberg.law.pdf.PdfSplitter
import com.goldberg.law.pdf.loader.AzureStoragePdfLoader
import com.goldberg.law.pdf.loader.FilePdfLoader
import com.goldberg.law.pdf.loader.PdfLoader
import com.goldberg.law.pdf.writer.AzureStoragePdfWriter
import com.goldberg.law.pdf.writer.FilePdfWriter
import com.goldberg.law.pdf.writer.PdfWriter
import com.google.inject.AbstractModule
import com.google.inject.Provides
import javax.inject.Singleton

class AppModule constructor(private val appEnvironmentSettings: AppEnvironmentSettings) : AbstractModule() {
    override fun configure() {

    }

//    @Provides
//    @Singleton
//    @Named("ClassifierModelId")
//    fun provideClassifierModelId(): String = appEnvironmentSettings.intelligenceService.classifierModel

//    @Provides
//    @Singleton
//    @Named("CustomDataModelId")
//    fun provideCustomDataModelId(): String = appEnvironmentSettings.intelligenceService.dataExtractorModel

    @Provides
    @Singleton
    fun documentAnalysisClient(): DocumentAnalysisClient = DocumentAnalysisClientBuilder()
        .endpoint(appEnvironmentSettings.intelligenceService.apiEndpoint)
        .credential(AzureKeyCredential(appEnvironmentSettings.intelligenceService.apiKey))
        .buildClient()

    @Provides
    @Singleton
    fun pdfWriter(): PdfWriter = if (appEnvironmentSettings.executionEnvironment == ExecutionEnvironment.LOCAL) FilePdfWriter() else AzureStoragePdfWriter()

    @Provides
    @Singleton
    fun pdfLoader(): PdfLoader = if (appEnvironmentSettings.executionEnvironment == ExecutionEnvironment.LOCAL) FilePdfLoader() else AzureStoragePdfLoader()

    @Provides
    @Singleton
    fun CsvWriter(): CsvWriter = if (appEnvironmentSettings.executionEnvironment == ExecutionEnvironment.LOCAL) FileCsvWriter() else AzureStorageCsvWriter()

    /** these should not have to instantiated here, I have no idea why guice can't find the @Inject constructor **/
    @Provides
    @Singleton
    fun documentClassifier(documentAnalysisClient: DocumentAnalysisClient): DocumentClassifier = DocumentClassifier(documentAnalysisClient, appEnvironmentSettings.intelligenceService.classifierModel)

    @Provides
    @Singleton
    fun documentDataExtractor(documentAnalysisClient: DocumentAnalysisClient): DocumentDataExtractor = DocumentDataExtractor(documentAnalysisClient, appEnvironmentSettings.intelligenceService.dataExtractorModel, appEnvironmentSettings.intelligenceService.checkExtractorModel)

    @Provides
    @Singleton
    fun pdfExtractorMain(pdfLoader: PdfLoader,
                         splitter: PdfSplitter,
                         classifier: DocumentClassifier,
                         dataExtractor: DocumentDataExtractor,
                         csvWriter: CsvWriter,
                         accountSummaryCreator: AccountSummaryCreator,
                         checkToStatementMatcher: CheckToStatementMatcher
    ) = PdfExtractorMain(pdfLoader, splitter, classifier, dataExtractor, csvWriter, checkToStatementMatcher, accountSummaryCreator)
}