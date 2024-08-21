package com.goldberg.law.document

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzeResult
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.azure.ai.formrecognizer.documentanalysis.models.OperationResult
import com.azure.core.util.polling.PollResponse
import com.azure.core.util.polling.SyncPoller
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.TransactionHistoryPage
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.pdf.model.ClassifiedPdfDocument
import com.goldberg.law.pdf.model.StatementType
import com.goldberg.law.pdf.model.StatementType.BankTypes
import com.goldberg.law.pdf.model.StatementType.CreditCardTypes
import com.goldberg.law.util.fromWrittenDate
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.nio.file.Files
import java.nio.file.Paths

class DocumentDataExtractorTest {
    @Mock
    val client: DocumentAnalysisClient = mock()
    @Mock
    val poller: SyncPoller<OperationResult, AnalyzeResult> = mock()
    @Mock
    val operationResult: PollResponse<OperationResult> = mock()
    @Mock
    val analyzeResult: AnalyzeResult = mock()
    @Mock
    val analyzedDocument: AnalyzedDocument = mock()

    val documentDataExtractor = DocumentDataExtractor(client, MODEL_ID)

    @BeforeEach
    fun before() {
        whenever(client.beginAnalyzeDocument(any(), any())).thenReturn(poller)
        whenever(poller.waitForCompletion()).thenReturn(operationResult)
        whenever(poller.finalResult).thenReturn(analyzeResult)
    }

