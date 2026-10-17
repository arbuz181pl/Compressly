package com.compressly

import android.app.Application
import com.compressly.data.ThumbnailStrip
import com.compressly.service.CompressionNotifications

class CompresslyApp : Application() {

    /**
     * Shared across the whole process. Decoding a filmstrip is expensive, so every screen
     * reads from the same bounded cache.
     */
    val thumbnails: ThumbnailStrip by lazy { ThumbnailStrip(this) }

    override fun onCreate() {
        super.onCreate()
        CompressionNotifications.createChannel(this)
    }
}
