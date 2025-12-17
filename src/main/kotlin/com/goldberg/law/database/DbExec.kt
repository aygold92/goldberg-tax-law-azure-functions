package com.goldberg.law.database

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

object DbExec {
    /**
     * Starts a transaction if none is currently active
     */
    fun <T> txnSafe(block: () -> T): T = TransactionManager.currentOrNull()?.let {
            block() // we're already in a transaction -- don't create a new one
        } ?: transaction { block() }

    // for batch CRUDL operations
    const val DEFAULT_BATCH_SIZE = 500
}