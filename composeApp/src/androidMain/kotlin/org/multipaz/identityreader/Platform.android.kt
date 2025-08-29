package org.multipaz.identityreader

import android.os.Build
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.android.Android
import org.multipaz.context.applicationContext
import org.multipaz.context.getActivity

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    override fun exitApp() {
        System.exit(0)
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*> = Android
