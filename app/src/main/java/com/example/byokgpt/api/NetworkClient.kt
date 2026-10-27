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
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Dedicated OkHttpClient for SSE/streaming LLM responses.
     * readTimeout(0) ensures the stream does not abort during reasoning/thinking pauses.
     */
    val streamingHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
