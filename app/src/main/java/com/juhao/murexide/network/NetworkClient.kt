package com.juhao.murexide.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    const val BASE_URL = "https://chat-go.jwzhd.com"
    
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Content-Type", "application/json")
                    .build()
            )
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}