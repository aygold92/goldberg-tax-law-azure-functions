package com.goldberg.law.entity

import com.goldberg.law.datamanager.Extension
import com.goldberg.law.datamanager.StorageLocation
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.AbstractCollectionAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ObjectAssert
import java.math.BigDecimal
import java.util.UUID

object EntityValues {

    val CLIENT_ID = UUID.fromString("00000000-0000-0000-c11e-000000000001")
    val FILE_ID = UUID.fromString("00000000-0000-0000-f11e-000000000001")
    val FILE_ID_2 = UUID.fromString("00000000-0000-0000-f11e-000000000002")
    val FILE_ID_3 = UUID.fromString("00000000-0000-0000-f11e-000000000003")
    val CLASSFN_ID = UUID.fromString("00000000-0000-0000-c1a5-000000000001")
    val CLASSFN_ID_2 = UUID.fromString("00000000-0000-0000-c1a5-000000000002")
    val CLASSFN_ID_3 = UUID.fromString("00000000-0000-0000-c1a5-000000000003")
    val CLASSFN_ID_4 = UUID.fromString("00000000-0000-0000-c1a5-000000000004")
    val CLASSFN_ID_5 = UUID.fromString("00000000-0000-0000-c1a5-000000000005")
    val STMT_ID = UUID.fromString("00000000-0000-0000-57a7-000000000001")
    val STMT_ID_2 = UUID.fromString("00000000-0000-0000-57a7-000000000002")
    val STMT_ID_3 = UUID.fromString("00000000-0000-0000-57a7-000000000003")
    val STMT_ID_4 = UUID.fromString("00000000-0000-0000-57a7-000000000004")
    val STMT_ID_5 = UUID.fromString("00000000-0000-0000-57a7-000000000005")
    val TRANSACTION_ID = UUID.fromString("00000000-0000-0000-7ea5-000000000001")
    val CHECK_ID = UUID.fromString("00000000-0000-0000-c4ec-000000000001")
    val CHECK_ID_2 = UUID.fromString("00000000-0000-0000-c4ec-000000000002")
    val DEFAULT_FILE_CONTENT_HASH = UUID.fromString("00000000-0000-0000-f11e-00000000cafe")
    const val DEFAULT_CLIENT_NAME = "testClient"
    const val DEFAULT_FILENAME = "test.pdf"
    const val DEFAULT_ACCOUNT_NUMBER = "1234"
    const val DEFAULT_STATEMENT_DATE_STRING = "01/30/2024"
    val DEFAULT_STATEMENT_DATE = fromWrittenDate(DEFAULT_STATEMENT_DATE_STRING)
    const val DEFAULT_DATE = "1/15/2024"
    const val DEFAULT_DESCRIPTION = "test transaction"
    const val DEFAULT_CLASSIFICATION_TYPE = BankTypes.WF_BANK
    val DEFAULT_PAGES: Set<Int> = setOf(1)
    const val DEFAULT_FILE_PAGE = 1
    const val DEFAULT_NUM_PAGES = 1
    const val DEFAULT_CREATED_AT = 1700000000L
    const val DEFAULT_UPDATED_AT = 1700000000L
    const val DEFAULT_UPLOADED_AT = 1700000000L
    val DEFAULT_AMOUNT: BigDecimal = 500.asCurrency()
    val DEFAULT_BEGINNING_BALANCE: BigDecimal = 500.asCurrency()
    val DEFAULT_ENDING_BALANCE: BigDecimal = 1000.asCurrency()
    const val DEFAULT_BATES_STAMP = "AG-12345"
    val DEFAULT_BATES_STAMPS: Map<Int, String> = mapOf(1 to DEFAULT_BATES_STAMP)
    val DEFAULT_STORAGE_LOCATION = StorageLocation("test-container", "test/path", Extension.PDF)
    const val DEFAULT_CHECK_NUMBER = 1001
    const val DEFAULT_PAYEE = "John Doe"
    const val DEFAULT_MEMO = "test memo"

    val DEFAULT_STATEMENT = newStatement()
    val DEFAULT_TRANSACTION = DEFAULT_STATEMENT.transactions.first()
    val DEFAULT_FILE = DEFAULT_STATEMENT.classification.inputFile
    val DEFAULT_CLIENT = DEFAULT_STATEMENT.classification.inputFile.client
    val DEFAULT_CLASSIFICATION = DEFAULT_STATEMENT.classification
    val DEFAULT_CHECK = newCheck()

