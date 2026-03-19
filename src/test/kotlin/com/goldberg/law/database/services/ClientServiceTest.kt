package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.exception.DuplicateEntityException
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.EntityValues
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ClientServiceTest : DatabaseTest() {

    private val clientService = ClientService(db)

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, load, list, then delete`() {
            // Insert
            val clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())

            // Load — verify all fields round-trip correctly
            val loaded = clientService.loadClient(clientId)
            assertThat(loaded.clientId).isEqualTo(clientId)
            assertThat(loaded.clientName).isEqualTo(EntityValues.DEFAULT_CLIENT_NAME)

            // List — verify client appears with correct fields
            val listed = clientService.listClients()
            assertThat(listed).hasSize(1)
            val listedClient = listed.single()
            assertThat(listedClient.clientId).isEqualTo(clientId)
            assertThat(listedClient.clientName).isEqualTo(EntityValues.DEFAULT_CLIENT_NAME)

            // Delete
            clientService.deleteClient(clientId)

            // Load after delete throws EntityNotFoundException
            assertThatThrownBy { clientService.loadClient(clientId) }
                .isInstanceOf(EntityNotFoundException::class.java)

            // List after delete is empty
            assertThat(clientService.listClients()).isEmpty()
        }
    }

    @Nested
    inner class InsertClient {

        @Test
        fun `same name and token is idempotent and returns the same UUID`() {
            val token = UUID.randomUUID()
            val firstId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, token)
            val secondId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, token)
            assertThat(secondId).isEqualTo(firstId)
        }

        @Test
        fun `duplicate name with different token throws DuplicateEntityException`() {
            clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
            assertThatThrownBy {
                clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
            }.isInstanceOf(DuplicateEntityException::class.java)
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
