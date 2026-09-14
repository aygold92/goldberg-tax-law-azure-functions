package com.goldberg.law.function.api.model

import com.goldberg.law.agent.MemoryConsolidation

data class ExecuteMemoryConsolidationAgentRequest(
    /** Which memory store to consolidate, named for the agent that writes it: `SPLITTING` or `EXTRACTION`. */
    val memory: MemoryConsolidation
)
