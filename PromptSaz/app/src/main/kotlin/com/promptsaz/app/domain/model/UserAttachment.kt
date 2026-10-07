package com.promptsaz.app.domain.model

/**
 * One user-attached file of ANY type (image, audio, video, PDF, markdown,
 * code, …). Lives in memory only — the prompt archive stores just the
 * generated text; chat attachments persist through their own store and are
 * referenced by [ChatAttachmentMeta].
 *
 * Transport rules (per service protocol, decided in the provider):
 *  - text-like files travel as plain text parts (cheap + universally supported)
 *  - images/audio/video/pdf travel as inline base64 data (Gemini) or
 *    image_url parts (OpenAI-compatible — images only)
 */
data class UserAttachment(
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isTextLike: Boolean get() = isTextLike(displayName, mimeType)

    /** UTF-8 content of a text-like file, capped — null for binary files. */
    fun textContent(maxChars: Int = MAX_TEXT_CHARS): String? =
        if (isTextLike) String(bytes, Charsets.UTF_8).take(maxChars) else null

    companion object {
        /** API inline payloads are capped at 20 MB — stay safely under it. */
        const val MAX_FILE_BYTES: Int = 15 * 1024 * 1024
        const val MAX_TOTAL_BYTES: Int = 18 * 1024 * 1024

        /** A text file bigger than this is truncated, not rejected. */
        const val MAX_TEXT_CHARS = 60_000

        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "csv", "json", "xml", "yml", "yaml", "log",
            "py", "kt", "java", "js", "ts", "html", "css", "c", "cpp", "h",
            "cs", "go", "rs", "swift", "php", "rb", "sh", "bat", "sql", "ini",
            "toml", "conf", "svg", "srt", "vtt",
        )

        fun extensionOf(name: String): String =
            name.substringAfterLast('.', "").substringBefore('?').lowercase().take(12)

        /** Text-like = decodable as UTF-8 text and meaningful to a model. */
        fun isTextLike(name: String, mime: String): Boolean {
            if (mime.startsWith("text/")) return true
            if (mime == "application/json" || mime == "application/xml") return true
            if (mime == "application/octet-stream" || mime.isBlank()) {
                return extensionOf(name) in TEXT_EXTENSIONS
            }
            return extensionOf(name) in TEXT_EXTENSIONS
        }

        /** Best-effort mime for a display name — for pickers that report none. */
        fun guessMime(name: String): String = when (extensionOf(name)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "bmp" -> "image/bmp"
            "pdf" -> "application/pdf"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "mp4", "m4v" -> "video/mp4"
            "mpeg", "mpg" -> "video/mpeg"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "txt", "log" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            "csv" -> "text/csv"
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "text/javascript"
            "json" -> "application/json"
            "xml", "svg" -> "application/xml"
            else -> "application/octet-stream"
        }
    }
}
