package com.streamvault.data.remote.tavuno

import com.streamvault.data.local.entity.ProgramEntity
import com.streamvault.domain.model.Program
import com.streamvault.domain.model.Result
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TavunoEpgRepository @Inject constructor(
    private val tavunoApiService: TavunoApiService
) {

    suspend fun refreshEpgForProvider(providerId: Long): Result<List<ProgramEntity>> {
        return try {
            val response = tavunoApiService.getEpg(null)
            if (response.isSuccessful && response.body() != null) {
                val programs = response.body()!!.mapNotNull { dto ->
                    dto.toEntity(providerId)
                }
                Result.success(programs)
            } else {
                Result.error("Failed to fetch Tavuno EPG: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.error("Network error: ${e.message}")
        }
    }

    suspend fun refreshEpgForChannel(providerId: Long, channelId: Int): Result<List<ProgramEntity>> {
        return try {
            val response = tavunoApiService.getEpg(channelId)
            if (response.isSuccessful && response.body() != null) {
                val programs = response.body()!!.mapNotNull { dto ->
                    dto.toEntity(providerId)
                }
                Result.success(programs)
            } else {
                Result.error("Failed to fetch Tavuno EPG for channel: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.error("Network error: ${e.message}")
        }
    }

    private fun EpgProgramDto.toEntity(providerId: Long): ProgramEntity? {
        val startTime = parseIsoDateTime(startsAt)
        val endTime = parseIsoDateTime(endsAt)
        val channelIdStr = channelId?.toString() ?: return null
        return ProgramEntity(
            id = id.toLong(),
            providerId = providerId,
            channelId = channelIdStr,
            title = title,
            description = description ?: "",
            startTime = startTime,
            endTime = endTime,
            lang = "",
            rating = null,
            imageUrl = null,
            genre = null,
            category = null,
            hasArchive = false
        )
    }

    private fun parseIsoDateTime(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }
}
