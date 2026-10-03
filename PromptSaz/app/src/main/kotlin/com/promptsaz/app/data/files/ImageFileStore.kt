package com.promptsaz.app.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local storage for chat attachments and generated images + the compress /
 * base64 pipeline that feeds the vision models.
 *
 * Images are compressed to a sane size (max dimension, JPEG) before they are
 * sent to the API, so a 12-megapixel photo does not become a 10-MB request.
 */
interface ImageFileStore {

    /** Saves a chat attachment; returns the stored file name. */
    fun saveChatImage(bytes: ByteArray, extension: String): String

    /** Saves a generated image; returns the stored file name. */
    fun saveGeneratedImage(bytes: ByteArray, extension: String): String

    fun readChatImage(fileName: String): ByteArray?

    fun readGeneratedImage(fileName: String): ByteArray?

    fun deleteGeneratedImage(fileName: String)

    /** Downscales + re-encodes for upload; returns the input unchanged when undecodable. */
    fun compressForUpload(bytes: ByteArray, maxDimension: Int = 1280, quality: Int = 85): ByteArray

    /** Wraps bytes as a data: URL for the OpenAI image_url content part. */
    fun toDataUrl(bytes: ByteArray): String
}

@Singleton
class AndroidImageFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImageFileStore {

    private val chatDir: File get() = File(context.filesDir, CHAT_DIR).apply { mkdirs() }
    private val generatedDir: File get() = File(context.filesDir, GENERATED_DIR).apply { mkdirs() }

    override fun saveChatImage(bytes: ByteArray, extension: String): String {
        val name = randomName(extension)
        File(chatDir, name).writeBytes(bytes)
        return name
    }

    override fun saveGeneratedImage(bytes: ByteArray, extension: String): String {
        val name = randomName(extension)
        File(generatedDir, name).writeBytes(bytes)
        return name
    }

    override fun readChatImage(fileName: String): ByteArray? =
        runCatching { File(chatDir, fileName).readBytes() }.getOrNull()

    override fun readGeneratedImage(fileName: String): ByteArray? =
        runCatching { File(generatedDir, fileName).readBytes() }.getOrNull()

    override fun deleteGeneratedImage(fileName: String) {
        runCatching { File(generatedDir, fileName).delete() }
    }

    override fun compressForUpload(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        return runCatching {
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }.getOrDefault(bytes)
    }

    override fun toDataUrl(bytes: ByteArray): String =
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun randomName(extension: String): String {
        val suffix = extension.removePrefix(".").ifBlank { "jpg" }
        return "${System.currentTimeMillis()}-${SecureRandom().nextInt(1_000_000)}.$suffix"
    }

    companion object {
        const val CHAT_DIR = "chat_images"
        const val GENERATED_DIR = "generated_images"
    }
}
