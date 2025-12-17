package com.goldberg.law.database.tables

import com.goldberg.law.database.exception.DuplicateClientTokenException
import com.goldberg.law.database.exception.DuplicateEntityException
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import java.sql.SQLIntegrityConstraintViolationException
import java.util.UUID

// Type-safe wrapper for column-value pairs
class TypedColumnValue<T>(val column: Column<T>, val value: T)

// Extension function for easy creation with type safety
infix fun <T> Column<T>.withValue(value: T): TypedColumnValue<T> = TypedColumnValue(this, value)

open class ClientTokenUUIDTable(name: String = "", columnName: String = "id"): UUIDTable(name, columnName) {
    val clientToken = uuid("clientToken").uniqueIndex()

    private val tableNameWithoutFinalS = name.replace("s$", "")
    /**
     * Check for duplicate with idempotency support
     * @param ex The constraint violation exception
     * @param clientToken The client token for idempotency
     * @param uniqueKeyPairs Type-safe column-value pairs for the unique constraint that was violated
     */
    fun checkClientToken(ex: Exception, clientToken: UUID, vararg uniqueKeyPairs: TypedColumnValue<*>): UUID {
        if (!(ex is SQLIntegrityConstraintViolationException && ex.message?.contains("Duplicate") == true)) {
            throw ex
        }
        
        // Build query to find existing record
        var query = this.selectAll()
        uniqueKeyPairs.forEach { typed ->
            query = query.andWhere { 
                @Suppress("UNCHECKED_CAST")
                (typed.column as Column<Any>) eq (typed.value as Any)
            }
        }
        
        val existingRecord = query.singleOrNull() ?:
            throw DuplicateClientTokenException("Duplicate token $clientToken. Please generate a UUID and try again")

        val existingToken = existingRecord[this.clientToken]
        return if (existingToken == clientToken) {
            // Same entity and token - idempotent success
            existingRecord[this.id].value
        } else {
            // Same entity, different token - conflict
            throw DuplicateEntityException("Duplicate $tableNameWithoutFinalS: ${uniqueKeyPairs.map { it.value }} ")
        }
    }
}