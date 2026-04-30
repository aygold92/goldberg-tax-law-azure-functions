package com.goldberg.law.document

import com.goldberg.law.document.model.StatementModelValues.newPdfDocument
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.ATLANTIC_UNION
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.B_OF_A
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.EAGLE_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.M_T_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.NFCU_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.SANDY_SPRING
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.TFCU_BANK
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.TRUIST
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.CHECKS
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.ALLY_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.AMEX_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.B_OF_A_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.C1_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.CITI_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.NFCU_CC
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.WF_CC
import com.goldberg.law.entity.EntityValues.newInputFile
import com.goldberg.law.entity.EntityValues.newInputFileInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProxyDocumentClassifierTest {

    private val classifier = ProxyDocumentClassifier()

    private val allBankTypes = listOf(WF_BANK, EAGLE_BANK, B_OF_A, NFCU_BANK, TRUIST, SANDY_SPRING, ATLANTIC_UNION, M_T_BANK, TFCU_BANK)
    private val allCcTypes   = listOf(AMEX_CC, C1_CC, CITI_CC, WF_CC, B_OF_A_CC, NFCU_CC, ALLY_CC)

    /** Create a minimal one-page PdfDocument with the given filename. */
    private fun pdfDoc(filename: String) = newPdfDocument(
        pages = setOf(1),
        file = newInputFile(info = newInputFileInfo(fileName = filename, numPages = 1))
    )

    // ── Type selection ─────────────────────────────────────────────────────────

    @Test
    fun `bank statement gets a valid bank type`() {
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xt3.pdf"))
        assertThat(result.classifications).hasSize(1)
        assertThat(allBankTypes).contains(result.classifications[0].classification)
    }

    @Test
    fun `credit card statement gets a valid CC type`() {
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xT3.pdf"))
        assertThat(result.classifications).hasSize(1)
        assertThat(allCcTypes).contains(result.classifications[0].classification)
    }

    @Test
    fun `check page always gets CHECKS type`() {
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xc100.pdf"))
        assertThat(result.classifications).hasSize(1)
        assertThat(result.classifications[0].classification).isEqualTo(CHECKS)
    }

    @Test
    fun `all bank statements in a file use the same bank type`() {
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xt1_t2_t3.pdf"))
        val types = result.classifications.map { it.classification }
        assertThat(types).hasSize(3)
        assertThat(types).allMatch { it == types[0] }
        assertThat(allBankTypes).contains(types[0])
    }

    @Test
    fun `all CC statements in a file use the same CC type`() {
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xT1_T2_T3.pdf"))
        val types = result.classifications.map { it.classification }
        assertThat(types).hasSize(3)
        assertThat(types).allMatch { it == types[0] }
        assertThat(allCcTypes).contains(types[0])
    }

    @Test
    fun `bank type and CC type are chosen independently`() {
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xt1_T1.pdf"))
        val bankType = result.classifications[0].classification
        val ccType = result.classifications[1].classification
        assertThat(allBankTypes).contains(bankType)
        assertThat(allCcTypes).contains(ccType)
    }

    // ── Page numbering ─────────────────────────────────────────────────────────

    @Test
    fun `pages are numbered sequentially starting at 1`() {
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xt1_c100_t2.pdf"))
        assertThat(result.classifications.map { it.pagesOrdered }).isEqualTo(listOf(
            listOf(1), listOf(2), listOf(3)
        ))
    }

    @Test
    fun `check pages are included in sequential page numbering`() {
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xc100c101_t2_c200.pdf"))
        assertThat(result.classifications).hasSize(3)
        assertThat(result.classifications[0].pagesOrdered).isEqualTo(listOf(1))
        assertThat(result.classifications[1].pagesOrdered).isEqualTo(listOf(2))
        assertThat(result.classifications[2].pagesOrdered).isEqualTo(listOf(3))
    }

    // ── Multiple account groups ────────────────────────────────────────────────

    @Test
    fun `multiple account groups are concatenated in order`() {
        // 2 items in first group + 1 in second = 3 total
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xt2_c100x1234x09-01-2024xt1.pdf"))
        assertThat(result.classifications).hasSize(3)
        assertThat(result.classifications.map { it.pagesOrdered }).isEqualTo(listOf(
            listOf(1), listOf(2), listOf(3)
        ))
        assertThat(result.classifications[1].classification).isEqualTo(CHECKS) // c100
    }

    // ── Determinism & identity ─────────────────────────────────────────────────

    @Test
    fun `output is deterministic for the same filename`() {
        val filename = "testx9088x08-01-2024xt5c100s_c100_t0_t4c101c102.pdf"
        val result1 = classifier.classifyDocument(pdfDoc(filename))
        val result2 = classifier.classifyDocument(pdfDoc(filename))
        assertThat(result1.classifications).isEqualTo(result2.classifications)
    }

    @Test
    fun `fileId of the result matches the input document`() {
        val doc = pdfDoc("testx9088x08-01-2024xt1.pdf")
        val result = classifier.classifyDocument(doc)
        assertThat(result.fileId).isEqualTo(doc.fileId)
    }

    @Test
    fun `different seeds produce valid types for both`() {
        val typeA = classifier.classifyDocument(pdfDoc("seedAx9088x08-01-2024xt1.pdf")).classifications[0].classification
        val typeB = classifier.classifyDocument(pdfDoc("seedBx9088x08-01-2024xt1.pdf")).classifications[0].classification
        assertThat(allBankTypes).contains(typeA)
        assertThat(allBankTypes).contains(typeB)
    }

    // ── Full example ───────────────────────────────────────────────────────────

    @Test
    fun `full example filename produces 4 classifications in the right order`() {
        // testx9088x08-01-2024x t5c100s _ c100 _ t0 _ t4c101c102
        //                        stmt(1)   chk   stmt  stmt
        val result = classifier.classifyDocument(pdfDoc("testx9088x08-01-2024xt5c100s_c100_t0_t4c101c102.pdf"))

        assertThat(result.classifications).hasSize(4)
        val types = result.classifications.map { it.classification }

        // Statement pages are some consistent bank type
        assertThat(allBankTypes).contains(types[0])
        assertThat(types[0]).isEqualTo(types[2]).isEqualTo(types[3])

        // Check page is CHECKS
        assertThat(types[1]).isEqualTo(CHECKS)

        // Pages 1-4 in order
        assertThat(result.classifications.map { it.pagesOrdered }).isEqualTo(listOf(
            listOf(1), listOf(2), listOf(3), listOf(4)
        ))
    }
}