    fun newClient(
        clientId: UUID = CLIENT_ID,
        clientName: String = DEFAULT_CLIENT_NAME,
        createdAt: Long = DEFAULT_CREATED_AT,
    ) = Client(
        clientId = clientId,
        clientName = clientName,
        createdAt = createdAt,
    )

    fun newInputFileInfo(
        fileId: UUID = FILE_ID,
        fileName: String = DEFAULT_FILENAME,
        contentHash: UUID = DEFAULT_FILE_CONTENT_HASH,
        storageLocation: StorageLocation = DEFAULT_STORAGE_LOCATION,
        uploadedAt: Long = DEFAULT_UPLOADED_AT,
        numPages: Int = DEFAULT_NUM_PAGES,
    ) = InputFileInfo(
        fileId = fileId,
        fileName = fileName,
        contentHash = contentHash,
        storageLocation = storageLocation,
        uploadedAt = uploadedAt,
        numPages = numPages,
    )

    fun newInputFile(
        client: Client = newClient(),
        info: InputFileInfo = newInputFileInfo(),
    ) = InputFile(
        client = client,
        info = info,
    )

    fun newInputFile(fileId: UUID) = newInputFile(info = newInputFileInfo(fileId = fileId))

    fun newClassificationInfo(
        classificationId: UUID = CLASSFN_ID,
        pages: Set<Int> = DEFAULT_PAGES,
        classificationType: String = DEFAULT_CLASSIFICATION_TYPE,
        modelLocation: StorageLocation? = null,
        createdAt: Long = DEFAULT_CREATED_AT,
        updatedAt: Long = DEFAULT_UPDATED_AT,
    ) = ClassificationInfo(
        classificationId = classificationId,
        pages = pages,
        classificationType = classificationType,
        modelLocation = modelLocation,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun newClassification(
        inputFile: InputFile = newInputFile(),
        info: ClassificationInfo = newClassificationInfo(),
    ) = Classification(
        inputFile = inputFile,
        info = info,
    )

    fun newClassification(fileId: UUID = FILE_ID, classificationId: UUID = CLASSFN_ID, type: String) = newClassification(
        inputFile = newInputFile(info = newInputFileInfo(fileId = fileId)),
        info = newClassificationInfo(classificationId = classificationId, classificationType = type),
    )

    fun newCheckDetails(
        checkId: UUID = CHECK_ID,
        checkNumber: Int? = DEFAULT_CHECK_NUMBER,
        accountNumber: String? = DEFAULT_ACCOUNT_NUMBER,
        description: String? = DEFAULT_MEMO,
        date: String? = DEFAULT_DATE,
        amount: BigDecimal? = DEFAULT_AMOUNT,
        to: String? = DEFAULT_PAYEE,
        batesStamp: String? = DEFAULT_BATES_STAMP,
    ) = CheckDetails(
        checkId = checkId,
        checkNumber = checkNumber,
        accountNumber = accountNumber,
        description = description,
        date = date,
        amount = amount,
        to = to,
        batesStamp = batesStamp,
    )

    fun newCheck(
        classification: Classification = newClassification(),
        checkDetails: CheckDetails = newCheckDetails(),
    ) = Check(
        classification = classification,
        checkDetails = checkDetails,
    )

    fun newStatementDetails(
        statementId: UUID = STMT_ID,
        date: String? = DEFAULT_STATEMENT_DATE_STRING,
        accountNumber: String? = DEFAULT_ACCOUNT_NUMBER,
        beginningBalance: BigDecimal? = DEFAULT_BEGINNING_BALANCE,
        endingBalance: BigDecimal? = DEFAULT_ENDING_BALANCE,
        interestCharged: BigDecimal? = null,
        feesCharged: BigDecimal? = null,
        batesStamps: Map<Int, String> = DEFAULT_BATES_STAMPS,
    ) = StatementDetails(
        statementId = statementId,
        date = date,
        accountNumber = accountNumber,
        beginningBalance = beginningBalance,
        endingBalance = endingBalance,
        interestCharged = interestCharged,
        feesCharged = feesCharged,
        batesStamps = batesStamps,
    )

    fun newTransactionDetails(
        transactionId: UUID = TRANSACTION_ID,
        date: String? = DEFAULT_DATE,
        description: String? = DEFAULT_DESCRIPTION,
        amount: BigDecimal? = DEFAULT_AMOUNT,
        checkNumber: Int? = null,
        filePageNumber: Int = DEFAULT_FILE_PAGE,
        checkId: UUID? = null,
    ) = TransactionDetails(
        transactionId = transactionId,
        date = date,
        description = description,
        amount = amount,
        checkNumber = checkNumber,
        filePageNumber = filePageNumber,
        checkId = checkId,
    )

    fun newTransaction(
        statementId: UUID = STMT_ID,
        transactionDetails: TransactionDetails = newTransactionDetails(),
        checkDetails: CheckDetails? = null,
    ) = Transaction(
        statementId = statementId,
        transactionDetails = transactionDetails,
        checkDetails = checkDetails,
    )

    fun newStatement(
        classification: Classification = newClassification(),
        statementDetails: StatementDetails = newStatementDetails(),
        suspiciousReasons: List<String> = emptyList(),
        transactions: List<TransactionDetails> = listOf(newTransactionDetails()),
    ) = Statement(
        classification = classification,
        statementDetails = statementDetails,
        suspiciousReasons = suspiciousReasons,
        transactions = transactions,
    )

    fun newClassifiedStatement(
        classification: Classification = newClassification(),
        statementDetails: StatementDetails = newStatementDetails(),
    ) = ClassifiedStatement(
        classification = classification,
        statementDetails = statementDetails,
    )

    fun newClassifiedCheck(
        classification: Classification = newClassification(),
        checkDetails: CheckDetails = newCheckDetails(),
    ) = ClassifiedCheck(
        classification = classification,
        checkDetails = checkDetails,
    )

    fun newInputFileSummary(
        inputFile: InputFile = newInputFile(),
        numChecks: Int = 0,
        numStatements: Int = 0,
        numTransactions: Int = 0,
        numAnalyzed: Int = 0,
        numDocuments: Int = 0,
    ) = InputFileSummary(
        inputFile = inputFile,
        numChecks = numChecks,
        numStatements = numStatements,
        numTransactions = numTransactions,
        numAnalyzed = numAnalyzed,
        numDocuments = numDocuments,
    )

    fun newStatementSummary(
        classification: Classification = newClassification(),
        statementDetails: StatementDetails = newStatementDetails(),
        suspiciousReasons: List<String> = emptyList(),
        missingChecks: Set<String> = emptySet(),
        manuallyVerified: Boolean = false,
        totalSpending: BigDecimal = ZERO,
        totalIncomeCredits: BigDecimal = ZERO,
        numTransactions: Int = 0,
    ) = StatementSummary(
        classification = classification,
        statementDetails = statementDetails,
        suspiciousReasons = suspiciousReasons,
        missingChecks = missingChecks,
        manuallyVerified = manuallyVerified,
        totalSpending = totalSpending,
        totalIncomeCredits = totalIncomeCredits,
        numTransactions = numTransactions,
    )

    fun newClassifiedPages(
        pages: Set<Int> = DEFAULT_PAGES,
        classification: String = DEFAULT_CLASSIFICATION_TYPE,
    ) = ClassifiedPages(
        pages = pages,
        classification = classification,
    )

    fun newClassifiedFilePages(
        fileId: UUID = FILE_ID,
        pages: Set<Int> = DEFAULT_PAGES,
        classification: String = DEFAULT_CLASSIFICATION_TYPE,
    ) = ClassifiedFilePages(
        fileId = fileId,
        pages = pages,
        classification = classification,
    )

    fun newClassifiedFile(
        fileId: UUID = FILE_ID,
        classifications: List<ClassifiedPages> = listOf(newClassifiedPages()),
    ) = ClassifiedFile(
        fileId = fileId,
        classifications = classifications,
    )

    private val fieldsToIgnore = arrayOf(
        ".*clientId",
        ".*fileId",
        ".*classificationId",
        ".*statementId",
        ".*transactionId",
        ".*checkId",
        ".*uploadedAt",
        ".*createdAt",
        ".*updatedAt"
    )
    fun <T> ObjectAssert<T>.entityCompare() = usingRecursiveComparison()
        .ignoringFieldsMatchingRegexes(*fieldsToIgnore)
    fun <T> AbstractCollectionAssert<*, out Collection<T>, T, *>.entityCompare() =
        usingRecursiveComparison().ignoringFieldsMatchingRegexes(*fieldsToIgnore)


}