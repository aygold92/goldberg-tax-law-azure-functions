package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.exception.DuplicateClientTokenException
import com.goldberg.law.database.exception.DuplicateEntityException
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.EntityValues.entityCompare
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ClientServiceTest : DatabaseTest() {

    private val clientService = ClientService(db)

    @BeforeEach
    fun clearClients() {
        db.txnSafe {
            ClientsTable.deleteAll()
        }
    }

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, load, list, then delete`() {
            Thread.sleep(20) // for timing
            // Insert
            val clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())

            // Load — verify all fields round-trip correctly
            Thread.sleep(20) // to ensure timing
            val loaded = clientService.loadClient(clientId)
            assertThat(loaded).entityCompare()
                .isEqualTo(EntityValues.newClient(clientId = clientId))
            assertThat(loaded.clientId).isEqualTo(clientId)
            assertTimeIsDuringTest(loaded.createdAt)

            // List — verify client appears with correct fields
            val listed = clientService.listClients()
            assertThat(listed).hasSize(1)
            assertThat(listed.single()).isEqualTo(loaded)

            // Delete
            assertThat(clientService.deleteClient(clientId)).isEqualTo(1)

            // Load after delete throws EntityNotFoundException
            assertThatThrownBy { clientService.loadClient(clientId) }
                .isInstanceOf(EntityNotFoundException::class.java)

            // List after delete is empty
            assertThat(clientService.listClients()).isEmpty()
        }
    }

    @Nested
    inner class ClientToken {
        @Test
        fun `same name and token is idempotent and returns the same UUID`() {
            val token = UUID.randomUUID()
            val firstId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, token)
            val secondId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, token)
            assertThat(secondId).isEqualTo(firstId)
        }

        @Test
        fun `duplicate name with different token throws DuplicateEntityException with name and id`() {
            val clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
            assertThatThrownBy {
                clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
            }.isInstanceOf(DuplicateEntityException::class.java)
                .hasMessageContaining(clientId.toString())
                .hasMessageContaining(EntityValues.DEFAULT_CLIENT_NAME)
        }

        @Test
        fun `duplicate client token`() {
            val clientToken = UUID.randomUUID()
            clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, clientToken)
            assertThatThrownBy {
                clientService.insertClient("Other ClientName", clientToken)
            }.isInstanceOf(DuplicateClientTokenException::class.java)
        }
    }

    @Nested
    inner class DeleteClient {

        @Test
        fun `non-existent ID throws EntityNotFoundException`() {
            assertThatThrownBy { clientService.deleteClient(UUID.randomUUID()) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class ListClients {
        @Test
        fun `returns all inserted clients with correct names`() {
            clientService.insertClient("Client Alpha", UUID.randomUUID())
            clientService.insertClient("Client Beta", UUID.randomUUID())

            val clients = clientService.listClients()

            assertThat(clients).hasSize(2)
            assertThat(clients.map { it.clientName })
                .containsExactlyInAnyOrder("Client Alpha", "Client Beta")
        }
    }
}
