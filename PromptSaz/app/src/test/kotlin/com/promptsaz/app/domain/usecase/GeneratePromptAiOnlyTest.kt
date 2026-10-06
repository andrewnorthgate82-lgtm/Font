package com.promptsaz.app.domain.usecase

import com.promptsaz.app.domain.engine.PromptEngine
import com.promptsaz.app.domain.engine.assembler.PromptAssembler
import com.promptsaz.app.domain.engine.clarify.ClarificationEngine
import com.promptsaz.app.domain.engine.score.QualityScorer
import com.promptsaz.app.domain.engine.improve.PromptAnalyzer
import com.promptsaz.app.domain.engine.variant.VariantGenerator
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.provider.PromptProvider
import com.promptsaz.app.domain.provider.PromptRefinement
import com.promptsaz.app.domain.provider.ProviderGeneration
import com.promptsaz.app.domain.provider.ProviderHealth
import com.promptsaz.app.domain.provider.ProviderRegistry
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.repository.PromptRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The «بهبود ساختار» / «دوباره ساخت» bug: when the model call failed (e.g.
 * the key's quota ran out), the app REPLACED the good AI prompt with a weaker
 * offline rewrite — and the user lost the AI result. regenerateAiOnly must
 * never downgrade: on failure nothing is saved and the Persian reason is
 * returned; on success the new AI prompt is saved.
 */
class GeneratePromptAiOnlyTest {

    // ---------------------------------------------------------------- fakes --

    private class FakeSettingsRepository(initial: AppSettings) : SettingsRepository {
        private val flow = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = flow
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDefaultDomain(domainId: String) {}
        override suspend fun setDefaultTarget(target: TargetAi) {}
        override suspend fun setDefaultLanguage(language: OutputLanguage) {}
        override suspend fun setDefaultDetail(level: DetailLevel) {}
        override suspend fun setAiEnabled(enabled: Boolean) {}
        override suspend fun setAiBaseUrl(url: String) {}
        override suspend fun setAiModel(model: String) {}
        override suspend fun setAiImageModel(model: String) {}
        override suspend fun addService(name: String, baseUrl: String, type: String): String = "svc-new"
        override suspend fun updateService(id: String, name: String, baseUrl: String, type: String) {}
        override suspend fun removeService(id: String) {}
        override suspend fun setActiveService(id: String) {}
        override suspend fun setModeService(modeId: String, serviceId: String) {}
        override suspend fun setServiceModel(serviceId: String, model: String, imageModel: Boolean) {}
    }

    private class CountingPromptRepository : PromptRepository {
        var savedCount = 0
        override fun observePrompts(
            query: String,
            domainId: String?,
            favoritesOnly: Boolean,
        ): Flow<List<ArchivedPrompt>> = MutableStateFlow(emptyList())
        override fun observePrompt(id: Long): Flow<ArchivedPrompt?> = MutableStateFlow(null)
        override fun observeCount(): Flow<Int> = MutableStateFlow(0)
        override suspend fun getPrompt(id: Long): ArchivedPrompt? = null
        override suspend fun getAll(): List<ArchivedPrompt> = emptyList()
        override suspend fun save(prompt: ArchivedPrompt): Long { savedCount++; return savedCount.toLong() }
        override suspend fun insertAll(prompts: List<ArchivedPrompt>) {}
        override suspend fun update(prompt: ArchivedPrompt) {}
        override suspend fun delete(id: Long) {}
        override suspend fun toggleFavorite(id: Long) {}
        override suspend fun duplicate(id: Long): Long? = null
        override suspend fun allTags(): List<String> = emptyList()
    }

    private class FakeKbRepository : KbRepository {
        override suspend fun getRegistry() = throw UnsupportedOperationException()
        override fun registryFlow(): Flow<Nothing> = emptyFlow()
        override suspend fun getReadyDomains() = emptyList<com.promptsaz.app.domain.model.KbDomainEntry>()
        override suspend fun getDomain(domainId: String): DomainKnowledge? = null
    }

    /** Provider whose generatePrompt fails (quota) or succeeds on demand. */
    private class FakeProvider(
        private val quotaMessageFa: String? = null,
    ) : PromptProvider {
        override val id = "openai_compatible"
        override val displayNameFa = "تست"
        var generateCalls = 0
        var lastRefinement: PromptRefinement? = null
        override suspend fun isConfigured(serviceId: String?): Boolean = true
        override suspend fun testConnection(serviceId: String?): ProviderHealth = ProviderHealth.Ok()
        override suspend fun listModels(serviceId: String?): Result<List<String>> = Result.success(emptyList())
        override suspend fun generatePrompt(
            spec: PromptSpec,
            kb: DomainKnowledge?,
            serviceId: String?,
            refinement: PromptRefinement?,
        ): Result<ProviderGeneration> {
            generateCalls++
            lastRefinement = refinement
            return if (quotaMessageFa != null) {
                Result.failure(IllegalStateException(quotaMessageFa))
            } else {
                Result.success(ProviderGeneration(title = "عنوان مدل", prompt = "پرامپت ساخته‌شده توسط مدل"))
            }
        }
    }

    private fun useCaseWith(provider: FakeProvider, repository: CountingPromptRepository): GeneratePromptUseCase =
        GeneratePromptUseCase(
            engine = PromptEngine(
                clarificationEngine = ClarificationEngine(),
                assembler = PromptAssembler(),
                scorer = QualityScorer(PromptAnalyzer()),
                variants = VariantGenerator(),
            ),
            scorer = QualityScorer(PromptAnalyzer()),
            kbRepository = FakeKbRepository(),
            promptRepository = repository,
            settingsRepository = FakeSettingsRepository(
                AppSettings(
                    aiEnabled = true,
                    aiServices = listOf(
                        AiService(
                            id = "s1",
                            name = "Gemini",
                            baseUrl = "https://example.com/v1beta",
                            type = AiService.TYPE_GEMINI,
                            model = "gemini-flash-latest",
                        ),
                    ),
                    promptServiceId = "s1",
                ),
            ),
            providerRegistry = ProviderRegistry(setOf(provider)),
        )

    private val spec = PromptSpec(
        idea = "یک پست اینستاگرام برای کافه",
        domainId = "general",
        targetAi = TargetAi.ANY,
        outputLanguage = OutputLanguage.SAME_AS_INPUT,
        detailLevel = DetailLevel.STANDARD,
    )

    // --------------------------------------------------------------- tests ---

    @Test
    fun `regenerateAiOnly keeps the current prompt and saves NOTHING on quota failure`() = runTest {
        val repository = CountingPromptRepository()
        val useCase = useCaseWith(FakeProvider(quotaMessageFa = "سهمیه یا نرخ درخواست‌ها تمام شده (کد ۴۲۹)"), repository)

        val regeneration = useCase.regenerateAiOnly(spec)

        assertTrue("must fail, not downgrade", regeneration is Regeneration.Failed)
        assertEquals("سهمیه یا نرخ درخواست‌ها تمام شده (کد ۴۲۹)", (regeneration as Regeneration.Failed).messageFa)
        assertEquals("nothing may be saved on failure", 0, repository.savedCount)
    }

    @Test
    fun `regenerateAiOnly saves the new AI prompt on success`() = runTest {
        val repository = CountingPromptRepository()
        val useCase = useCaseWith(FakeProvider(quotaMessageFa = null), repository)

        val regeneration = useCase.regenerateAiOnly(spec)

        assertTrue(regeneration is Regeneration.Saved)
        val saved = regeneration as Regeneration.Saved
        assertTrue("the saved prompt must be AI-generated", saved.result.isAiGenerated)
        assertEquals("پرامپت ساخته‌شده توسط مدل", saved.result.text)
        assertEquals(1, repository.savedCount)
    }

    @Test
    fun `regenerateAiOnly explains when AI mode is off`() = runTest {
        val repository = CountingPromptRepository()
        val provider = FakeProvider(quotaMessageFa = null)
        // aiEnabled = false — same constructor but flip the flag
        val useCase = GeneratePromptUseCase(
            engine = PromptEngine(
                clarificationEngine = ClarificationEngine(),
                assembler = PromptAssembler(),
                scorer = QualityScorer(PromptAnalyzer()),
                variants = VariantGenerator(),
            ),
            scorer = QualityScorer(PromptAnalyzer()),
            kbRepository = FakeKbRepository(),
            promptRepository = repository,
            settingsRepository = FakeSettingsRepository(AppSettings(aiEnabled = false)),
            providerRegistry = ProviderRegistry(setOf(provider)),
        )

        val regeneration = useCase.regenerateAiOnly(spec)

        assertTrue(regeneration is Regeneration.Failed)
        assertEquals("حالت هوش مصنوعی فعال نیست؛ در تنظیمات روشن کن.", (regeneration as Regeneration.Failed).messageFa)
        assertEquals(0, provider.generateCalls)
        assertEquals(0, repository.savedCount)
    }

    @Test
    fun `improve sends the previous prompt and the evaluator suggestions to the model`() = runTest {
        val repository = CountingPromptRepository()
        val provider = FakeProvider(quotaMessageFa = null)
        val useCase = useCaseWith(provider, repository)

        val refinement = PromptRefinement(
            previousPrompt = "پرامپت قبلی مدل",
            suggestionsFa = listOf("مخاطب هدف مشخص نشده", "نمونه خروجی ندارد"),
        )
        useCase.regenerateAiOnly(spec, refinement)

        assertEquals(refinement, provider.lastRefinement)
    }

    @Test
    fun `normal invoke still falls back offline when the model fails`() = runTest {
        val repository = CountingPromptRepository()
        val useCase = useCaseWith(FakeProvider(quotaMessageFa = "سهمیه تمام شده (۴۲۹)"), repository)

        val outcome = useCase(spec)

        // the classic flow keeps its graceful degradation…
        assertEquals(false, outcome.result.isAiGenerated)
        assertEquals("سهمیه تمام شده (۴۲۹)", outcome.aiErrorFa)
        assertEquals(1, repository.savedCount)
    }
}
