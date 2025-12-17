package com.goldberg.law.function.model.tracking

import java.util.*

data class DocumentOrchestrationStatus(
    val fileId: UUID,
    var numStatementPages: Int?,
    var numCheckPages: Int?,
    var docsAnalyzed: Int?,
    var classified: Boolean,
) {
    val numDocsTotal: Int?
        get() = Pair(numStatementPages, numCheckPages).let { (nS, nC) ->
            if (nS == null && nC == null) null
            else if (nS == null) nC
            else if (nC == null) nS
            else nS + nC
        }
    fun incrementDocumentsAnalyzed() = this.also {
        docsAnalyzed = docsAnalyzed?.plus(1)
    }
}