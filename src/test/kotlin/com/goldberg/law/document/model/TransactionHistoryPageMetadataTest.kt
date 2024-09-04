package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.pdf.model.DocumentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionHistoryPageMetadataTest {

    @Test
    fun testToCsvUnknownAccount() {
        assertThat(BASIC_TH_PAGE_METADATA.toCsv(null, DocumentType.BankTypes.WF_BANK))
            .isEqualTo("\"WF Bank - Unknown\",\"AG-12345\",4/7/2020,2,\"$FILENAME\",1")
    }
}