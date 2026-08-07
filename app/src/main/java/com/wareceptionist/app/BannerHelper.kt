package com.wareceptionist.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object BannerHelper {

    /**
     * Extracts userx_banner.jpg from res/drawable to cacheDir and returns a shareable content:// URI
     */
    fun getBannerUri(context: Context): Uri? {
        return try {
            val cacheFile = File(context.cacheDir, "userx_banner.jpg")
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                context.resources.openRawResource(R.drawable.userx_banner).use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
        } catch (e: Exception) {
            AppLogger.log(context, "❌ Error preparing banner image URI: ${e.message}")
            null
        }
    }
}
