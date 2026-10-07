package com.promptsaz.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.promptsaz.app.domain.model.ChatAttachmentMeta
import com.promptsaz.app.domain.model.UserAttachment

/**
 * The one attachment system of the app (گفتگو / تولید پرامپت / تولید تصویر):
 * a «+» opens the system file picker with ANY mime type and MULTI-SELECT —
 * no count limit; per-file and total size caps keep the API happy.
 */

/** Human-readable size, e.g. «۲.۴ مگابایت». */
fun formatFileSize(bytes: Int): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f", mb) + " مگابایت"
        kb >= 1.0 -> String.format("%.0f", kb) + " کیلوبایت"
        else -> "$bytes بایت"
    }
}

@Composable
private fun AttachmentIcon(mimeType: String, modifier: Modifier = Modifier) {
    val icon = when {
        mimeType.startsWith("image/") -> Icons.Rounded.Description
        mimeType.startsWith("audio/") -> Icons.Rounded.MusicNote
        mimeType.startsWith("video/") -> Icons.Rounded.Videocam
        mimeType == "application/pdf" -> Icons.Rounded.PictureAsPdf
        else -> Icons.Rounded.Description
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun AttachmentThumb(bytes: ByteArray?, mimeType: String, size: Int) {
    val thumb = remember(bytes) {
        bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    if (thumb != null) {
        Image(
            bitmap = thumb.asImageBitmap(),
            contentDescription = "پیش‌نمایش پیوست",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AttachmentIcon(mimeType, Modifier.size((size / 2).dp))
        }
    }
}

/**
 * Removable chips of the files about to be sent — used above the input bar of
 * all three sections. Images show a live thumbnail, other types a type icon.
 */
@Composable
fun AttachmentChipsRow(
    attachments: List<UserAttachment>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        itemsIndexed(attachments, key = { i, a -> "$i-${a.displayName}-${a.bytes.size}" }) { index, attachment ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    AttachmentThumb(
                        bytes = attachment.bytes.takeIf { attachment.isImage },
                        mimeType = attachment.mimeType,
                        size = 34,
                    )
                    Column(modifier = Modifier.width(120.dp)) {
                        Text(
                            text = attachment.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatFileSize(attachment.bytes.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "حذف ${attachment.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onRemove(index) },
                    )
                }
            }
        }
    }
}

/** Read-only chips of the files stored on a chat message bubble. */
@Composable
fun ChatAttachmentChips(
    attachments: List<ChatAttachmentMeta>,
    readImage: (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        attachments.forEach { meta ->
            val bytes = if (meta.mimeType.startsWith("image/")) readImage(meta.fileName) else null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                if (meta.mimeType.startsWith("image/")) {
                    AttachmentThumb(bytes, meta.mimeType, size = 32)
                } else {
                    AttachmentIcon(meta.mimeType, Modifier.size(20.dp))
                }
                Text(
                    text = meta.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The «+» of every section: opens the system picker (any type, multi-select,
 * no count limit) and maps the picked URIs into [UserAttachment]s — skipping
 * unreadable/oversized files with a Persian notice.
 */
@Composable
fun rememberAttachmentPicker(
    onPicked: (List<UserAttachment>) -> Unit,
    onNotice: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val picked = mutableListOf<UserAttachment>()
        uris.forEach { uri ->
            runCatching {
                val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "فایل"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) return@runCatching
                if (bytes.size > UserAttachment.MAX_FILE_BYTES) {
                    onNotice("«$name» بزرگ‌تر از ۱۵ مگابایت است و ارسال نشد.")
                    return@runCatching
                }
                val mime = context.contentResolver.getType(uri)
                    ?: UserAttachment.guessMime(name)
                picked += UserAttachment(displayName = name, mimeType = mime, bytes = bytes)
            }.onFailure {
                onNotice("خواندن یکی از فایل‌ها ممکن نشد.")
            }
        }
        if (picked.isNotEmpty()) onPicked(picked)
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/** The real file name behind a content URI, when the provider exposes it. */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
}.getOrNull()
