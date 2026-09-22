package com.goldberg.law.document.exception

import com.goldberg.law.entity.EntityType
import java.util.UUID

class EntityNotFoundException private constructor(message: String) : RuntimeException(message) {
    constructor(entityType: EntityType, id: UUID) :
        this("$entityType with id $id not found")
    constructor(entityType: EntityType, ids: List<UUID>) :
        this("$entityType with ids $ids not found")
    /** For lookups by something other than an id, e.g. the agent session that produced the entity. */
    constructor(entityType: EntityType, lookup: String) :
        this("$entityType for $lookup not found")
}
