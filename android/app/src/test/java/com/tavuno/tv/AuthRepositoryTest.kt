package com.tavuno.tv

import com.tavuno.tv.data.model.LoginRequest
import com.tavuno.tv.data.model.LoginResponse
import com.tavuno.tv.data.model.Profile
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {
    
    @Test
    fun `login request should have correct structure`() {
        // Arrange
        val email = "test@example.com"
        val password = "password123"
        val deviceFingerprint = "test_device_123"
        
        // Act
        val request = LoginRequest(email, password, deviceFingerprint, "android-tv")
        
        // Assert
        assertEquals(email, request.email)
        assertEquals(password, request.password)
        assertEquals(deviceFingerprint, request.deviceFingerprint)
        assertEquals("android-tv", request.platform)
    }
    
    @Test
    fun `login response should contain required fields`() {
        // Arrange
        val loginResponse = LoginResponse(
            accessToken = "access_token",
            refreshToken = "refresh_token",
            accessExpiresAt = 1234567890.0,
            refreshExpiresAt = 1234567890.0,
            profile = Profile(1, "test@example.com", "Test User")
        )
        
        // Assert
        assertNotNull(loginResponse.accessToken)
        assertNotNull(loginResponse.refreshToken)
        assertNotNull(loginResponse.profile)
        assertEquals(1, loginResponse.profile.id)
    }
    
    @Test
    fun `profile should contain required fields`() {
        // Arrange
        val profile = Profile(1, "test@example.com", "Test User")
        
        // Assert
        assertEquals(1, profile.id)
        assertEquals("test@example.com", profile.email)
        assertEquals("Test User", profile.displayName)
    }
}
