package com.goldberg.law.document

import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.PdfDocument
import com.goldberg.law.document.proxy.ProxyClassificationSpec
import com.goldberg.law.document.proxy.ProxyFilenameParser
import com.goldberg.law.entity.ClassifiedFile
import com.goldberg.law.entity.ClassifiedPages
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

/**
 * A local-only stand-in for [AzureDocumentClassifier] that derives classifications
 * entirely from the filename, requiring no Azure credentials.
 *
 * Enable via env var: UseProxyDocumentIntelligence=true
 *
 * See [com.goldberg.law.document.proxy.ProxyFilenameParser] for filename format.
 */
class ProxyDocumentClassifier : DocumentClassifier() {
    private val logger = KotlinLogging.logger {}

    private val allBankTypes = listOf(
        DocumentType.BankTypes.WF_BANK,
        DocumentType.BankTypes.EAGLE_BANK,
        DocumentType.BankTypes.B_OF_A,
        DocumentType.BankTypes.NFCU_BANK,
        DocumentType.BankTypes.TRUIST,
        DocumentType.BankTypes.SANDY_SPRING,
        DocumentType.BankTypes.ATLANTIC_UNION,
        DocumentType.BankTypes.M_T_BANK,
        DocumentType.BankTypes.TFCU_BANK,
    )

    private val allCreditCardTypes = listOf(
        DocumentType.CreditCardTypes.AMEX_CC,
        DocumentType.CreditCardTypes.C1_CC,
        DocumentType.CreditCardTypes.CITI_CC,
        DocumentType.CreditCardTypes.WF_CC,
        DocumentType.CreditCardTypes.B_OF_A_CC,
        DocumentType.CreditCardTypes.NFCU_CC,
        DocumentType.CreditCardTypes.ALLY_CC,
    )

    override fun classifyDocument(document: PdfDocument): ClassifiedFile {
        val spec = ProxyFilenameParser.parse(document.fileName)
        val random = Random(spec.seed.hashCode().toLong())

        // Pick one stable bank type and one stable CC type for the whole file
        val bankType = allBankTypes.random(random)
        val ccType = allCreditCardTypes.random(random)

        var pageNum = 1
        val classifications = mutableListOf<ClassifiedPages>()

        spec.accountGroups.forEach { group ->
            group.classificationSpecs.forEach { classSpec ->
                val classificationType = when (classSpec) {
                    is ProxyClassificationSpec.StatementSpec ->
                        if (classSpec.isCreditCard) ccType else bankType
                    is ProxyClassificationSpec.CheckPageSpec ->
                        DocumentType.CheckTypes.CHECKS
                }
                classifications.add(ClassifiedPages(setOf(pageNum), classificationType))
                pageNum++
            }
        }

        logger.debug { "[Proxy Classifier] ${document.fileName} → ${classifications.size} classifications" }
        return ClassifiedFile(document.fileId, classifications)
    }
}
