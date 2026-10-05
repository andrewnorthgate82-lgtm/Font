package com.promptsaz.app.ui.nav

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ImageGeneration
import com.promptsaz.app.ui.theme.BrandGradient
import com.promptsaz.app.util.PlatformUtils
import com.promptsaz.app.util.TimeAgo
import java.io.File

/**
 * The unified app menu (replaces the bottom bar): گفتگو / تولید پرامپت /
 * تولید تصویر / تنظیمات / درباره ما. Each section reveals its ۵ تاریخچهٔ
 * اخیر — with live THUMBNAILS in the تصویر section and a long-press
 * MULTI-SELECT mode for deleting / sharing several items at once.
 */
@Composable
fun AppMenuDrawer(
    conversations: List<ChatConversation>,
    prompts: List<ArchivedPrompt>,
    generations: List<ImageGeneration>,
    currentRoute: String?,
    viewModel: AppMenuViewModel,
    onOpenChat: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onOpenPromptTab: () -> Unit,
    onOpenPrompt: (Long) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenKb: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenGeneration: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expandedSection by rememberSaveable { mutableStateOf<String?>(null) }
    var chatShowsAll by rememberSaveable { mutableStateOf(false) }
    var imageShowsAll by rememberSaveable { mutableStateOf(false) }

    // --- multi-select state (per section, cleared when the drawer closes) ----
    var selectionSection by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val context = LocalContext.current

    fun toggleSelection(section: String, id: Long) {
        selectionSection = section
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun exitSelection() {
        selectionSection = null
        selectedIds = emptySet()
    }

    // ModalDrawerSheet = the opaque surface of the menu (fixes the
    // see-through drawer) + it pads its content below the status bar and
    // above the navigation bar by default (DrawerDefaults.windowInsets).
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.88f),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // --- brand header ------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 20.dp, bottom = 18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = "چیستا",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // --- sections with their recent history ---------------------------
        MenuSection(
            icon = Icons.Rounded.Forum,
            title = "گفتگو",
            active = currentRoute?.startsWith("chat") == true,
            expanded = expandedSection == SECTION_CHAT,
            onHeaderClick = {
                onOpenChat()
                expandedSection = if (expandedSection == SECTION_CHAT) null else SECTION_CHAT
            },
        ) {
            val list = if (chatShowsAll) conversations else conversations.take(5)
            if (list.isEmpty()) {
                MenuEmptyText("هنوز گفتگویی نداری")
            } else {
                list.forEach { conversation ->
                    MenuHistoryItem(
                        title = conversation.title,
                        subtitle = TimeAgo.format(conversation.updatedAt),
                        selected = selectionSection == SECTION_CHAT && conversation.id in selectedIds,
                        selectionMode = selectionSection == SECTION_CHAT,
                        onClick = {
                            if (selectionSection == SECTION_CHAT) {
                                toggleSelection(SECTION_CHAT, conversation.id)
                            } else {
                                onOpenConversation(conversation.id)
                                onDismiss()
                            }
                        },
                        onLongClick = { toggleSelection(SECTION_CHAT, conversation.id) },
                    )
                }
                if (!chatShowsAll && conversations.size > 5) {
                    MenuMoreButton { chatShowsAll = true }
                }
            }
            if (selectionSection == SECTION_CHAT && selectedIds.isNotEmpty()) {
                SelectionActionBar(
                    count = selectedIds.size,
                    shareEnabled = false, // sharing a chat needs its messages; delete-only here
                    onShare = {},
                    onDelete = {
                        viewModel.deleteConversations(selectedIds.toList())
                        exitSelection()
                    },
                    onCancel = { exitSelection() },
                )
            }
        }

        MenuSection(
            icon = Icons.Rounded.EditNote,
            title = "تولید پرامپت",
            active = currentRoute?.startsWith("home") == true,
            expanded = expandedSection == SECTION_PROMPT,
            onHeaderClick = {
                onOpenPromptTab()
                expandedSection = if (expandedSection == SECTION_PROMPT) null else SECTION_PROMPT
            },
        ) {
            if (prompts.isEmpty()) {
                MenuEmptyText("هنوز پرامپتی نساخته‌ای")
            } else {
                prompts.take(5).forEach { prompt ->
                    MenuHistoryItem(
                        title = prompt.title,
                        subtitle = TimeAgo.format(prompt.updatedAt),
                        selected = selectionSection == SECTION_PROMPT && prompt.id in selectedIds,
                        selectionMode = selectionSection == SECTION_PROMPT,
                        onClick = {
                            if (selectionSection == SECTION_PROMPT) {
                                toggleSelection(SECTION_PROMPT, prompt.id)
                            } else {
                                onOpenPrompt(prompt.id)
                                onDismiss()
                            }
                        },
                        onLongClick = { toggleSelection(SECTION_PROMPT, prompt.id) },
                    )
                }
            }
            if (selectionSection == SECTION_PROMPT && selectedIds.isNotEmpty()) {
                SelectionActionBar(
                    count = selectedIds.size,
                    shareEnabled = true,
                    onShare = {
                        val chosen = prompts.filter { it.id in selectedIds }
                        val text = chosen.joinToString("\n\n———\n\n") { p ->
                            "«${p.title}»\n${p.promptText}"
                        }
                        PlatformUtils.shareText(context, text)
                        exitSelection()
                    },
                    onDelete = {
                        viewModel.deletePrompts(selectedIds.toList())
                        exitSelection()
                    },
                    onCancel = { exitSelection() },
                )
            }
            MenuLink(icon = Icons.Rounded.Archive, label = "آرشیو کامل پرامپت‌ها", onClick = {
                onOpenArchive()
                onDismiss()
            })
            MenuLink(icon = Icons.Rounded.MenuBook, label = "دانش‌نامه حوزه‌ها", onClick = {
                onOpenKb()
                onDismiss()
            })
        }

        MenuSection(
            icon = Icons.Rounded.Image,
            title = "تولید تصویر",
            active = currentRoute?.startsWith("image") == true,
            expanded = expandedSection == SECTION_IMAGE,
            onHeaderClick = {
                onOpenImage()
                expandedSection = if (expandedSection == SECTION_IMAGE) null else SECTION_IMAGE
            },
        ) {
            val list = if (imageShowsAll) generations else generations.take(5)
            if (list.isEmpty()) {
                MenuEmptyText("هنوز تصویری نساخته‌ای")
            } else {
                list.forEach { generation ->
                    MenuHistoryItem(
                        title = generation.prompt,
                        subtitle = TimeAgo.format(generation.createdAt),
                        thumbnail = {
                            viewModel.readGenerationImage(generation.fileName)
                        },
                        selected = selectionSection == SECTION_IMAGE && generation.id in selectedIds,
                        selectionMode = selectionSection == SECTION_IMAGE,
                        onClick = {
                            if (selectionSection == SECTION_IMAGE) {
                                toggleSelection(SECTION_IMAGE, generation.id)
                            } else {
                                onOpenGeneration(generation.id)
                                onDismiss()
                            }
                        },
                        onLongClick = { toggleSelection(SECTION_IMAGE, generation.id) },
                    )
                }
                if (!imageShowsAll && generations.size > 5) {
                    MenuMoreButton { imageShowsAll = true }
                }
            }
            if (selectionSection == SECTION_IMAGE && selectedIds.isNotEmpty()) {
                SelectionActionBar(
                    count = selectedIds.size,
                    shareEnabled = true,
                    onShare = {
                        shareGenerations(context, viewModel, generations.filter { it.id in selectedIds })
                        exitSelection()
                    },
                    onDelete = {
                        viewModel.deleteGenerations(selectedIds.toList())
                        exitSelection()
                    },
                    onCancel = { exitSelection() },
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 12.dp),
        )

        // --- fixed items ---------------------------------------------------
        MenuLink(icon = Icons.Rounded.Settings, label = "تنظیمات", big = true, onClick = {
            onOpenSettings()
            onDismiss()
        })
        MenuLink(icon = Icons.Rounded.Info, label = "درباره ما", big = true, onClick = {
            onOpenAbout()
            onDismiss()
        })

        Spacer(Modifier.height(28.dp))
    }
    } // ModalDrawerSheet
}

