package com.goldberg.law.function.model

import com.goldberg.law.document.model.input.DocumentDataModelTest.Companion.GSON
import com.goldberg.law.function.model.activity.SplitPdfActivityOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PdfPageDataTest {
    @Test
    fun testSeralize() {
        val model = SplitPdfActivityOutput(pdfPages = setOf(PdfPageData("test", 1), PdfPageData("test", 2)))
        val otherModel = GSON.fromJson(GSON.toJson(model), SplitPdfActivityOutput::class.java)
        assertThat(otherModel).isEqualTo(model)
    }
}