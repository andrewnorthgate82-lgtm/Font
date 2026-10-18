package com.font.localaipoc

import android.app.Activity
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val memorySampler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val peakPssKb = AtomicLong(0)
    private val preferences: SharedPreferences by lazy {
        getSharedPreferences("local_ai_poc", Context.MODE_PRIVATE)
    }

    @Volatile private var engine: Engine? = null
    @Volatile private var activeConversation: Conversation? = null
    private var loadedModel: ModelSpec? = null
    private var engineLoadMs: Long = 0
    private var isWorking = false
    private var isGenerating = false
    private var sampleTask: ScheduledFuture<*>? = null
    private var pendingDownloadId: Long = INVALID_DOWNLOAD_ID
    private var pendingDownloadModel: ModelSpec? = null
    private var selectedModel = ModelSpec.E2B
    private var selectedCase = PromptCatalog.cases.first()
    private val runRecords = mutableListOf<RunRecord>()

    private lateinit var modelSpinner: Spinner
    private lateinit var testSpinner: Spinner
    private lateinit var promptInput: EditText
    private lateinit var deviceInfoView: TextView
    private lateinit var modelStatusView: TextView
    private lateinit var operationStatusView: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var loadButton: Button
    private lateinit var unloadButton: Button
    private lateinit var deleteButton: Button
    private lateinit var generateButton: Button
    private lateinit var cancelButton: Button
    private lateinit var outputView: TextView
    private lateinit var checkView: TextView
    private lateinit var metricsView: TextView
    private lateinit var copyReportButton: Button

    private val downloadPoll = object : Runnable {
        override fun run() {
            pollDownload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245, 247, 246)
        window.navigationBarColor = Color.rgb(245, 247, 246)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        buildUi()
        bindActions()
        refreshDeviceInfo()
        restorePendingDownload()
        refreshControls()
        if (pendingDownloadId != INVALID_DOWNLOAD_ID) {
            mainHandler.post(downloadPoll)
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            fillViewport = true
            setBackgroundColor(Color.rgb(245, 247, 246))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        content.addView(makeText("آزمون هوش مصنوعی محلی", 25, true, Color.rgb(24, 53, 49)))
        content.addView(makeText(
            "نمونهٔ فنی برای سنجش تولید فارسی روی خود گوشی؛ بدون API مدل ابری و بدون انتشار خودکار.",
            15,
            false,
            Color.rgb(68, 83, 80),
        ))
        addGap(content, 10)
        deviceInfoView = makeText("در حال خواندن مشخصات دستگاه…", 13, false, Color.rgb(68, 83, 80))
        content.addView(deviceInfoView)

        addSectionTitle(content, "۱) مدل محلی")
        modelSpinner = makeSpinner(ModelSpec.values().map { it.title })
        content.addView(modelSpinner, matchWrap())
        modelStatusView = makeText("", 14, false, Color.rgb(68, 83, 80))
        content.addView(modelStatusView)
        downloadButton = makeButton("دانلود مدل", Color.rgb(20, 122, 114))
        content.addView(downloadButton, matchWrap())
        downloadProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        content.addView(downloadProgress, matchWrap())
        loadButton = makeButton("بارگذاری مدل", Color.rgb(20, 122, 114))
        content.addView(loadButton, matchWrap())
        unloadButton = makeButton("آزادکردن حافظهٔ مدل", Color.rgb(73, 91, 87))
        content.addView(unloadButton, matchWrap())
        deleteButton = makeButton("حذف فایل مدل", Color.rgb(145, 67, 53))
        content.addView(deleteButton, matchWrap())
        operationStatusView = makeText("", 14, false, Color.rgb(68, 83, 80))
        content.addView(operationStatusView)

        addSectionTitle(content, "۲) آزمون فارسی و پلتفرم")
        content.addView(makeText(
            "نسخهٔ نخست عمداً با CPU و ۴ رشته اجرا می‌شود تا مبنای قابل‌تکرار داشته باشیم. ابتدا E2B را اجرا کن؛ سپس می‌توانی E4B را مقایسه کنی.",
            14,
            false,
            Color.rgb(68, 83, 80),
        ))
        addGap(content, 4)
        testSpinner = makeSpinner(PromptCatalog.cases.map { it.title })
        content.addView(testSpinner, matchWrap())
        promptInput = EditText(this).apply {
            minLines = 6
            maxLines = 12
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextSize(15f)
            setTextColor(Color.rgb(29, 41, 39))
            setHintTextColor(Color.rgb(106, 118, 115))
            hint = "درخواست آزمایشی فارسی…"
            gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_RTL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        content.addView(promptInput, matchWrap())
        generateButton = makeButton("تولید پیش‌نویس روی گوشی", Color.rgb(20, 122, 114))
        content.addView(generateButton, matchWrap())
        cancelButton = makeButton("توقف آزمون", Color.rgb(145, 67, 53)).apply {
            visibility = View.GONE
        }
        content.addView(cancelButton, matchWrap())

        addSectionTitle(content, "خروجی مدل")
        outputView = makeText("پس از اجرای آزمون، پاسخ مدل اینجا دیده می‌شود.", 16, false, Color.rgb(29, 41, 39)).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        content.addView(outputView, matchWrap())
        checkView = makeText("", 14, true, Color.rgb(20, 122, 114))
        content.addView(checkView)

        addSectionTitle(content, "اندازه‌گیری‌ها")
        metricsView = makeText("هنوز آزمونی ثبت نشده است.", 14, false, Color.rgb(68, 83, 80))
        content.addView(metricsView)
        copyReportButton = makeButton("کپی گزارش CSV", Color.rgb(73, 91, 87))
        content.addView(copyReportButton, matchWrap())
        content.addView(makeText(
            "نکته: پس از دریافت فایل مدل، آزمون را یک بار در حالت پرواز اجرا کن تا اجرای آفلاین تأیید شود. عدد باتری درصدی و دما وضعیت حرارتی Android است؛ این‌ها ابزار آزمایشگاهی نیستند.",
            13,
            false,
            Color.rgb(89, 101, 98),
        ))
    }

    private fun bindActions() {
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedModel = ModelSpec.values()[position]
                refreshControls()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        testSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCase = PromptCatalog.cases[position]
                promptInput.setText(selectedCase.prompt)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        modelSpinner.setSelection(ModelSpec.E2B.ordinal)
        testSpinner.setSelection(0)

        downloadButton.setOnClickListener { confirmAndStartDownload() }
        loadButton.setOnClickListener { loadSelectedModel() }
        unloadButton.setOnClickListener { unloadModel() }
        deleteButton.setOnClickListener { confirmDeleteSelectedModel() }
        generateButton.setOnClickListener { runSelectedTest() }
        cancelButton.setOnClickListener { cancelInference() }
        copyReportButton.setOnClickListener { copyCsvReport() }
    }

    private fun refreshDeviceInfo() {
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val ramMb = memory.totalMem / (1024L * 1024L)
        val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        deviceInfoView.text = buildString {
            append("گوشی: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: $androidVersion  |  نسخهٔ ساخت: ${Build.DISPLAY}\n")
            append("RAM قابل‌مشاهده برای Android: ${ramMb} MB\n")
            append("RAM فیزیکی اعلام‌شده: ۱۲ GB؛ افزایش حافظهٔ HyperOS در این عدد حساب نشده است.")
        }
    }

    private fun refreshControls() {
        if (!::modelSpinner.isInitialized) return
        val file = try {
            selectedModel.file(this)
        } catch (_: Exception) {
            null
        }
        val modelReady = file != null && selectedModel.isComplete(file)
        val downloadActive = pendingDownloadId != INVALID_DOWNLOAD_ID

        modelStatusView.text = when {
            downloadActive && pendingDownloadModel == selectedModel ->
                "دریافت ${selectedModel.title} در حال انجام است؛ حجم فایل ${selectedModel.sizeLabel}."
            modelReady ->
                "مدل آمادهٔ محلی است: ${selectedModel.sizeLabel}."
            else ->
                "فایل مدل هنوز روی گوشی نیست. دریافت اولیه اینترنت می‌خواهد؛ بعد از آن، تولید محلی و آفلاین است."
        }
        downloadButton.text = if (modelReady) "مدل دریافت شده" else "دریافت ${selectedModel.title} (${selectedModel.sizeLabel})"
        downloadButton.isEnabled = !isWorking && !downloadActive && !modelReady
        loadButton.isEnabled = !isWorking && modelReady && loadedModel != selectedModel
        unloadButton.isEnabled = !isWorking && loadedModel != null
        deleteButton.isEnabled = !isWorking && modelReady && loadedModel != selectedModel && !downloadActive
        generateButton.isEnabled = !isWorking && engine != null && loadedModel == selectedModel
        cancelButton.visibility = if (isGenerating) View.VISIBLE else View.GONE
        modelSpinner.isEnabled = !isWorking && !downloadActive
        testSpinner.isEnabled = !isGenerating
        promptInput.isEnabled = !isGenerating
        copyReportButton.isEnabled = runRecords.isNotEmpty()
        if (!downloadActive) {
            downloadProgress.visibility = View.GONE
            downloadProgress.isIndeterminate = false
        }
    }

    private fun confirmAndStartDownload() {
        val spec = selectedModel
        val file = try {
            spec.file(this)
        } catch (error: Exception) {
            showError(error.message ?: "مسیر ذخیره‌سازی در دسترس نیست.")
            return
        }
        val parent = file.parentFile
        if (parent == null) {
            showError("مسیر ذخیره‌سازی مدل پیدا نشد.")
            return
        }
        parent.mkdirs()
        val availableBytes = try {
            StatFs(parent.absolutePath).availableBytes
        } catch (_: Exception) {
            0L
        }
        val requiredBytes = spec.expectedBytes + 350_000_000L
        if (availableBytes > 0L && availableBytes < requiredBytes) {
            showError("فضای خالی کافی نیست. برای این مدل و فایل‌های موقت حدود ${spec.sizeLabel} به‌علاوهٔ کمی فضای آزاد لازم است.")
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("دریافت فایل مدل")
            .setMessage(
                "${spec.title} با حجم ${spec.sizeLabel} از مخزن عمومی LiteRT دریافت می‌شود. " +
                    "اینترنت فقط برای همین دریافت اولیه است؛ پس از اتمام، آزمون تولید متن آفلاین اجرا می‌شود. بهتر است از Wi‑Fi استفاده کنی."
            )
            .setNegativeButton("انصراف", null)
            .setPositiveButton("شروع دریافت") { _, _ -> enqueueModelDownload(spec, file) }
            .show()
    }

    private fun enqueueModelDownload(spec: ModelSpec, file: File) {
        if (pendingDownloadId != INVALID_DOWNLOAD_ID) return
        try {
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
            val request = DownloadManager.Request(Uri.parse(spec.downloadUrl)).apply {
                setTitle("دریافت ${spec.title}")
                setDescription("فایل مدل محلی؛ ${spec.sizeLabel}")
                setMimeType("application/octet-stream")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
                setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, spec.fileName)
            }
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            pendingDownloadId = manager.enqueue(request)
            pendingDownloadModel = spec
            preferences.edit()
                .putLong(KEY_DOWNLOAD_ID, pendingDownloadId)
                .putString(KEY_DOWNLOAD_MODEL, spec.name)
                .apply()
            operationStatusView.text = "دریافت آغاز شد. می‌توانی از اعلان Android هم وضعیت را ببینی."
            downloadProgress.visibility = View.VISIBLE
            downloadProgress.isIndeterminate = true
            refreshControls()
            mainHandler.removeCallbacks(downloadPoll)
            mainHandler.post(downloadPoll)
        } catch (error: Exception) {
            clearPendingDownload()
            showError("شروع دریافت ناموفق بود: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun restorePendingDownload() {
        val savedId = preferences.getLong(KEY_DOWNLOAD_ID, INVALID_DOWNLOAD_ID)
        val savedModelName = preferences.getString(KEY_DOWNLOAD_MODEL, null)
        if (savedId <= 0L || savedModelName == null) return
        val spec = ModelSpec.values().firstOrNull { it.name == savedModelName } ?: return
        pendingDownloadId = savedId
        pendingDownloadModel = spec
        modelSpinner.setSelection(spec.ordinal)
        downloadProgress.visibility = View.VISIBLE
        downloadProgress.isIndeterminate = true
        operationStatusView.text = "در حال بازیابی وضعیت دریافت مدل…"
    }

    private fun pollDownload() {
        if (pendingDownloadId == INVALID_DOWNLOAD_ID || pendingDownloadModel == null) return
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(pendingDownloadId)
        val cursor = try {
            manager.query(query)
        } catch (error: Exception) {
            showError("خواندن وضعیت دریافت ناموفق بود: ${error.message ?: "خطای نامشخص"}")
            clearPendingDownload()
            refreshControls()
            return
        }

        cursor.use { result ->
            if (result == null || !result.moveToFirst()) {
                val spec = pendingDownloadModel
                val file = try { spec?.file(this) } catch (_: Exception) { null }
                if (spec != null && file != null && spec.isComplete(file)) {
                    completeDownload(spec, file)
                } else {
                    clearPendingDownload()
                    operationStatusView.text = "ردیف دریافت پیدا نشد؛ اگر فایل کامل نیست، دوباره دریافت را آغاز کن."
                    refreshControls()
                }
                return
            }

            val status = result.getInt(result.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = result.getInt(result.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val soFar = result.getLong(result.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = result.getLong(result.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            when (status) {
                DownloadManager.STATUS_PENDING -> {
                    operationStatusView.text = "در صف دریافت…"
                    setDownloadProgress(soFar, total)
                    mainHandler.postDelayed(downloadPoll, DOWNLOAD_POLL_MS)
                }
                DownloadManager.STATUS_RUNNING -> {
                    operationStatusView.text = if (total > 0L) {
                        "دریافت: ${percent(soFar, total)}٪ — ${formatBytes(soFar)} از ${formatBytes(total)}"
                    } else {
                        "دریافت فایل مدل در حال انجام است: ${formatBytes(soFar)}"
                    }
                    setDownloadProgress(soFar, total)
                    mainHandler.postDelayed(downloadPoll, DOWNLOAD_POLL_MS)
                }
                DownloadManager.STATUS_PAUSED -> {
                    operationStatusView.text = "دریافت موقتاً متوقف شد: ${downloadReason(reason)}"
                    setDownloadProgress(soFar, total)
                    mainHandler.postDelayed(downloadPoll, DOWNLOAD_POLL_MS)
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val spec = pendingDownloadModel ?: return
                    val file = try { spec.file(this) } catch (error: Exception) {
                        showError(error.message ?: "فایل مدل پیدا نشد.")
                        clearPendingDownload()
                        refreshControls()
                        return
                    }
                    completeDownload(spec, file)
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonText = downloadReason(reason)
                    clearPendingDownload()
                    downloadProgress.visibility = View.GONE
                    operationStatusView.text = "دریافت ناموفق بود: $reasonText"
                    refreshControls()
                }
                else -> {
                    operationStatusView.text = "وضعیت دریافت: $status"
                    mainHandler.postDelayed(downloadPoll, DOWNLOAD_POLL_MS)
                }
            }
        }
    }

    private fun completeDownload(spec: ModelSpec, file: File) {
        clearPendingDownload()
        downloadProgress.visibility = View.GONE
        if (!spec.isComplete(file)) {
            operationStatusView.text = "فایل دریافت‌شده ناقص یا کوچک‌تر از حد انتظار است (${formatBytes(file.length())}). دکمهٔ دریافت را دوباره بزن تا فایل ناقص جایگزین شود."
            refreshControls()
            return
        }
        operationStatusView.text = "دریافت کامل شد. اندازهٔ فایل: ${formatBytes(file.length())}. حالا مدل را بارگذاری کن."
        modelSpinner.setSelection(spec.ordinal)
        refreshControls()
    }

    private fun setDownloadProgress(soFar: Long, total: Long) {
        downloadProgress.visibility = View.VISIBLE
        if (total <= 0L) {
            downloadProgress.isIndeterminate = true
        } else {
            downloadProgress.isIndeterminate = false
            downloadProgress.progress = percent(soFar, total)
        }
    }

    private fun clearPendingDownload() {
        pendingDownloadId = INVALID_DOWNLOAD_ID
        pendingDownloadModel = null
        preferences.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_DOWNLOAD_MODEL).apply()
        mainHandler.removeCallbacks(downloadPoll)
    }

    private fun downloadReason(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "در انتظار اتصال اینترنت"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "در انتظار تلاش دوباره"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "در انتظار Wi‑Fi"
        DownloadManager.PAUSED_UNKNOWN -> "توقف نامشخص"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "فضای ذخیره‌سازی کافی نیست"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "حافظهٔ ذخیره‌سازی در دسترس نیست"
        DownloadManager.ERROR_CANNOT_RESUME -> "دریافت قابل ادامه نیست؛ دوباره تلاش کن"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "خطای دریافت از سرور مدل"
        else -> "کد خطا $reason"
    }

    private fun confirmDeleteSelectedModel() {
        val spec = selectedModel
        if (loadedModel == spec) {
            showError("ابتدا مدل را از حافظه آزاد کن.")
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("حذف فایل مدل")
            .setMessage("فایل ${spec.title} از حافظهٔ برنامه حذف شود؟ برای اجرای دوباره باید آن را مجدداً دریافت کنی.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("حذف") { _, _ ->
                try {
                    spec.file(this).delete()
                    operationStatusView.text = "فایل مدل حذف شد."
                } catch (error: Exception) {
                    showError("حذف فایل ناموفق بود: ${error.message ?: "خطای نامشخص"}")
                }
                refreshControls()
            }
            .show()
    }

    private fun loadSelectedModel() {
        if (isWorking) return
        val target = selectedModel
        val file = try {
            target.file(this)
        } catch (error: Exception) {
            showError(error.message ?: "مسیر فایل مدل در دسترس نیست.")
            return
        }
        if (!target.isComplete(file)) {
            showError("فایل مدل کامل نیست. ابتدا آن را دریافت کن.")
            refreshControls()
            return
        }

        isWorking = true
        operationStatusView.text = "در حال بارگذاری مدل روی CPU؛ ممکن است چند ثانیه طول بکشد…"
        startMemorySampling()
        refreshControls()
        worker.execute {
            val startMs = SystemClock.elapsedRealtime()
            try {
                val oldEngine = engine
                engine = null
                loadedModel = null
                if (oldEngine?.isInitialized() == true) oldEngine.close()

                val cacheDir = File(cacheDir, "litert_cache").apply { mkdirs() }
                val newEngine = createEngine(file, cacheDir)
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                engine = newEngine
                loadedModel = target
                engineLoadMs = elapsedMs
                val pssAfter = Debug.getPss()
                val pssPeak = peakPssKb.get().coerceAtLeast(pssAfter)
                runOnUiThread {
                    stopMemorySampling()
                    isWorking = false
                    operationStatusView.text =
                        "مدل آماده شد؛ بارگذاری ${elapsedMs / 1000.0}s، حافظهٔ PSS پس از بارگذاری ${formatKb(pssAfter)}، قلهٔ مشاهده‌شده ${formatKb(pssPeak)}."
                    refreshControls()
                }
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                runOnUiThread {
                    stopMemorySampling()
                    isWorking = false
                    operationStatusView.text = "بارگذاری مدل ناموفق بود: $message"
                    refreshControls()
                }
            }
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun createEngine(file: File, cacheDir: File): Engine {
        ExperimentalFlags.enableBenchmark = true
        val candidate = Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.CPU(threadCount = 4),
                maxNumTokens = MAX_CONTEXT_TOKENS,
                cacheDir = cacheDir.absolutePath,
            )
        )
        try {
            candidate.initialize()
            return candidate
        } catch (error: Throwable) {
            try {
                if (candidate.isInitialized()) candidate.close()
            } catch (_: Throwable) {
                // Preserve the original initialization failure.
            }
            throw error
        }
    }

    private fun unloadModel() {
        if (isWorking) return
        val oldEngine = engine
        if (oldEngine == null) {
            operationStatusView.text = "مدلی در حافظه بارگذاری نشده است."
            refreshControls()
            return
        }
        isWorking = true
        operationStatusView.text = "در حال آزادکردن حافظهٔ مدل…"
        refreshControls()
        engine = null
        loadedModel = null
        worker.execute {
            try {
                if (oldEngine.isInitialized()) oldEngine.close()
                runOnUiThread {
                    isWorking = false
                    operationStatusView.text = "مدل از حافظه آزاد شد؛ فایل مدل روی گوشی باقی ماند."
                    refreshControls()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    isWorking = false
                    operationStatusView.text = "آزادکردن مدل با خطا روبه‌رو شد: ${error.message ?: error.javaClass.simpleName}"
                    refreshControls()
                }
            }
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun runSelectedTest() {
        if (isWorking) return
        val engineForRun = engine
        val modelForRun = loadedModel
        if (engineForRun == null || modelForRun != selectedModel) {
            showError("ابتدا مدل انتخاب‌شده را بارگذاری کن.")
            return
        }
        val prompt = promptInput.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) {
            showError("متن آزمون خالی است.")
            return
        }
        val testCase = selectedCase
        val batteryBefore = readBatteryPercent()
        val thermalBefore = readThermalStatus()
        val pssBefore = Debug.getPss()
        startMemorySampling(pssBefore)
        isWorking = true
        isGenerating = true
        outputView.text = "در حال تولید محلی…"
        checkView.text = ""
        metricsView.text = "در حال اندازه‌گیری زمان، حافظه و وضعیت حرارتی…"
        operationStatusView.text = "در حال اجرای آزمون: ${testCase.title}"
        refreshControls()

        worker.execute {
            var conversation: Conversation? = null
            try {
                val config = ConversationConfig(
                    systemInstruction = Contents.of(PromptCatalog.SYSTEM_INSTRUCTION),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.7),
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                )
                conversation = engineForRun.createConversation(config)
                activeConversation = conversation
                val startedAt = SystemClock.elapsedRealtime()
                val response = conversation.sendMessage(prompt, maxOutputToken = MAX_OUTPUT_TOKENS)
                val wallTimeMs = SystemClock.elapsedRealtime() - startedAt
                val benchmark = conversation.getBenchmarkInfo()
                val pssAfter = Debug.getPss()
                val pssPeak = peakPssKb.get().coerceAtLeast(pssAfter)
                val output = response.toString()
                val result = InferenceResult(
                    model = modelForRun,
                    testCase = testCase,
                    output = output,
                    wallTimeMs = wallTimeMs,
                    benchmark = benchmark,
                    pssBeforeKb = pssBefore,
                    pssPeakKb = pssPeak,
                    pssAfterKb = pssAfter,
                    batteryBefore = batteryBefore,
                    thermalBefore = thermalBefore,
                    engineLoadMs = engineLoadMs,
                )
                runOnUiThread {
                    stopMemorySampling()
                    activeConversation = null
                    isWorking = false
                    isGenerating = false
                    showInferenceResult(result)
                    refreshControls()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    stopMemorySampling()
                    activeConversation = null
                    isWorking = false
                    isGenerating = false
                    outputView.text = ""
                    operationStatusView.text = "اجرای آزمون ناموفق بود: ${error.message ?: error.javaClass.simpleName}"
                    metricsView.text = "اگر خطا از کمبود حافظه یا backend بود، E2B و CPU را مبنا بگیر و E4B را جداگانه امتحان کن."
                    refreshControls()
                }
            } finally {
                try {
                    if (conversation?.isAlive == true) conversation.close()
                } catch (_: Throwable) {
                    // The model may already have closed the conversation after a native error.
                }
                activeConversation = null
            }
        }
    }

    private fun showInferenceResult(result: InferenceResult) {
        val batteryAfter = readBatteryPercent()
        val thermalAfter = readThermalStatus()
        val zwnjPass = PromptCatalog.checkExactPhrase(result.testCase, result.output)
        val record = RunRecord(
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()),
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}; ${Build.DISPLAY})",
            osReportedRamMb = reportedRamMb(),
            model = result.model.name,
            backend = "CPU-4",
            testId = result.testCase.id,
            wallTimeMs = result.wallTimeMs,
            engineLoadMs = result.engineLoadMs,
            timeToFirstTokenSec = result.benchmark.timeToFirstTokenInSecond,
            prefillTokens = result.benchmark.lastPrefillTokenCount,
            prefillTokensPerSec = result.benchmark.lastPrefillTokensPerSecond,
            decodeTokens = result.benchmark.lastDecodeTokenCount,
            decodeTokensPerSec = result.benchmark.lastDecodeTokensPerSecond,
            processPssBeforeKb = result.pssBeforeKb,
            processPssPeakKb = result.pssPeakKb,
            processPssAfterKb = result.pssAfterKb,
            thermalBefore = result.thermalBefore,
            thermalAfter = thermalAfter,
            batteryBeforePercent = result.batteryBefore,
            batteryAfterPercent = batteryAfter,
            zwnjCheckPassed = zwnjPass,
            output = result.output,
        )
        runRecords.add(record)
        outputView.text = result.output.ifBlank { "(مدل خروجی متنی برنگرداند.)" }
        checkView.text = when (zwnjPass) {
            true -> "آزمون نیم‌فاصله: متن دقیق مورد انتظار در خروجی حفظ شد."
            false -> "آزمون نیم‌فاصله: متن دقیقاً حفظ نشد؛ خروجی را بررسی کن."
            null -> "این مورد بررسی خودکار کیفیت ندارد؛ طبیعی‌بودن فارسی و تناسب با پلتفرم را دستی امتیاز بده."
        }
        metricsView.text = formatMetrics(record, result.benchmark)
        operationStatusView.text = "آزمون تمام شد و در گزارش محلی ثبت شد."
    }

    private fun formatMetrics(record: RunRecord, benchmark: BenchmarkInfo): String = buildString {
        append("مدل / backend: ${record.model} / ${record.backend}\n")
        append("بارگذاری مدل: ${record.engineLoadMs} ms\n")
        append("زمان تا نخستین توکن (TTFT): ${formatSeconds(record.timeToFirstTokenSec)}\n")
        append("سرعت پیش‌پردازش: ${record.prefillTokensPerSec.csvNumber()} توکن/ثانیه (${record.prefillTokens} توکن)\n")
        append("سرعت تولید: ${record.decodeTokensPerSec.csvNumber()} توکن/ثانیه (${record.decodeTokens} توکن)\n")
        append("زمان کامل این درخواست: ${record.wallTimeMs} ms\n")
        append("حافظهٔ فرایند PSS پیش/قله/پس: ${formatKb(record.processPssBeforeKb)} / ${formatKb(record.processPssPeakKb)} / ${formatKb(record.processPssAfterKb)}\n")
        append("وضعیت حرارتی Android پیش/پس: ${record.thermalBefore} / ${record.thermalAfter}\n")
        append("باتری پیش/پس: ${record.batteryBeforePercent?.let { "$it٪" } ?: "ناموجود"} / ${record.batteryAfterPercent?.let { "$it٪" } ?: "ناموجود"}\n")
        append("اطلاعات runtime: زمان آغاز ثبت‌شده توسط LiteRT-LM ${formatSeconds(benchmark.initTimeInSecond)}")
    }

    private fun cancelInference() {
        val conversation = activeConversation
        if (conversation == null) {
            operationStatusView.text = "درخواست توقف ارسال شد؛ کمی صبر کن."
            return
        }
        try {
            conversation.cancelProcess()
            operationStatusView.text = "درخواست توقف ارسال شد…"
        } catch (error: Throwable) {
            operationStatusView.text = "توقف انجام نشد: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun copyCsvReport() {
        if (runRecords.isEmpty()) {
            showError("هنوز نتیجه‌ای برای گزارش وجود ندارد.")
            return
        }
        val csv = buildString {
            append(RunRecord.csvHeader)
            append('\n')
            runRecords.forEachIndexed { index, record ->
                if (index > 0) append('\n')
                append(record.toCsvRow())
            }
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("local-ai-poc.csv", csv))
        Toast.makeText(this, "گزارش ${runRecords.size} آزمون در کلیپ‌بورد کپی شد.", Toast.LENGTH_LONG).show()
    }

    private fun startMemorySampling(initialPssKb: Long = Debug.getPss()) {
        stopMemorySampling()
        peakPssKb.set(initialPssKb)
        sampleTask = memorySampler.scheduleAtFixedRate({
            try {
                val current = Debug.getPss()
                peakPssKb.accumulateAndGet(current) { previous, next -> maxOf(previous, next) }
            } catch (_: Throwable) {
                // Sampling is best-effort; inference itself should continue.
            }
        }, 0, 500, TimeUnit.MILLISECONDS)
    }

    private fun stopMemorySampling() {
        sampleTask?.cancel(false)
        sampleTask = null
    }

    private fun readBatteryPercent(): Int? {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100f / scale).toInt()
    }

    private fun readThermalStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "ناموجود"
        val power = getSystemService(POWER_SERVICE) as PowerManager
        return when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "عادی"
            PowerManager.THERMAL_STATUS_LIGHT -> "کمی گرم"
            PowerManager.THERMAL_STATUS_MODERATE -> "متوسط"
            PowerManager.THERMAL_STATUS_SEVERE -> "زیاد"
            PowerManager.THERMAL_STATUS_CRITICAL -> "بحرانی"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "اضطراری"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "خاموشی حرارتی"
            else -> "نامشخص"
        }
    }

    private fun reportedRamMb(): Long {
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        return memory.totalMem / (1024L * 1024L)
    }

    private fun makeSpinner(items: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun makeText(
        value: String,
        sizeSp: Int,
        bold: Boolean,
        color: Int,
    ): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        textDirection = View.TEXT_DIRECTION_RTL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        gravity = Gravity.START
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun makeButton(value: String, color: Int): Button = Button(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(color)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        isAllCaps = false
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun addSectionTitle(parent: LinearLayout, value: String) {
        addGap(parent, 12)
        parent.addView(makeText(value, 19, true, Color.rgb(24, 53, 49)))
    }

    private fun addGap(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun percent(current: Long, total: Long): Int =
        if (total <= 0L) 0 else ((current.coerceAtLeast(0L) * 100L) / total).toInt().coerceIn(0, 100)

    private fun formatBytes(bytes: Long): String = String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)

    private fun formatKb(kb: Long): String = String.format(Locale.US, "%.0f MB", kb / 1024.0)

    private fun formatSeconds(seconds: Double): String = String.format(Locale.US, "%.2f s", seconds)

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(downloadPoll)
        stopMemorySampling()
        try {
            activeConversation?.cancelProcess()
        } catch (_: Throwable) {
            // The conversation may already be finished.
        }
        val oldEngine = engine
        engine = null
        worker.execute {
            try {
                val conversation = activeConversation
                if (conversation?.isAlive == true) conversation.close()
            } catch (_: Throwable) {
                // Best-effort cleanup during Activity teardown.
            }
            try {
                if (oldEngine?.isInitialized() == true) oldEngine.close()
            } catch (_: Throwable) {
                // Best-effort cleanup during Activity teardown.
            }
        }
        worker.shutdown()
        memorySampler.shutdownNow()
        super.onDestroy()
    }

    private data class InferenceResult(
        val model: ModelSpec,
        val testCase: PromptCase,
        val output: String,
        val wallTimeMs: Long,
        val benchmark: BenchmarkInfo,
        val pssBeforeKb: Long,
        val pssPeakKb: Long,
        val pssAfterKb: Long,
        val batteryBefore: Int?,
        val thermalBefore: String,
        val engineLoadMs: Long,
    )

    companion object {
        private const val INVALID_DOWNLOAD_ID = -1L
        private const val DOWNLOAD_POLL_MS = 750L
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_DOWNLOAD_MODEL = "download_model"
        private const val MAX_CONTEXT_TOKENS = 2048
        private const val MAX_OUTPUT_TOKENS = 256
    }
}
