package com.goldberg.law.document.exception

import com.goldberg.law.entity.EntityType
import java.util.UUID

class EntityNotFoundException private constructor(message: String) : RuntimeException(message) {
    constructor(entityType: EntityType, id: UUID) :
        this("$entityType with id $id not found")
    constructor(entityType: EntityType, ids: List<UUID>) :
        this("$entityType with ids $ids not found")
}