private const val SECTION_CHAT = "chat"
private const val SECTION_PROMPT = "prompt"
private const val SECTION_IMAGE = "image"

/** A collapsible section header + its history items. */
@Composable
private fun MenuSection(
    icon: ImageVector,
    title: String,
    active: Boolean,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        onClick = onHeaderClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.padding(6.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "بستن" else "باز کردن",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (expanded) {
        Column(modifier = Modifier.padding(start = 6.dp)) {
            content()
        }
    }
}

/**
 * One history entry: long-press enters multi-select; in select mode the
 * leading slot becomes a check circle and every tap toggles. [thumbnail]
 * (used by the تصویر section) renders a live preview of the saved image.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuHistoryItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    thumbnail: (() -> ByteArray?)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // --- leading slot: thumbnail / check circle / plain dot ------------
        if (selectionMode) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = if (selected) "انتخاب‌شده" else "انتخاب نشده",
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(20.dp),
            )
        } else if (thumbnail != null) {
            val bitmap = remember(title) {
                thumbnail()?.let { bytes ->
                    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "پیش‌نمایش تصویر",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                ThumbPlaceholder()
            }
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ThumbPlaceholder() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The action bar of the multi-select mode: share / delete / cancel. */
@Composable
private fun SelectionActionBar(
    count: Int,
    shareEnabled: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = "${count.toPersianDigitsFa()} مورد انتخاب شد",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onShare, enabled = shareEnabled) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("اشتراک", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancel) { Text("لغو") }
            }
        }
    }
}

/** A small in-menu link (آرشیو، دانش‌نامه). */
@Composable
private fun MenuLink(icon: ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (big) 12.dp else 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (big) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(if (big) 22.dp else 18.dp),
        )
        Text(
            text = label,
            style = if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun MenuEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun MenuMoreButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 2.dp)) {
        Text("نمایش همه", style = MaterialTheme.typography.labelMedium)
    }
}

/** Shares several saved generations as one multi-image share sheet. */
private fun shareGenerations(
    context: android.content.Context,
    viewModel: AppMenuViewModel,
    selected: List<ImageGeneration>,
) {
    runCatching {
        val uris = ArrayList<Uri>()
        selected.forEach { generation ->
            val bytes = viewModel.readGenerationImage(generation.fileName) ?: return@forEach
            val file = File(context.cacheDir, "share-${generation.fileName}")
            file.writeBytes(bytes)
            uris += FileProvider.getUriForFile(context, context.packageName + ".files", file)
        }
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری تصاویر"))
    }
}

/** Small local helper so the action bar can show Persian digits. */
private fun Int.toPersianDigitsFa(): String {
    val digits = "۰۱۲۳۴۵۶۷۸۹"
    return toString().map { c -> if (c.isDigit()) digits[c - '0'] else c }.joinToString("")
}