    @Test
    fun testFullIndividualStatementBank() {
        // Processing statement 2/7/2022 - 1010086913443}, page 1
        val page7 = readFileRelative("WFBank[0].json")
        // Page 8 Processing statement 2/7/2022 - 1010086913443}, page 2
        val page8 = readFileRelative("WFBank[1].json")
        // Page 18 Processing statement 4/7/2022 - 1010086913443}, page 2
        val page18 = readFileRelative("WFBank[2].json")
        // Page 19 Processing statement 4/7/2022 - 1010086913443}, page 3
        val page19 = readFileRelative("WFBank[3].json")
        // Page 21 Processing statement 4/7/2022 - 9859884372}, page 5 TODO: fix the broken record
        val page21 = readFileRelative("WFBank[4].json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page7, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page8, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page18, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page19, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page21, AnalyzedDocument::class.java))
        )
        val result = documentDataExtractor.extractTransactionHistory(generateClassifiedPdfDocuments(5, BankTypes.WF_BANK)).toList()

        val firstStatementDate = fromWrittenDate("Feb 7, 2022")

        assertThat(result.size).isEqualTo(3)
        assertThat(result[0]).isEqualTo(
            BankStatement(
                filename = FILENAME,
                classification = BankTypes.WF_BANK,
                statementDate = firstStatementDate,
                statementType = StatementType.BANK,
                accountNumber = "1010086913443",
                startPage = 1,
                totalPages = 4,
                beginningBalance = 10124.23,
                endingBalance = 6390.78,
                thPages = mutableListOf(
                    TransactionHistoryPage(
                        filename = FILENAME,
                        filePageNumber = 1,
                        batesStamp = "MH-000403",
                        statementDate = firstStatementDate,
                        statementPageNum = 1,
                    ),
                    TransactionHistoryPage(
                        filename = FILENAME,
                        filePageNumber = 2,
                        batesStamp = "MH-000404",
                        statementDate = firstStatementDate,
                        statementPageNum = 2,
                        transactionHistoryRecords = listOf(
                            TransactionHistoryRecord(checkNumber = 12058, date = fromWrittenDate("1/13/2022"), description = "Check", amount = -928.0),
                            TransactionHistoryRecord(checkNumber = null, date = fromWrittenDate("1/14/2022"), description = "S C Herman & Ass Ppe220109 Herm01 Robert Herman", amount = 183.38),
                            TransactionHistoryRecord(checkNumber = null, date = fromWrittenDate("1/14/2022"), description = "WT Fed#06710 Morgan Stanley and /Org=Robert B. Herman Sylvan C Herman Srf# D1020140041101 Trn#220114020140 Rfb#", amount = 20000.0),
                            TransactionHistoryRecord(checkNumber = null, date = fromWrittenDate("1/14/2022"), description = "Wire Trans Svc Charge - Sequence: 220114020140 Srf# D1020140041101 Trn#220114020140 Rfb#", amount = -15.0),
                            TransactionHistoryRecord(checkNumber = 12055, date = fromWrittenDate("1/18/2022"), description = "Check", amount = -100.0),
                            TransactionHistoryRecord(checkNumber = 12054, date = fromWrittenDate("1/18/2022"), description = "Check", amount = -100.0),
                            TransactionHistoryRecord(checkNumber = null, date = fromWrittenDate("1/20/2022"), description = "Comp of Maryland Dir Db Rad 012022 004822018026253 x", amount = -1173.34),
                            TransactionHistoryRecord(checkNumber = 12050, date = fromWrittenDate("1/21/2022"), description = "Check", amount = -400.0),
                            TransactionHistoryRecord(checkNumber = 12066, date = fromWrittenDate("1/21/2022"), description = "Check", amount = -930.1),
                            TransactionHistoryRecord(checkNumber = null, date = fromWrittenDate("1/24/2022"), description = "ATM Withdrawal authorized on 01/24 5701 Connecticut Ave NW Washington DC 0004269 ATM ID 0085P Card 9899", amount = -200.0),
                            TransactionHistoryRecord(checkNumber = 12061, date = fromWrittenDate("1/24/2022"), description = "Check", amount = -303.64),
                            TransactionHistoryRecord(checkNumber = 12063, date = fromWrittenDate("1/25/2022"), description = "Check", amount = -10884.93),
                            TransactionHistoryRecord(checkNumber = 12064, date = fromWrittenDate("1/25/2022"), description = "Check", amount = -1253.67),
                            TransactionHistoryRecord(checkNumber = 12062, date = fromWrittenDate("1/25/2022"), description = "Check", amount = -5607.95),
                            TransactionHistoryRecord(checkNumber = null, date = fromWrittenDate("1/28/2022"), description = "S C Herman & Ass Ppe220123 Herm01 Robert Herman", amount = 183.4),
                            TransactionHistoryRecord(checkNumber = 12060, date = fromWrittenDate("1/28/2022"), description = "Check", amount = -343.53),
                            TransactionHistoryRecord(checkNumber = 12067, date = fromWrittenDate("1/28/2022"), description = "Check", amount = -930.1),
                            TransactionHistoryRecord(checkNumber = 12070, date = fromWrittenDate("2/7/2022"), description = "Check", amount = -930.1),
                            TransactionHistoryRecord(checkNumber = null, date = fromWrittenDate("2/7/2022"), description = "Interest Payment", amount = 0.13)

                        )
                    )
                ),
            )
        )

        val secondStatementDate = fromWrittenDate("Apr 7, 2022")

        assertThat(result[1]).isEqualTo(
            BankStatement(
                filename = FILENAME,
                classification = BankTypes.WF_BANK,
                statementDate = secondStatementDate,
                accountNumber = "1010086913443",
                statementType = StatementType.BANK,
                startPage = 2,
                totalPages = 6,
                beginningBalance = 20698.0,
                endingBalance = 11789.10,
                thPages = mutableListOf(
                    TransactionHistoryPage(
                        filename = FILENAME,
                        filePageNumber = 3,
                        batesStamp = "MH-000414",
                        statementDate = secondStatementDate,
                        statementPageNum = 2,
                        transactionHistoryRecords = listOf(
                            TransactionHistoryRecord(description = "Check", checkNumber = 12087, date = fromWrittenDate("3/9", "2022"), amount = -930.0),
                            TransactionHistoryRecord(description = "Check", checkNumber = 12086, date = fromWrittenDate("3/9", "2022"), amount = -1457.0),
                            TransactionHistoryRecord(description = "Check", checkNumber = 12078, date = fromWrittenDate("3/10", "2022"), amount = -400.0),
                            TransactionHistoryRecord(description = "S C Herman & Ass Ppe220306 Herm01 Robert Herman", checkNumber = null, date = fromWrittenDate("3/11", "2022"), amount = 183.38),
                            TransactionHistoryRecord(description = "WT Fed#05582 Morgan Stanley and /Org=Robert B. Herman Sylvan C Herman CO Srf# S06207715Ea601 Trn#220318022868 Rfb#", checkNumber = null, date = fromWrittenDate("3/18", "2022"), amount = 20000.0),
                            TransactionHistoryRecord(description = "Wire Trans Svc Charge - Sequence: 220318022868 Srf# S06207715Ea601 Trn#220318022868 Rfb#", checkNumber = null, date = fromWrittenDate("3/18", "2022"), amount = -15.0),
                            TransactionHistoryRecord(description = "Check", checkNumber = 12088, date = fromWrittenDate("3/21", "2022"), amount = -930.0),
                            TransactionHistoryRecord(description = "Check", checkNumber = 12092, date = fromWrittenDate("3/22", "2022"), amount = -16275.43),
                            TransactionHistoryRecord(description = "Check", checkNumber = 12091, date = fromWrittenDate("3/22", "2022"), amount = -5414.72),
                            TransactionHistoryRecord(description = "Check", checkNumber = 12089, date = fromWrittenDate("3/24", "2022"), amount = -336.62)
                        )
                    ),
                    TransactionHistoryPage(
                        filename = FILENAME,
                        filePageNumber = 4,
                        batesStamp = "MH-000415",
                        statementDate = secondStatementDate,
                        statementPageNum = 3,
                        transactionHistoryRecords = listOf(
                            TransactionHistoryRecord(description = "Check",checkNumber = 12090, date = fromWrittenDate("3/24","2022"), amount = -166.86),
                            TransactionHistoryRecord(description = "S C Herman & Ass Ppe220322 Herm01 Robert Herman",checkNumber = null, date = fromWrittenDate("3/25","2022"), amount = 183.4),
                            TransactionHistoryRecord(description = "ATM Withdrawal authorized on 03/25 3700 Calvert St NW Washington DC 0002802 ATM ID 0217F Card 9899",checkNumber = null, date = fromWrittenDate("3/25","2022"), amount = -100.0),
                            TransactionHistoryRecord(description = "Check",checkNumber = 12095, date = fromWrittenDate("3/28","2022"), amount = -435.0),
                            TransactionHistoryRecord(description = "Check",checkNumber = 12094, date = fromWrittenDate("3/28","2022"), amount = -435.0),
                            TransactionHistoryRecord(description = "Check",checkNumber = 12097, date = fromWrittenDate("3/30","2022"), amount = -930.1),
                            TransactionHistoryRecord(description = "ATM Withdrawal authorized on 04/01 3700 Calvert St NW Washington DC 0003127 ATM ID 0217F Card 9899",checkNumber = null, date = fromWrittenDate("4/1/","022"), amount = -100.0),
                            TransactionHistoryRecord(description = "ATM Withdrawal authorized on 04/04 2800 University Blvd W St Wheaton MD 0005362 ATM ID 02840 Card 9899",checkNumber = null, date = fromWrittenDate("4/4/","022"), amount = -20.0),
                            TransactionHistoryRecord(description = "Check",checkNumber = 12099, date = fromWrittenDate("4/4/","2022"), amount = -930.1),
                            TransactionHistoryRecord(description = "Check",checkNumber = 12096, date = fromWrittenDate("4/4/","2022"), amount = -400.0),
                            TransactionHistoryRecord(description = "Interest Payment",checkNumber = null, date = fromWrittenDate("4/7/","2022"), amount = 0.15)
                        )
                    )
                ),
            )
        )

        assertThat(result[2]).isEqualTo(
            BankStatement(
                filename = FILENAME,
                classification = BankTypes.WF_BANK,
                statementDate = secondStatementDate,
                accountNumber = "9859884372",
                statementType = StatementType.BANK,
                startPage = 5,
                totalPages = 6,
                beginningBalance = 201684.13,
                endingBalance = 201684.52,
                thPages = mutableListOf(
                    TransactionHistoryPage(
                        filename = FILENAME,
                        filePageNumber = 5,
                        batesStamp = "MH-000417",
                        statementDate = secondStatementDate,
                        statementPageNum = 5,
                        transactionHistoryRecords = listOf(
                            TransactionHistoryRecord(date = fromWrittenDate("4/7", "2022"), checkNumber = null, description = "0.39", amount = -201684.52)
                        )
                    )
                ),
            )
        )
    }

