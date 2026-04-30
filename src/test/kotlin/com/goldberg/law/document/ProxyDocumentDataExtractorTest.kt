package com.goldberg.law.document

import com.goldberg.law.document.model.StatementModelValues.newClassifiedPdfDocument
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newClassificationInfo
import com.goldberg.law.entity.EntityValues.newInputFile
import com.goldberg.law.entity.EntityValues.newInputFileInfo
import com.goldberg.law.util.ZERO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ProxyDocumentDataExtractorTest {

    private val extractor = ProxyDocumentDataExtractor()

    /**
     * Builds a [ClassifiedPdfDocument] whose filename the proxy extractor can parse.
     *
     * [pageNum] is the logical page number within the full file — what
     * [com.goldberg.law.document.proxy.ProxyFilenameParser] assigns sequentially.
     * The underlying PDDocument contains a single blank page (the extractor never
     * reads PDF content, only the filename and page number stored in the classification).
     */
    private fun classifiedDoc(
        filename: String,
        pageNum: Int,
        type: String = DocumentType.BankTypes.WF_BANK,
    ) = newClassifiedPdfDocument(
        pages = setOf(pageNum),
        classification = newClassification(
            inputFile = newInputFile(info = newInputFileInfo(fileName = filename, numPages = 1)),
            info = newClassificationInfo(pages = setOf(pageNum), classificationType = type),
        )
    )

    /** Sum of all transaction amounts — positive for deposits, negative for withdrawals. */
    private fun StatementDataModel.txSum(): BigDecimal =
        getTransactionRecords().sumOf { it.amount ?: ZERO }

    // ── Account & date ─────────────────────────────────────────────────────────

    @Test
    fun `account number is taken from the filename spec`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt3.pdf", pageNum = 1))
        assertThat(model.accountNumber).isEqualTo("9088")
    }

    @Test
    fun `first statement date matches the spec date`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt3.pdf", pageNum = 1))
        assertThat(model.date).isEqualTo("8/1/2024")
    }

    @Test
    fun `second statement date is incremented by one month`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt3_t2.pdf", pageNum = 2))
        assertThat(model.date).isEqualTo("9/1/2024")
    }

    @Test
    fun `third statement date is incremented by two months`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt1_t1_t1.pdf", pageNum = 3))
        assertThat(model.date).isEqualTo("10/1/2024")
    }

    @Test
    fun `check page between statements does not advance the month counter`() {
        // page 1=stmt, page 2=check, page 3=stmt — page 3 is the *second* statement so 09
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt3_c100_t2.pdf", pageNum = 3))
        assertThat(model.date).isEqualTo("9/1/2024")
    }

    // ── Transaction counts ─────────────────────────────────────────────────────

    @Test
    fun `transaction count matches the spec`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt5.pdf", pageNum = 1))
        assertThat(model.getTransactionRecords()).hasSize(5)
    }

    @Test
    fun `zero-transaction statement produces no transactions`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt0.pdf", pageNum = 1))
        assertThat(model.getTransactionRecords()).isEmpty()
    }

    @Test
    fun `check numbers encoded in the spec appear as check transactions`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt3c100c101.pdf", pageNum = 1))
        val checkNums = model.getTransactionRecords().mapNotNull { it.checkNumber }.toSet()
        assertThat(checkNums).containsExactlyInAnyOrder(100, 101)
    }

    @Test
    fun `non-check transactions have descriptions and no check number`() {
        // t5c100 = 5 total: 4 non-check + 1 check
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt5c100.pdf", pageNum = 1))
        val nonCheckTxns = model.getTransactionRecords().filter { it.checkNumber == null }
        assertThat(nonCheckTxns).hasSize(4)
        assertThat(nonCheckTxns).allMatch { it.description != null }
    }

    @Test
    fun `check transactions have no description`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt3c100.pdf", pageNum = 1))
        val checkTxn = model.getTransactionRecords().first { it.checkNumber == 100 }
        assertThat(checkTxn.description).isNull()
    }

    // ── Balances ───────────────────────────────────────────────────────────────

    @Test
    fun `non-suspicious ending balance equals beginning plus transaction sum`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt5.pdf", pageNum = 1))
        assertThat(model.endingBalance).isEqualByComparingTo(model.beginningBalance!! + model.txSum())
    }

    @Test
    fun `zero-transaction ending balance equals beginning balance`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt0.pdf", pageNum = 1))
        assertThat(model.endingBalance).isEqualByComparingTo(model.beginningBalance)
    }

    @Test
    fun `suspicious ending balance differs from the real ending`() {
        val model = extractor.extractStatementData(classifiedDoc("testx9088x08-01-2024xt5s.pdf", pageNum = 1))
        val realEnding = model.beginningBalance!! + model.txSum()
        assertThat(model.endingBalance).isNotEqualByComparingTo(realEnding)
    }

    @Test
    fun `suspicious statement carries the real (not reported) ending to the next month`() {
        val filename = "testx9088x08-01-2024xt5s_t3.pdf"
        val stmt1 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 1))
        val stmt2 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 2))

        val realEnding1 = stmt1.beginningBalance!! + stmt1.txSum()

        // The reported ending of stmt1 is wrong (suspicious), but stmt2's beginning = real ending
        assertThat(stmt1.endingBalance).isNotEqualByComparingTo(realEnding1)
        assertThat(stmt2.beginningBalance).isEqualByComparingTo(realEnding1)
    }

    @Test
    fun `non-suspicious balance chain carries forward correctly across three statements`() {
        val filename = "testx9088x08-01-2024xt3_t2_t4.pdf"
        val stmt1 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 1))
        val stmt2 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 2))
        val stmt3 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 3))

        assertThat(stmt2.beginningBalance).isEqualByComparingTo(stmt1.endingBalance)
        assertThat(stmt3.beginningBalance).isEqualByComparingTo(stmt2.endingBalance)
    }

    // ── Check pages ────────────────────────────────────────────────────────────

    @Test
    fun `single check page produces a CheckDataModel with the correct check number`() {
        val model = extractor.extractCheckData(
            classifiedDoc("testx9088x08-01-2024xc100.pdf", pageNum = 1, type = DocumentType.CheckTypes.CHECKS)
        )
        assertThat(model.checkNumber).isEqualTo(100)
        assertThat(model.accountNumber).isEqualTo("9088")
        assertThat(model.amount).isNotNull
        assertThat(model.checkEntries).isNull()
    }

    @Test
    fun `multi-check page puts all checks in a CheckEntriesTable`() {
        val model = extractor.extractCheckData(
            classifiedDoc("testx9088x08-01-2024xc100c101.pdf", pageNum = 1, type = DocumentType.CheckTypes.CHECKS)
        )
        assertThat(model.checkEntries).isNotNull
        assertThat(model.checkEntries!!.images).hasSize(2)
        assertThat(model.checkEntries!!.images.map { it.checkNumber }).containsExactlyInAnyOrder(100, 101)
    }

    @Test
    fun `multi-check page entries carry the account number`() {
        val model = extractor.extractCheckData(
            classifiedDoc("testx9088x08-01-2024xc200c201.pdf", pageNum = 1, type = DocumentType.CheckTypes.CHECKS)
        )
        assertThat(model.checkEntries!!.images).allMatch { it.accountNumber == "9088" }
    }

    // ── Check amount consistency ───────────────────────────────────────────────

    @Test
    fun `check withdrawal in statement matches the check page amount for the same check number`() {
        val filename = "testx9088x08-01-2024xt3c100_c100.pdf"
        val stmtModel = extractor.extractStatementData(classifiedDoc(filename, pageNum = 1))
        val checkModel = extractor.extractCheckData(
            classifiedDoc(filename, pageNum = 2, type = DocumentType.CheckTypes.CHECKS)
        )

        val checkTxn = stmtModel.getTransactionRecords().first { it.checkNumber == 100 }
        // Statement stores as negative withdrawal; check page stores as positive amount
        assertThat(checkTxn.amount?.abs()).isEqualByComparingTo(checkModel.amount)
    }

    @Test
    fun `check amount is consistent across different filenames with the same check number`() {
        // The check amount depends only on the check number, not the filename or seed
        val amountA = extractor.extractCheckData(
            classifiedDoc("seedAx9088x08-01-2024xc100.pdf", pageNum = 1, type = DocumentType.CheckTypes.CHECKS)
        ).amount
        val amountB = extractor.extractCheckData(
            classifiedDoc("seedBx1234x01-15-2024xc100.pdf", pageNum = 1, type = DocumentType.CheckTypes.CHECKS)
        ).amount

        assertThat(amountA).isEqualByComparingTo(amountB)
    }

    // ── Multiple account groups ────────────────────────────────────────────────

    @Test
    fun `second account group has its own account number`() {
        val filename = "testx9088x08-01-2024xt3x1234x08-01-2024xt3.pdf"
        val stmt1 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 1))
        val stmt2 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 2))
        assertThat(stmt1.accountNumber).isEqualTo("9088")
        assertThat(stmt2.accountNumber).isEqualTo("1234")
    }

    @Test
    fun `second account group starts a fresh independent beginning balance`() {
        val filename = "testx9088x08-01-2024xt3x1234x08-01-2024xt3.pdf"
        val stmt1 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 1))
        val stmt2 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 2))

        // stmt2's beginning is NOT a carry-over from stmt1's ending; they are independent
        assertThat(stmt2.beginningBalance).isNotEqualByComparingTo(stmt1.endingBalance)
    }

    @Test
    fun `second account group date is independent from the first`() {
        val filename = "testx9088x08-01-2024xt1x1234x06-15-2024xt1.pdf"
        val stmt2 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 2))
        assertThat(stmt2.date).isEqualTo("6/15/2024")
    }

    // ── Determinism ────────────────────────────────────────────────────────────

    @Test
    fun `statement output is deterministic for the same filename and page`() {
        val filename = "testx9088x08-01-2024xt5c100s_c100_t0_t4c101c102.pdf"
        val model1 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 1))
        val model2 = extractor.extractStatementData(classifiedDoc(filename, pageNum = 1))

        assertThat(model1.beginningBalance).isEqualByComparingTo(model2.beginningBalance)
        assertThat(model1.endingBalance).isEqualByComparingTo(model2.endingBalance)
        assertThat(model1.getTransactionRecords().size).isEqualTo(model2.getTransactionRecords().size)
    }

    @Test
    fun `check page output is deterministic for the same check number`() {
        val filename = "testx9088x08-01-2024xc100.pdf"
        val model1 = extractor.extractCheckData(classifiedDoc(filename, pageNum = 1, type = DocumentType.CheckTypes.CHECKS))
        val model2 = extractor.extractCheckData(classifiedDoc(filename, pageNum = 1, type = DocumentType.CheckTypes.CHECKS))

        assertThat(model1.amount).isEqualByComparingTo(model2.amount)
        assertThat(model1.checkNumber).isEqualTo(model2.checkNumber)
    }

    @Test
    fun `different seeds produce different beginning balances`() {
        val balanceA = extractor.extractStatementData(classifiedDoc("seedAx9088x08-01-2024xt3.pdf", pageNum = 1)).beginningBalance
        val balanceB = extractor.extractStatementData(classifiedDoc("seedBx9088x08-01-2024xt3.pdf", pageNum = 1)).beginningBalance
        assertThat(balanceA).isNotEqualByComparingTo(balanceB)
    }
}
