package com.goldberg.law.document.model.output

data class CheckDataKey(
    val accountNumber: String?,
    val checkNumber: Int?
) {
    override fun toString() = "$accountNumber - $checkNumber"

    fun isComplete(): Boolean = listOf(accountNumber, checkNumber).all{ it != null }
}