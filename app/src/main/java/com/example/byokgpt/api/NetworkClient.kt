package com.example.byokgpt.api

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client with connection pooling for BYOKGPT.
 * Reuses TCP and TLS connections across Gemini, Claude, and OpenAI requests,
 * eliminating 200-800ms of handshake latency on subsequent turns.
 */
object NetworkClient {
    val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
