package com.tavuno.tv.data.repository

/**
 * Build the Authorization header for an authenticated call.
 *
 * Free launch (`AUTH_OPEN_ACCESS`) runs without a login, so there is no access
 * token to send. Returning `null` lets Retrofit omit the header entirely and
 * the backend resolves its seeded guest identity — instead of the app failing
 * with "No access token available" the moment a stream is opened.
 *
 * @param token the stored access token, or null when running as a guest.
 * @return `"Bearer <token>"`, or null when there is nothing genuine to send.
 */
internal fun bearerAuthorization(token: String?): String? =
    token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }