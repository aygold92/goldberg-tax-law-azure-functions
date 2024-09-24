package com.goldberg.law.function.model.request

data class AnalyzeDocumentResult(
    val status: String,
    val result: Collection<String>?,
    val errorMessage: String?,
) {
    object Status {
        const val SUCCESS = "Success"
        const val FAILED = "Failed"
    }

    companion object {
        fun success(result: Collection<String>) = AnalyzeDocumentResult(Status.SUCCESS, result, null)
        fun failed(ex: Throwable) = AnalyzeDocumentResult(Status.FAILED, null, ex.message)
    }
}