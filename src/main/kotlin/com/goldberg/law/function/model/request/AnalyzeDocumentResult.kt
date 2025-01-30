package com.goldberg.law.function.model.request

data class AnalyzeDocumentResult(
    val status: String,
    val result: Map<String, Set<String>>?,
    val errorMessage: String?,
) {
    object Status {
        const val SUCCESS = "Success"
        const val FAILED = "Failed"
    }

    companion object {
        fun success(result: Map<String, Set<String>>) = AnalyzeDocumentResult(Status.SUCCESS, result, null)
        fun failed(ex: Throwable) = AnalyzeDocumentResult(Status.FAILED, null, ex.message)
        fun failed(exceptions: List<Throwable>) = AnalyzeDocumentResult(Status.FAILED, null, exceptions.joinToString("\n") { it.message ?: it.toString() })
    }
}