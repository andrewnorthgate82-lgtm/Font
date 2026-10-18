package com.font.localaipoc

import android.content.Context
import android.os.Environment
import java.io.File

enum class ModelSpec(
    val title: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val sizeLabel: String,
) {
    E2B(
        title = "Gemma 4 E2B — مدل پایه",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm?download=true",
        expectedBytes = 2_588_147_712L,
        sizeLabel = "حدود ۲٫۶ گیگابایت",
    ),
    E4B(
        title = "Gemma 4 E4B — مدل بزرگ‌تر",
        fileName = "gemma-4-E4B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/gemma-4-E4B-it.litertlm?download=true",
        expectedBytes = 3_659_530_240L,
        sizeLabel = "حدود ۳٫۷ گیگابایت",
    );

    val minimumAcceptedBytes: Long
        get() = expectedBytes * 99 / 100

    fun file(context: Context): File {
        val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("حافظهٔ فایل‌های برنامه در دسترس نیست.")
        return File(folder, fileName)
    }

    fun isComplete(file: File): Boolean = file.isFile && file.length() >= minimumAcceptedBytes
}
