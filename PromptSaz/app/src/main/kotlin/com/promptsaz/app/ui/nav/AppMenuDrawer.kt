package com.promptsaz.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ImageGeneration
import com.promptsaz.app.ui.theme.BrandGradient
import com.promptsaz.app.util.TimeAgo

/**
 * The unified app menu (replaces the bottom bar): گفتگو / تولید پرامپت /
 * تولید تصویر / تنظیمات / درباره ما. Tapping a section opens its tab and
 * reveals its ۵ تاریخچهٔ اخیر — tapping a history item jumps straight into
 * that conversation / prompt / generated image.
 */
@Composable
fun AppMenuDrawer(
    conversations: List<ChatConversation>,
    prompts: List<ArchivedPrompt>,
    generations: List<ImageGeneration>,
    currentRoute: String?,
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

    Column(
        modifier = Modifier
            .fillMaxWidth(0.88f)
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
                        onClick = {
                            onOpenConversation(conversation.id)
                            onDismiss()
                        },
                    )
                }
                if (!chatShowsAll && conversations.size > 5) {
                    MenuMoreButton { chatShowsAll = true }
                }
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
                        onClick = {
                            onOpenPrompt(prompt.id)
                            onDismiss()
                        },
                    )
                }
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
                        onClick = {
                            onOpenGeneration(generation.id)
                            onDismiss()
                        },
                    )
                }
                if (!imageShowsAll && generations.size > 5) {
                    MenuMoreButton { imageShowsAll = true }
                }
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
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(springMenu()) + fadeIn(tween(180)),
        exit = shrinkVertically(springMenu()) + fadeOut(tween(140)),
    ) {
        Column(modifier = Modifier.padding(start = 6.dp)) {
            content()
        }
    }
}

/** Gentle spring for the menu's expand/collapse — ظریف و نرم. */
private fun springMenu() = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** One history entry under a section. */
@Composable
private fun MenuHistoryItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        )
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
