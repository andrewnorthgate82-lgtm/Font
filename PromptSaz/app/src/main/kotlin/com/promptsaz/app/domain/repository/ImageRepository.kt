package com.promptsaz.app.domain.repository

import com.promptsaz.app.domain.model.ImageGeneration
import kotlinx.coroutines.flow.Flow

/** History + generation flow of the تصویر (image studio) tab. */
interface ImageRepository {

    /** Past generations, newest first. */
    fun history(): Flow<List<ImageGeneration>>

    /**
     * Generates one image with [model] and saves it into app storage.
     * Returns the saved history entry.
     */
    suspend fun generate(prompt: String, model: String, size: String, serviceId: String? = null): Result<ImageGeneration>

    /** Reads a saved generation's bytes (for the UI). */
    fun readImage(fileName: String): ByteArray?

    suspend fun delete(generationId: Long)

    /**
     * Fallback for services without an images endpoint (HTTP 404): asks the
     * chat model to write a professional image-generation prompt (English,
     * for tools like Midjourney / DALL·E) from the user's description.
     */
    suspend fun generateImagePrompt(prompt: String, model: String, serviceId: String? = null): Result<String>
}
