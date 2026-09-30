package com.goldberg.law.entity

/**
 * Where a human review of an agent's flags stands. A null status wherever it's stored means the agent flagged
 * nothing, so no review was ever needed.
 */
enum class ReviewStatus {
    /** The agent flagged something a human hasn't looked at yet. */
    PENDING,
    /** A human has looked at everything flagged. */
    VERIFIED,
    ;

    companion object {
        /** The status a record starts with: pending if the agent flagged anything, otherwise none. */
        fun initial(flagged: Boolean): ReviewStatus? = if (flagged) PENDING else null
    }
}
