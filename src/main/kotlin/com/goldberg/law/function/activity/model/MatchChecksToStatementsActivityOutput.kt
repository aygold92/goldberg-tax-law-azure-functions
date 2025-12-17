package com.goldberg.law.function.activity.model

import com.goldberg.law.entity.Transaction

data class MatchChecksToStatementsActivityOutput(
     val matchedTransactions: List<Transaction>,
)