package com.goldberg.law.function.model.tracking

import com.microsoft.durabletask.TaskOrchestrationContext

/**
 * Initialize with the factory so that we can unit test the status
 */
class OrchestrationStatusFactory {
    fun new(ctx: TaskOrchestrationContext, stage: OrchestrationStage, filenames: Collection<String>) = OrchestrationStatus(
        ctx,
        stage,
        filenames.associateWith { DocumentOrchestrationStatus(it, null, null, null) }.toMutableMap(),
    )
}