    @Test
    fun testFullStatementDoubleCash() {
        val page1 = readFileRelative("DoubleCash[0].json")
        val page2 = readFileRelative("DoubleCash[1].json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page2, AnalyzedDocument::class.java))
        )
        val result = documentDataExtractor.extractTransactionHistory(generateClassifiedPdfDocuments(2, CreditCardTypes.CITI_DOUBLE_CASH)).toList()

        val statement = result[0]
        assertThat(statement.statementDate).isEqualTo(fromWrittenDate("11/02/21"))
        assertThat(statement.accountNumber).isEqualTo("1358")

        assertThat(statement.getSuspiciousReasons())
            .hasSize(4)
            .contains(BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(1881.4, -6195.49, 1649.41))

    }

    @Test
    fun testFullStatementC1SignatureJoint() {
        val page1 = readFileRelative("C1Venture[0].json")
        val page2 = readFileRelative("C1Venture[1].json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page2, AnalyzedDocument::class.java))
        )
        val result = documentDataExtractor.extractTransactionHistory(generateClassifiedPdfDocuments(2, CreditCardTypes.C1_SIGNATURE)).toList()
        val statement = result[0]
        assertThat(statement.statementDate).isEqualTo(fromWrittenDate("Jun 09, 2021"))
        assertThat(statement.accountNumber).isEqualTo("5695")
        assertThat(statement.getNetTransactions()).isEqualTo(-59.0)
        assertThat(statement.getSuspiciousReasons()).isEmpty()
    }

