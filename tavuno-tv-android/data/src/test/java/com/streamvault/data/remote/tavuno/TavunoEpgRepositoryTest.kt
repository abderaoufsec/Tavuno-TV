package com.streamvault.data.remote.tavuno

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.entity.ProgramEntity
import com.streamvault.domain.model.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

class TavunoEpgRepositoryTest {

    private val tavunoApiService = mock<TavunoApiService>()
    private val repository = TavunoEpgRepository(tavunoApiService)

    @Test
    fun `refreshEpgForProvider returns program entities when API succeeds`() = runTest {
        val programs = listOf(
            EpgProgramDto(
                id = 1,
                title = "Show 1",
                startsAt = "2026-09-20T10:00:00Z",
                endsAt = "2026-09-20T11:00:00Z",
                description = "Description 1",
                channelId = 123
            ),
            EpgProgramDto(
                id = 2,
                title = "Show 2",
                startsAt = "2026-09-20T11:00:00Z",
                endsAt = "2026-09-20T12:00:00Z",
                description = "Description 2",
                channelId = 123
            )
        )

        whenever(tavunoApiService.getEpg(null)).thenReturn(Response.success(programs))

        val result = repository.refreshEpgForProvider(999L)

        when (result) {
            is Result.Success -> {
                val entities = result.data
                assertThat(entities).hasSize(2)
                assertThat(entities[0].title).isEqualTo("Show 1")
                assertThat(entities[0].channelId).isEqualTo("123")
                assertThat(entities[0].providerId).isEqualTo(999L)
                assertThat(entities[1].title).isEqualTo("Show 2")
            }
            is Result.Error -> {
                throw AssertionError("Expected success but got error: ${result.message}")
            }
            Result.Loading -> {
                throw AssertionError("Expected success but got loading")
            }
        }
    }

    @Test
    fun `refreshEpgForProvider returns empty list when API returns empty`() = runTest {
        whenever(tavunoApiService.getEpg(null)).thenReturn(Response.success(emptyList()))

        val result = repository.refreshEpgForProvider(999L)

        when (result) {
            is Result.Success -> {
                val entities = result.data
                assertThat(entities).isEmpty()
            }
            is Result.Error -> {
                throw AssertionError("Expected success but got error: ${result.message}")
            }
            Result.Loading -> {
                throw AssertionError("Expected success but got loading")
            }
        }
    }

    @Test
    fun `refreshEpgForProvider returns error when API fails`() = runTest {
        whenever(tavunoApiService.getEpg(null)).thenReturn(
            Response.error(500, okhttp3.ResponseBody.create(null, "Server error"))
        )

        val result = repository.refreshEpgForProvider(999L)

        when (result) {
            is Result.Success -> {
                throw AssertionError("Expected error but got success")
            }
            is Result.Error -> {
                assertThat(result.message).contains("Failed to fetch Tavuno EPG")
            }
            Result.Loading -> {
                throw AssertionError("Expected error but got loading")
            }
        }
    }

    @Test
    fun `refreshEpgForProvider filters programs without channelId`() = runTest {
        val programs = listOf(
            EpgProgramDto(
                id = 1,
                title = "Show 1",
                startsAt = "2026-09-20T10:00:00Z",
                endsAt = "2026-09-20T11:00:00Z",
                description = "Description 1",
                channelId = 123
            ),
            EpgProgramDto(
                id = 2,
                title = "Show 2",
                startsAt = "2026-09-20T11:00:00Z",
                endsAt = "2026-09-20T12:00:00Z",
                description = "Description 2",
                channelId = null // Should be filtered out
            )
        )

        whenever(tavunoApiService.getEpg(null)).thenReturn(Response.success(programs))

        val result = repository.refreshEpgForProvider(999L)

        when (result) {
            is Result.Success -> {
                val entities = result.data
                assertThat(entities).hasSize(1)
                assertThat(entities[0].title).isEqualTo("Show 1")
            }
            is Result.Error -> {
                throw AssertionError("Expected success but got error: ${result.message}")
            }
            Result.Loading -> {
                throw AssertionError("Expected success but got loading")
            }
        }
    }

    @Test
    fun `refreshEpgForChannel returns program entities for specific channel`() = runTest {
        val programs = listOf(
            EpgProgramDto(
                id = 1,
                title = "Channel Show",
                startsAt = "2026-09-20T10:00:00Z",
                endsAt = "2026-09-20T11:00:00Z",
                description = "Description",
                channelId = 456
            )
        )

        whenever(tavunoApiService.getEpg(456)).thenReturn(Response.success(programs))

        val result = repository.refreshEpgForChannel(999L, 456)

        when (result) {
            is Result.Success -> {
                val entities = result.data
                assertThat(entities).hasSize(1)
                assertThat(entities[0].channelId).isEqualTo("456")
            }
            is Result.Error -> {
                throw AssertionError("Expected success but got error: ${result.message}")
            }
            Result.Loading -> {
                throw AssertionError("Expected success but got loading")
            }
        }
    }
}
