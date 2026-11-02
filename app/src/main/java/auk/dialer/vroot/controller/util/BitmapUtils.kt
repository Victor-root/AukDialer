package auk.dialer.vroot.controller.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * The image at [uri], decoded no larger than needed for [maxSize] pixels on its longer side, or null
 * when it cannot be read. A camera photo decoded at full size takes hundreds of megabytes.
 */
fun decodeSampledBitmap(contentResolver: ContentResolver, uri: Uri, maxSize: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        var sampleSize = 1
        while (longest / (sampleSize * 2) >= maxSize) sampleSize *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val decodedLongest = maxOf(decoded.width, decoded.height)
        if (decodedLongest <= maxSize) return decoded

        val scale = maxSize.toFloat() / decodedLongest
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            .also { if (it != decoded) decoded.recycle() }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
