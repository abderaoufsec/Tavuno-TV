package com.tavuno.tv

import com.google.gson.Gson
import com.tavuno.tv.data.model.LoginRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthModelsSerializationTest {

    @Test
    fun `LoginRequest serializes device_fingerprint in snake_case`() {
        val request = LoginRequest("a@b.com", "password123", "fingerprint-abc", "android-tv")
        val json = Gson().toJson(request)
        assertTrue(json.contains("\"device_fingerprint\":\"fingerprint-abc\""))
        assertFalse(json.contains("\"deviceFingerprint\""))
    }
}
