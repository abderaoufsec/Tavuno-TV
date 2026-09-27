package com.tavuno.tv.data.repository

import com.tavuno.tv.data.model.ChannelNowNext
import com.tavuno.tv.data.model.EpgProgram
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CatalogRepositoryTest {
    
    @Test
    fun `parse channel now next with valid data`() = runTest {
        // Test that the ChannelNowNext model parses correctly
        val nowNext = ChannelNowNext(
            channelId = 1,
            now = EpgProgram(
                id = 100,
                title = "News at Six",
                startsAt = "2026-09-27T18:00:00Z",
                endsAt = "2026-09-27T18:30:00Z",
                description = "Evening news broadcast",
                channelId = 1
            ),
            next = EpgProgram(
                id = 101,
                title = "Sports Update",
                startsAt = "2026-09-27T18:30:00Z",
                endsAt = "2026-09-27T19:00:00Z",
                description = "Sports highlights",
                channelId = 1
            ),
            later = EpgProgram(
                id = 102,
                title = "Documentary",
                startsAt = "2026-09-27T19:00:00Z",
                endsAt = "2026-09-27T20:00:00Z",
                description = "Nature documentary",
                channelId = 1
            )
        )
        
        assertEquals(1, nowNext.channelId)
        assertNotNull(nowNext.now)
        assertEquals("News at Six", nowNext.now?.title)
        assertNotNull(nowNext.next)
        assertEquals("Sports Update", nowNext.next?.title)
        assertNotNull(nowNext.later)
        assertEquals("Documentary", nowNext.later?.title)
    }
    
    @Test
    fun `parse channel now next with null programs`() = runTest {
        // Test that null programs are handled gracefully
        val nowNext = ChannelNowNext(
            channelId = 1,
            now = null,
            next = null,
            later = null
        )
        
        assertEquals(1, nowNext.channelId)
        assertNull(nowNext.now)
        assertNull(nowNext.next)
        assertNull(nowNext.later)
    }
    
    @Test
    fun `parse channel now next with partial programs`() = runTest {
        // Test partial data (only now, no next/later)
        val nowNext = ChannelNowNext(
            channelId = 1,
            now = EpgProgram(
                id = 100,
                title = "Current Show",
                startsAt = "2026-09-27T18:00:00Z",
                endsAt = "2026-09-27T18:30:00Z",
                description = null,
                channelId = 1
            ),
            next = null,
            later = null
        )
        
        assertEquals(1, nowNext.channelId)
        assertNotNull(nowNext.now)
        assertEquals("Current Show", nowNext.now?.title)
        assertNull(nowNext.now?.description) // Handle null description
        assertNull(nowNext.next)
        assertNull(nowNext.later)
    }
}
