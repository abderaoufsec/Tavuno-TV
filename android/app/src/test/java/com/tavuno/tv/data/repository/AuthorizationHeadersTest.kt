package com.tavuno.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the free-launch guest path.
 *
 * Before this, playback authorization called a token lookup that threw
 * "No access token available", so opening any stream failed on a guest build.
 */
class AuthorizationHeadersTest {

    @Test
    fun `guest with no token sends no header instead of failing`() {
        assertNull(bearerAuthorization(null))
    }

    @Test
    fun `blank token is treated as guest`() {
        assertNull(bearerAuthorization(""))
        assertNull(bearerAuthorization("   "))
    }

    @Test
    fun `real token becomes a bearer header`() {
        assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig", bearerAuthorization("eyJhbGciOiJIUzI1NiJ9.payload.sig"))
    }
}