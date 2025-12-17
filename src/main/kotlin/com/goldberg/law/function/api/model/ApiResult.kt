package com.goldberg.law.function.api.model

open class ApiResult(
    val status: ApiStatus,
    val errorMessage: String? = null
) {
    enum class ApiStatus {
        Success, Failed
    }

    companion object {
        fun failed(errorMessage: String) = ApiResult(ApiStatus.Failed, errorMessage)
        fun failed(ex: Throwable) = ApiResult(ApiStatus.Failed, ex.message ?: ex.javaClass.name)
        fun failed(exceptions: List<Throwable>) = ApiResult(
            ApiStatus.Failed,
            exceptions.joinToString("\n") { it.message ?: it.toString() }
        )
    }
}

