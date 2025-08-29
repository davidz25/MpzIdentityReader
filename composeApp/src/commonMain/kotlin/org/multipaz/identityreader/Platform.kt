package org.multipaz.identityreader

import io.ktor.client.engine.HttpClientEngineFactory

interface Platform {
    val name: String

    fun exitApp()
}

expect fun getPlatform(): Platform

expect fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*>
