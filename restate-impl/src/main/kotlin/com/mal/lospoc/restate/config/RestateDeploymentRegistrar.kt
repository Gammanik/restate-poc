package com.mal.lospoc.restate.config

import jakarta.annotation.PostConstruct
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RestateDeploymentRegistrar {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @PostConstruct
    fun registerDeployment() {
        // Wait a bit for Restate endpoint to start
        Thread.sleep(2000)

        try {
            val json = """{"uri":"http://host.docker.internal:9080"}"""
            val request = Request.Builder()
                .url("http://localhost:9070/deployments")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    println("✅ Successfully registered Restate deployment at http://host.docker.internal:9080")
                } else {
                    println("⚠️  Failed to register Restate deployment: ${response.code} - ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            println("⚠️  Could not register Restate deployment (Restate server may not be running): ${e.message}")
        }
    }
}
