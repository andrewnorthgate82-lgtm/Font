package com.promptsaz.app.ui.screens.archive

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.data.export.ArchiveExportFile
import com.promptsaz.app.data.export.toArchived
import com.promptsaz.app.data.export.toExportDto
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.repository.PromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val promptRepository: PromptRepository,
    private val kbRepository: KbRepository,
    private val json: Json,
) : ViewModel() {

    data class Filters(
        val query: String = "",
        val domainId: String? = null,
        val favoritesOnly: Boolean = false,
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    private val _domains = MutableStateFlow<List<KbDomainEntry>>(emptyList())
    val domains: StateFlow<List<KbDomainEntry>> = _domains.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val prompts: StateFlow<List<ArchivedPrompt>> = _filters
        .flatMapLatest { f ->
            promptRepository.observePrompts(f.query, f.domainId, f.favoritesOnly)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _domains.value = runCatching { kbRepository.getReadyDomains() }.getOrDefault(emptyList())
        }
    }

    fun setQuery(query: String) = _filters.update { it.copy(query = query) }

    fun setDomain(domainId: String?) = _filters.update { it.copy(domainId = domainId) }

    fun toggleFavoritesOnly() = _filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun toggleFavorite(id: Long) = viewModelScope.launch {
        runCatching { promptRepository.toggleFavorite(id) }
            .onFailure { _message.value = "ذخیره علاقه‌مندی ناموفق بود" }
    }

    fun delete(id: Long) = viewModelScope.launch {
        runCatching { promptRepository.delete(id) }
            .onSuccess { _message.value = "پرامپت حذف شد" }
            .onFailure { _message.value = "حذف ناموفق بود؛ دوباره امتحان کن" }
    }

    fun duplicate(id: Long) = viewModelScope.launch {
        runCatching { promptRepository.duplicate(id) }
            .onSuccess { _message.value = "کپی ساخته شد" }
            .onFailure { _message.value = "ساخت کپی ناموفق بود" }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Serializes the whole archive and writes it to the SAF-picked file. */
    fun exportTo(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        runCatching {
            val all = promptRepository.getAll()
            val file = ArchiveExportFile(
                exportedAt = System.currentTimeMillis(),
                prompts = all.map { it.toEntity().toExportDto() },
            )
            val text = json.encodeToString(ArchiveExportFile.serializer(), file)
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("نوشتن فایل ناموفق بود")
            all.size
        }.onSuccess { count ->
            _message.value = "${count.toPersianDigitsSafe()} پرامپت خارج شد"
        }.onFailure { error ->
            _message.value = "خروجی گرفتن ناموفق بود: ${error.message ?: "خطای نامشخص"}"
        }
    }

    /** Reads a SAF-picked file and inserts its prompts (additive import). */
    fun importFrom(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("خواندن فایل ناموفق بود")
            val file = json.decodeFromString(ArchiveExportFile.serializer(), text)
            if (file.prompts.isEmpty()) error("فایل خالی است یا فرمت آن درست نیست")
            promptRepository.insertAll(file.prompts.map { it.toArchived() })
            file.prompts.size
        }.onSuccess { count ->
            _message.value = "${count.toPersianDigitsSafe()} پرامپت وارد شد"
        }.onFailure { error ->
            _message.value = "وارد کردن ناموفق بود: ${error.message ?: "فایل معتبر نیست"}"
        }
    }

    private fun Int.toPersianDigitsSafe(): String = com.promptsaz.app.util.toPersianDigits(this)
}
