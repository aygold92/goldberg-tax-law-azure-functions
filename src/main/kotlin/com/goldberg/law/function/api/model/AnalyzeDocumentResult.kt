package com.goldberg.law.function.api.model

import com.goldberg.law.function.model.ExtractedDocumentIds
import java.util.*

data class AnalyzeDocumentResult(
    val status: String,
    val result: Map<UUID, ExtractedDocumentIds>?,
    val errorMessage: String?,
) {
    object Status {
        const val SUCCESS = "Success"
        const val FAILED = "Failed"
    }

    companion object {
        fun success(result: Map<UUID, ExtractedDocumentIds>) = AnalyzeDocumentResult(Status.SUCCESS, result, null)
        fun failed(ex: Throwable) = AnalyzeDocumentResult(Status.FAILED, null, ex.message)
        fun failed(exceptions: List<Throwable>) = AnalyzeDocumentResult(
            Status.FAILED,
            null,
            exceptions.joinToString("\n") { it.message ?: it.toString() }
        )
    }
}