    fun readFileRelative(filename: String): String =
        Files.readString(Paths.get(javaClass.getResource("$PATH_TO_CLASS/extraction/$filename")!!.toURI()))

    @Test
    fun testFullStatementEagleBank() {
        val page1 = readFileRelative("EagleBank[0].json")
        val page2 = readFileRelative("EagleBank[1].json")
        val page3 = readFileRelative("EagleBank[2].json")
        val page4 = readFileRelative("EagleBank[3].json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page2, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page3, AnalyzedDocument::class.java)),
            listOf(OBJECT_MAPPER.readValue(page4, AnalyzedDocument::class.java)),
        )
        val result = documentDataExtractor.extractTransactionHistory(generateClassifiedPdfDocuments(4, BankTypes.EAGLE_BANK)).toList()

        assertThat(result).hasSize(2)

        val statement = result[0]
        assertThat(statement.statementDate).isEqualTo(fromWrittenDate("May 15, 2019"))
        assertThat(statement.accountNumber).isEqualTo("0100002492")
        assertThat(statement.getNetTransactions()).isEqualTo(330.94)
        assertThat(statement.getSuspiciousReasons()).isEmpty()

        val statement2 = result[1]
        assertThat(statement2.statementDate).isEqualTo(fromWrittenDate("June 15, 2019"))
        assertThat(statement2.accountNumber).isEqualTo("0100002492")
        assertThat(statement2.getNetTransactions()).isEqualTo(-867.1)
        assertThat(statement2.getSuspiciousReasons()).hasSize(3)  // one of the records is missing a date
    }

    companion object {
        val PATH_TO_CLASS = "/" + DocumentDataExtractor::class.java.packageName.replace('.', '/')
        fun generateClassifiedPdfDocuments(numberOfDocs: Int, bankName: String): List<ClassifiedPdfDocument> = IntRange(1, numberOfDocs).map {
            ClassifiedPdfDocument(FILENAME, PDDocument().apply { addPage(PDPage()) }, listOf(it), bankName)
        }

        private val FILENAME = "filename"

        val OBJECT_MAPPER = ObjectMapper()
        private val MODEL_ID = "TestModel"
    }

    fun newDocument(fieldMap: Map<String, Any?>): AnalyzedDocument {
        val documentFieldMap = fieldMap.map { it.key to newField(it.value) }.toMap()
        return OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(mapOf("fields" to documentFieldMap)), AnalyzedDocument::class.java)
    }

    fun newField(value: Any?, type: DocumentFieldType? = null, content: String? = null): DocumentField {
        val contentString = if (content != null) "\"$content\"" else if (value is Map<*,*> || value is Collection<*> || value == null) "null" else  "\"$value\""
        val typeString = type ?: DocumentFieldType.fromValue(value)
        val valueString = when(value) {
            null -> "null"
            is Double, Int -> value.toString()
            is Map<*,*> -> OBJECT_MAPPER.writeValueAsString(newFieldMap((value as Map<String, Any>).entries))
            is Collection<*> -> OBJECT_MAPPER.writeValueAsString(newFieldList(value as Collection<Any>))
            else -> "\"$value\""
        }

        return "{\"value\":$valueString,\"type\":\"$typeString\",\"content\":$contentString}".let {
            OBJECT_MAPPER.readValue(it, DocumentField::class.java)
        }
    }

    fun newFieldMap(fields: Set<Map.Entry<String, Any?>>): Map<String, DocumentField> =
        fields.map { it.key to newField(it.value) }.toMap()

    fun newFieldList(fieldValues: Collection<Any>): Collection<DocumentField> = fieldValues.map { newField(it) }.toList()

    enum class DocumentFieldType(private val value: String) {
        STRING("string"), NUMBER("number"), ARRAY("array"), OBJECT("object"), INTEGER("integer");

        override fun toString(): String {
            return value
        }

        companion object {
            fun fromValue(value: Any?) = when (value) {
                is Double -> NUMBER
                is Int -> INTEGER
                is Map<*, *> -> OBJECT
                is Collection<*> -> ARRAY
                else -> STRING
            }
        }
    }
}