package com.goldberg.law.document.exception

import com.goldberg.law.util.toTransactionDate
import java.util.*

class BadDataException(
    fieldName: String,
    statementDate: Date?,
    pageNum: Int?
) : RuntimeException("Unable to find field $fieldName for statement ${statementDate?.toTransactionDate()}, page $pageNum")