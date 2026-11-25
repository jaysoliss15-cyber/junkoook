package com.example.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object MagicByteValidator {

    enum class ImageFormat {
        PNG,
        JPEG,
        WEBP,
        UNKNOWN
    }

    fun detectFormat(bytes: ByteArray): ImageFormat {
        if (bytes.size < 12) return ImageFormat.UNKNOWN

        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return ImageFormat.PNG
        }

        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return ImageFormat.JPEG
        }

        // WebP: RIFF .... WEBP
        if (bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte()
        ) {
            return ImageFormat.WEBP
        }

        return ImageFormat.UNKNOWN
    }

    const val MIN_VERIFIED_IMAGE_BYTES = 15 * 1024 // 15 KB
    const val MIN_VERIFIED_WIDTH_PX = 480
    const val MIN_VERIFIED_HEIGHT_PX = 800

    fun isValidImageBytes(bytes: ByteArray): Boolean {
        return detectFormat(bytes) != ImageFormat.UNKNOWN
    }

    fun meetsMinimumFileSize(bytes: ByteArray, minBytes: Int = MIN_VERIFIED_IMAGE_BYTES): Boolean {
        return bytes.size >= minBytes
    }

    fun meetsMinimumDimensions(
        width: Int,
        height: Int,
        minWidth: Int = MIN_VERIFIED_WIDTH_PX,
        minHeight: Int = MIN_VERIFIED_HEIGHT_PX
    ): Boolean {
        return width >= minWidth && height >= minHeight
    }

    fun validateAndDecode(bytes: ByteArray): Bitmap? {
        if (!isValidImageBytes(bytes)) {
            return null
        }
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Throwable) {
            null
        }
    }

    fun validateAndDecodeVerified(
        bytes: ByteArray,
        minBytes: Int = MIN_VERIFIED_IMAGE_BYTES,
        minWidth: Int = MIN_VERIFIED_WIDTH_PX,
        minHeight: Int = MIN_VERIFIED_HEIGHT_PX
    ): Bitmap? {
        if (!meetsMinimumFileSize(bytes, minBytes)) return null
        val decoded = validateAndDecode(bytes) ?: return null
        if (!meetsMinimumDimensions(decoded.width, decoded.height, minWidth, minHeight)) {
            decoded.recycle()
            return null
        }
        return decoded
    }
}
