package com.goldberg.law.database.tables

import com.goldberg.law.database.exception.DuplicateClientTokenException
import com.goldberg.law.database.exception.DuplicateEntityException
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.sql.SQLIntegrityConstraintViolationException
import java.util.UUID

// Type-safe wrapper for column-value pairs
data class TypedColumnValue<T>(val column: Column<T>, val value: T)

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
    fun checkClientToken(ex: Exception, clientToken: UUID, vararg uniqueKeyPairings: Pair<List<TypedColumnValue<*>>, String>): UUID {
        // Exposed wraps JDBC exceptions in ExposedSQLException — unwrap to get the real cause.
        // SQLIntegrityConstraintViolationException is the standard JDBC type for unique violations
        // in both MySQL and H2, so no DB-specific message sniffing needed.
        val cause = (ex as? ExposedSQLException)?.cause ?: ex
        if (cause !is SQLIntegrityConstraintViolationException) throw ex
        
        // Each inner list is AND-ed; the outer lists are OR-ed together.
        val condition = uniqueKeyPairings.map { (andGroup, _) ->
            andGroup.map { typed ->
                @Suppress("UNCHECKED_CAST")
                (typed.column as Column<Any>) eq (typed.value as Any)
            }.reduce { a, b -> a and b }
        }.reduce { a, b -> a or b }

        val existingRecord = this.selectAll().andWhere { condition }.singleOrNull()
            ?: throw DuplicateClientTokenException("Duplicate token $clientToken. Please generate a UUID and try again")

        val existingToken = existingRecord[this.clientToken]
        val existingId = existingRecord[this.id].value

        val allGroupsMatch = uniqueKeyPairings.all { (group, _) ->
            group.all { typed ->
                existingRecord[typed.column] == typed.value
            }
        }

        return if (existingToken == clientToken && allGroupsMatch) {
            existingId
        } else if (existingToken == clientToken) {
            throw DuplicateClientTokenException("Token $clientToken already used with different data. Please generate a new UUID and try again")
        } else {
            val commonColumns = if (uniqueKeyPairings.size > 1)
                uniqueKeyPairings.map { (group, _) -> group.map { it.column }.toSet() }.reduce { a, b -> a intersect b }
            else emptySet()

            val matchedFields = uniqueKeyPairings
                .filter { (group, _) -> group.all { typed -> existingRecord[typed.column] == typed.value } }
                .flatMap { (group, msg) -> group.filter { it.column !in commonColumns }.map { msg } }
                .distinct()
            throw DuplicateEntityException("Duplicate $tableNameWithoutFinalS: ${matchedFields.joinToString(", ")} (existing Id=$existingId)")
        }
    }
}