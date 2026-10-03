package com.promptsaz.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.promptsaz.app.R

/**
 * Clipboard + share helpers. Persian errors surface as toasts when the
 * operation fails (rare, but possible on some OEM launchers).
 */
object PlatformUtils {

    fun copyToClipboard(context: Context, text: String): Boolean = try {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("PromptSaz", text))
        true
    } catch (_: Exception) {
        false
    }

    fun copyWithFeedback(context: Context, text: String) {
        val ok = copyToClipboard(context, text)
        Toast.makeText(
            context,
            if (ok) context.getString(R.string.copy_success) else context.getString(R.string.copy_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_via)))
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
