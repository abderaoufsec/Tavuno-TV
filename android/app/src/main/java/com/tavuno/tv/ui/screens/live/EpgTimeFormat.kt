package com.tavuno.tv.ui.screens.live

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formats EPG timestamps for the Live TV UI.
 *
 * The API always returns UTC timestamps (typically `2026-10-01T14:10:00Z`;
 * older cached payloads used a `2026-10-01 14:10:00+00:00` variant), so parsing
 * tolerates both shapes and any unparseable value degrades to null instead of
 * crashing playback navigation.
 */
object EpgTimeFormat {

    private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Parses an API timestamp into an [Instant], or null when unusable. */
    fun parseInstant(value: String?): Instant? {
        val candidate = value?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        parseStrict(candidate)?.let { return it }
        // Legacy cached values used a space separator ("2026-10-01 14:10:00+00:00").
        val isoLike = candidate.replaceFirst(' ', 'T')
        return if (isoLike != candidate) parseStrict(isoLike) else null
    }

    private fun parseStrict(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching {
                OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
            }.getOrNull()

    /** Local wall-clock label such as "14:10", or null when unavailable. */
    fun clockLabel(value: String?, zone: ZoneId = ZoneId.systemDefault()): String? =
        parseInstant(value)?.let { clockFormatter.withZone(zone).format(it) }

    /** Range label such as "14:10 - 14:15", degrading when only one side is known. */
    fun rangeLabel(
        startsAt: String?,
        endsAt: String?,
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        val start = clockLabel(startsAt, zone)
        val end = clockLabel(endsAt, zone)
        return when {
            start != null && end != null -> "$start - $end"
            start != null -> start
            else -> end
        }
    }
}
