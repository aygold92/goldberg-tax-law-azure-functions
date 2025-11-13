package com.goldberg.law.function.model.response

import com.goldberg.law.document.model.output.BankStatement

data class LoadBankStatementResponse(
    val statement: BankStatement,
    val suspiciousReasons: List<String>,
)


