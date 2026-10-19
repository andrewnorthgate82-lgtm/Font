package com.font.localimagepoc

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
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
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ImageGeneratorOptions
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val memorySampler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val peakPssKb = AtomicLong(0)
    private val preferences by lazy {
        getSharedPreferences("local_image_poc", Context.MODE_PRIVATE)
    }

    @Volatile private var imageGenerator: ImageGenerator? = null
    private var modelReady = false
    private var isWorking = false
    private var pendingDownloadId = INVALID_DOWNLOAD_ID
    private var sampleTask: ScheduledFuture<*>? = null
    private var lastBitmap: Bitmap? = null
    private var lastRecord: ImageRunRecord? = null

    private lateinit var deviceInfoView: TextView
    private lateinit var modelStatusView: TextView
    private lateinit var operationStatusView: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var prepareButton: Button
    private lateinit var deleteModelButton: Button
    private lateinit var promptInput: EditText
    private lateinit var stepsInput: EditText
    private lateinit var seedInput: EditText
    private lateinit var generateButton: Button
    private lateinit var unloadButton: Button
    private lateinit var imageView: ImageView
    private lateinit var metricsView: TextView
    private lateinit var saveImageButton: Button
    private lateinit var copyReportButton: Button

    private val downloadPoll = object : Runnable {
        override fun run() = pollDownload()
    }

    private val modelRoot: File
        get() = File(filesDir, MODEL_DIRECTORY)
    private val binsDirectory: File
        get() = File(modelRoot, "sd15_bins")
    private val completionMarker: File
        get() = File(modelRoot, ".complete")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245, 247, 246)
        window.navigationBarColor = Color.rgb(245, 247, 246)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        buildUi()
        modelReady = isModelReadyOnDisk()
        refreshDeviceInfo()
        restorePendingDownload()
        refreshControls()
        if (pendingDownloadId != INVALID_DOWNLOAD_ID) {
            mainHandler.post(downloadPoll)
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(245, 247, 246))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        content.addView(makeText("آزمون تولید تصویر آفلاین", 25, true, Color.rgb(24, 53, 49)))
        content.addView(makeText(
            "نمونهٔ فنی جداگانه با MediaPipe و Stable Diffusion 1.5؛ بعد از دریافت مدل، تولید روی خود گوشی انجام می‌شود.",
            15,
            false,
            Color.rgb(68, 83, 80),
        ))
        addGap(content, 8)
        content.addView(makeText(
            "این موتور آزمایشی و منسوخ‌شده است و هنوز روی Redmi تو آزموده نشده. APK و مدل جدا هستند؛ مدل حدود ۲٫۱ GB دانلود دارد و برای دانلود و استخراج، حدود ۵ GB فضای خالی لازم است.",
            13,
            false,
            Color.rgb(145, 67, 53),
        ))
        addGap(content, 8)
        deviceInfoView = makeText("در حال خواندن مشخصات دستگاه…", 13, false, Color.rgb(68, 83, 80))
        content.addView(deviceInfoView)

        addSectionTitle(content, "۱) آماده‌سازی مدل")
        modelStatusView = makeText("", 14, false, Color.rgb(68, 83, 80))
        content.addView(modelStatusView)
        downloadButton = makeButton("دانلود مدل روی Wi‑Fi", Color.rgb(20, 122, 114))
        content.addView(downloadButton, matchWrap())
        downloadProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        content.addView(downloadProgress, matchWrap())
        prepareButton = makeButton("بررسی و آماده‌سازی فایل دریافت‌شده", Color.rgb(73, 91, 87)).apply {
            visibility = View.GONE
        }
        content.addView(prepareButton, matchWrap())
        deleteModelButton = makeButton("حذف فایل‌های مدل", Color.rgb(145, 67, 53))
        content.addView(deleteModelButton, matchWrap())
        operationStatusView = makeText("", 14, false, Color.rgb(68, 83, 80))
        content.addView(operationStatusView)

        addSectionTitle(content, "۲) درخواست تصویر")
        content.addView(makeText(
            "برای اولین آزمون از همان توضیح قبلی استفاده کن. تصویر ۵۱۲×۵۱۲ است؛ شروع با ۱۲ گام فقط برای سنجش اولیه است، نه تنظیم کیفیت نهایی.",
            14,
            false,
            Color.rgb(68, 83, 80),
        ))
        promptInput = EditText(this).apply {
            minLines = 5
            maxLines = 10
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextSize(15f)
            setTextColor(Color.rgb(29, 41, 39))
            setHintTextColor(Color.rgb(106, 118, 115))
            hint = "Describe the image in English…"
            setText(DEFAULT_PROMPT)
            gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        content.addView(promptInput, matchWrap())

        val parameterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        parameterRow.addView(makeText("Steps", 14, true, Color.rgb(68, 83, 80)), LinearLayout.LayoutParams(0, dp(48), 1f))
        stepsInput = makeSmallInput("12", 2)
        parameterRow.addView(stepsInput, LinearLayout.LayoutParams(dp(78), dp(48)))
        parameterRow.addView(makeText("Seed", 14, true, Color.rgb(68, 83, 80)).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(72), dp(48)))
        seedInput = makeSmallInput("42", 10)
        parameterRow.addView(seedInput, LinearLayout.LayoutParams(dp(96), dp(48)))
        content.addView(parameterRow, matchWrap())

        generateButton = makeButton("تولید یک تصویر روی گوشی", Color.rgb(20, 122, 114))
        content.addView(generateButton, matchWrap())
        unloadButton = makeButton("آزادکردن حافظهٔ مدل", Color.rgb(73, 91, 87))
        content.addView(unloadButton, matchWrap())

        addSectionTitle(content, "خروجی")
        imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            minimumHeight = dp(200)
            contentDescription = "Generated image preview"
        }
        content.addView(imageView, matchWrap())
        saveImageButton = makeButton("ذخیرهٔ تصویر در گالری", Color.rgb(73, 91, 87)).apply {
            isEnabled = false
        }
        content.addView(saveImageButton, matchWrap())

        addSectionTitle(content, "اندازه‌گیری‌های همین گوشی")
        metricsView = makeText("هنوز تصویری تولید نشده است.", 14, false, Color.rgb(68, 83, 80))
        content.addView(metricsView)
        copyReportButton = makeButton("کپی گزارش آزمون", Color.rgb(73, 91, 87)).apply {
            isEnabled = false
        }
        content.addView(copyReportButton, matchWrap())
        content.addView(makeText(
            "پس از دانلود و آماده‌سازی مدل، حالت پرواز را روشن کن و بعد تولید را بزن. زمان اجرا، PSS و وضعیت حرارتی اینجا از خود گوشی خوانده می‌شود؛ هیچ معیار سرعتی از گوشی دیگر به Redmi تعمیم داده نمی‌شود.",
            13,
            false,
            Color.rgb(89, 101, 98),
        ))

        downloadButton.setOnClickListener { confirmAndStartDownload() }
        prepareButton.setOnClickListener { prepareModelFromArchive() }
        deleteModelButton.setOnClickListener { confirmDeleteModel() }
        generateButton.setOnClickListener { runGeneration() }
        unloadButton.setOnClickListener { unloadGenerator() }
        saveImageButton.setOnClickListener { saveImageToGallery() }
        copyReportButton.setOnClickListener { copyReport() }
    }

    private fun refreshDeviceInfo() {
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val ramMb = memory.totalMem / (1024L * 1024L)
        deviceInfoView.text = buildString {
            append("گوشی: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("RAM قابل‌مشاهده برای Android: ${ramMb} MB؛ افزایش حافظهٔ مجازی در RAM فیزیکی حساب نمی‌شود.")
        }
    }

    private fun refreshControls() {
        if (!::downloadButton.isInitialized) return
        val downloading = pendingDownloadId != INVALID_DOWNLOAD_ID
        val archiveExists = archiveFile().let { it.isFile && it.length() >= MIN_ARCHIVE_BYTES }
        modelStatusView.text = when {
            modelReady -> "مدل آماده است؛ اجرای تولید بعد از دریافت، آفلاین انجام می‌شود."
            downloading -> "دریافت مدل بزرگ در جریان است؛ برای ادامه از اعلان Android هم می‌توانی وضعیت را ببینی."
            archiveExists -> "فایل ZIP دریافت شده؛ باید بررسی و از حالت فشرده خارج شود."
            else -> "مدل هنوز روی گوشی نیست. دریافت اولیه فقط با Wi‑Fi انجام می‌شود."
        }
        downloadButton.isEnabled = !isWorking && !downloading && !modelReady && !archiveExists
        prepareButton.visibility = if (!modelReady && archiveExists && !downloading) View.VISIBLE else View.GONE
        prepareButton.isEnabled = !isWorking && archiveExists
        deleteModelButton.isEnabled = !isWorking && !downloading && (modelReady || archiveExists || modelRoot.exists())
        generateButton.isEnabled = !isWorking && modelReady
        unloadButton.isEnabled = !isWorking && imageGenerator != null
        promptInput.isEnabled = !isWorking
        stepsInput.isEnabled = !isWorking
        seedInput.isEnabled = !isWorking
        saveImageButton.isEnabled = !isWorking && lastBitmap != null
        copyReportButton.isEnabled = !isWorking && lastRecord != null
        if (!downloading) {
            downloadProgress.visibility = View.GONE
            downloadProgress.isIndeterminate = false
        }
    }

    private fun confirmAndStartDownload() {
        if (pendingDownloadId != INVALID_DOWNLOAD_ID || isWorking) return
        val archive = archiveFile()
        val parent = archive.parentFile
        if (parent == null) {
            showError("مسیر ذخیره‌سازی فایل مدل در دسترس نیست.")
            return
        }
        parent.mkdirs()
        val freeBytes = try {
            StatFs(parent.absolutePath).availableBytes
        } catch (_: Exception) {
            0L
        }
        if (freeBytes > 0L && freeBytes < MIN_FREE_BYTES) {
            showError("برای دریافت و استخراج امن این مدل حدود ۵ GB فضای خالی لازم است. فضای خالی فعلی: ${formatBytes(freeBytes)}")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("دریافت مدل تصویر")
            .setMessage(
                "مدل تبدیل‌شدهٔ Stable Diffusion 1.5 حدود ۲٫۱ GB است. دانلود فقط روی Wi‑Fi مجاز است؛ برای فایل فشرده و نسخهٔ استخراج‌شده موقتاً حدود ۵ GB فضای خالی لازم است. پس از آماده‌شدن مدل می‌توانی حالت پرواز را روشن کنی."
            )
            .setNegativeButton("انصراف", null)
            .setPositiveButton("شروع دانلود") { _, _ -> enqueueModelDownload(archive) }
            .show()
    }

    private fun enqueueModelDownload(archive: File) {
        try {
            archive.parentFile?.mkdirs()
            if (archive.exists()) archive.delete()
            val request = DownloadManager.Request(Uri.parse(MODEL_DOWNLOAD_URL)).apply {
                setTitle("دریافت مدل تصویر محلی")
                setDescription("Stable Diffusion 1.5 برای اجرای آفلاین؛ حدود ۲٫۱ GB")
                setMimeType("application/zip")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(false)
                setAllowedOverRoaming(false)
                setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, MODEL_ARCHIVE_NAME)
            }
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            pendingDownloadId = manager.enqueue(request)
            preferences.edit().putLong(KEY_DOWNLOAD_ID, pendingDownloadId).apply()
            operationStatusView.text = "دانلود شروع شد. Android ممکن است آن را تا اتصال به Wi‑Fi در صف نگه دارد."
            downloadProgress.visibility = View.VISIBLE
            downloadProgress.isIndeterminate = true
            refreshControls()
            mainHandler.removeCallbacks(downloadPoll)
            mainHandler.post(downloadPoll)
        } catch (error: Exception) {
            clearPendingDownload()
            showError("شروع دانلود ممکن نشد: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun restorePendingDownload() {
        val savedId = preferences.getLong(KEY_DOWNLOAD_ID, INVALID_DOWNLOAD_ID)
        if (savedId <= 0L) return
        pendingDownloadId = savedId
        operationStatusView.text = "در حال بازیابی وضعیت دانلود مدل…"
        downloadProgress.visibility = View.VISIBLE
        downloadProgress.isIndeterminate = true
    }

    private fun pollDownload() {
        if (pendingDownloadId == INVALID_DOWNLOAD_ID) return
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = try {
            manager.query(DownloadManager.Query().setFilterById(pendingDownloadId))
        } catch (error: Exception) {
            clearPendingDownload()
            operationStatusView.text = "خواندن وضعیت دانلود ممکن نشد: ${error.message ?: "خطای نامشخص"}"
            refreshControls()
            return
        }
        cursor.use { result ->
            if (!result.moveToFirst()) {
                val archive = archiveFile()
                clearPendingDownload()
                if (archive.isFile && archive.length() >= MIN_ARCHIVE_BYTES) {
                    operationStatusView.text = "فایل دریافت‌شده پیدا شد؛ آماده‌سازی را ادامه بده."
                } else {
                    operationStatusView.text = "ردیف دانلود پیدا نشد و فایل کامل نیست؛ دانلود را دوباره آغاز کن."
                }
                refreshControls()
                return
            }
            val status = result.getInt(result.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = result.getInt(result.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val soFar = result.getLong(result.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = result.getLong(result.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            when (status) {
                DownloadManager.STATUS_PENDING -> {
                    operationStatusView.text = "در صف دانلود؛ Wi‑Fi را روشن نگه دار."
                    setDownloadProgress(soFar, total)
                    mainHandler.postDelayed(downloadPoll, DOWNLOAD_POLL_MS)
                }
                DownloadManager.STATUS_RUNNING -> {
                    operationStatusView.text = if (total > 0L) {
                        "دانلود: ${percent(soFar, total)}٪ — ${formatBytes(soFar)} از ${formatBytes(total)}"
                    } else {
                        "دانلود مدل در جریان است: ${formatBytes(soFar)}"
                    }
                    setDownloadProgress(soFar, total)
                    mainHandler.postDelayed(downloadPoll, DOWNLOAD_POLL_MS)
                }
                DownloadManager.STATUS_PAUSED -> {
                    operationStatusView.text = "دانلود موقتاً متوقف است: ${downloadReason(reason)}"
                    setDownloadProgress(soFar, total)
                    mainHandler.postDelayed(downloadPoll, DOWNLOAD_POLL_MS)
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    clearPendingDownload()
                    operationStatusView.text = "دانلود کامل شد؛ اکنون صحت فایل و ساختار مدل بررسی می‌شود."
                    refreshControls()
                    prepareModelFromArchive()
                }
                DownloadManager.STATUS_FAILED -> {
                    clearPendingDownload()
                    operationStatusView.text = "دانلود ناموفق بود: ${downloadReason(reason)}"
                    refreshControls()
                }
                else -> {
                    operationStatusView.text = "وضعیت دانلود: $status"
                    mainHandler.postDelayed(downloadPoll, DOWNLOAD_POLL_MS)
                }
            }
        }
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
        preferences.edit().remove(KEY_DOWNLOAD_ID).apply()
        mainHandler.removeCallbacks(downloadPoll)
    }

    private fun downloadReason(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "در انتظار Wi‑Fi یا شبکهٔ مجاز"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "در انتظار تلاش دوبارهٔ Android"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "در انتظار Wi‑Fi"
        DownloadManager.PAUSED_UNKNOWN -> "توقف نامشخص"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "فضای ذخیره‌سازی کافی نیست"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "حافظهٔ مقصد در دسترس نیست"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "پاسخ دانلود از مخزن پذیرفته نشد"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "انتقال فایل از مخزن ناقص شد"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "مخزن دانلود بیش از حد تغییرمسیر داد"
        else -> "کد خطای $reason"
    }

    private fun prepareModelFromArchive() {
        if (isWorking) return
        val archive = archiveFile()
        if (!archive.isFile || archive.length() < MIN_ARCHIVE_BYTES) {
            showError("فایل مدل کامل نیست؛ دانلود را دوباره انجام بده.")
            refreshControls()
            return
        }
        isWorking = true
        operationStatusView.text = "در حال بررسی SHA‑256 فایل بزرگ؛ لطفاً اپ را باز نگه دار…"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refreshControls()
        worker.execute {
            try {
                val actualHash = sha256(archive) { bytesRead ->
                    if (bytesRead % HASH_STATUS_INTERVAL < HASH_BUFFER_SIZE) {
                        postStatus("بررسی صحت فایل: ${formatBytes(bytesRead)} از ${formatBytes(archive.length())}…")
                    }
                }
                if (!actualHash.equals(EXPECTED_MODEL_SHA256, ignoreCase = true)) {
                    archive.delete()
                    throw IOException("اثر انگشت فایل با نسخهٔ رسمی مدل مطابقت ندارد؛ فایل ناقص حذف شد.")
                }
                postStatus("صحت فایل تأیید شد؛ در حال خارج‌کردن مدل از حالت فشرده…")
                modelRoot.deleteRecursively()
                modelRoot.mkdirs()
                extractModelArchive(archive, modelRoot) { extractedBytes ->
                    if (extractedBytes % EXTRACT_STATUS_INTERVAL < EXTRACT_BUFFER_SIZE) {
                        postStatus("آماده‌سازی مدل: ${formatBytes(extractedBytes)} استخراج شد…")
                    }
                }
                val vocab = File(binsDirectory, "bpe_simple_vocab_16e6.txt")
                val binCount = binsDirectory.walkTopDown().count { it.isFile && it.extension.equals("bin", true) }
                if (!vocab.isFile || binCount < 10) {
                    modelRoot.deleteRecursively()
                    throw IOException("ساختار پوشه‌های مدل با انتظار MediaPipe سازگار نیست.")
                }
                completionMarker.writeText(EXPECTED_MODEL_SHA256)
                archive.delete()
                modelReady = true
                runOnUiThread {
                    isWorking = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    operationStatusView.text = "مدل بررسی و آماده شد. اکنون می‌توانی حالت پرواز را روشن و تصویر تولید کنی."
                    refreshControls()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    isWorking = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    operationStatusView.text = "آماده‌سازی مدل ناموفق بود: ${error.message ?: error.javaClass.simpleName}"
                    refreshControls()
                }
            }
        }
    }

    private fun sha256(file: File, onProgress: (Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(HASH_BUFFER_SIZE)
        var total = 0L
        FileInputStream(file).buffered(HASH_BUFFER_SIZE).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
                total += count
                onProgress(total)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) }
    }

    private fun extractModelArchive(archive: File, destinationRoot: File, onProgress: (Long) -> Unit) {
        val canonicalRoot = destinationRoot.canonicalFile
        val rootPrefix = canonicalRoot.path + File.separator
        val buffer = ByteArray(EXTRACT_BUFFER_SIZE)
        var extractedBytes = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(archive), EXTRACT_BUFFER_SIZE)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(canonicalRoot, entry.name).canonicalFile
                if (!output.path.startsWith(rootPrefix)) {
                    throw IOException("نام فایل نامعتبر در آرشیو مدل.")
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(output), EXTRACT_BUFFER_SIZE).use { stream ->
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            extractedBytes += count
                            if (extractedBytes > MAX_EXTRACTED_BYTES) {
                                throw IOException("حجم استخراج‌شده از حد مجاز بیشتر شد.")
                            }
                            stream.write(buffer, 0, count)
                            onProgress(extractedBytes)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun runGeneration() {
        if (isWorking) return
        if (!modelReady || !isModelReadyOnDisk()) {
            modelReady = false
            refreshControls()
            showError("ابتدا مدل را دانلود و آماده‌سازی کن.")
            return
        }
        val prompt = promptInput.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) {
            showError("متن توصیف تصویر خالی است.")
            return
        }
        val steps = stepsInput.text?.toString()?.toIntOrNull()
        if (steps == null || steps !in 1..50) {
            showError("تعداد گام‌ها باید عددی بین ۱ و ۵۰ باشد.")
            return
        }
        val seed = seedInput.text?.toString()?.toIntOrNull()
        if (seed == null) {
            showError("Seed باید یک عدد صحیح باشد.")
            return
        }

        val batteryBefore = readBatteryPercent()
        val thermalBefore = readThermalStatus()
        val pssBefore = Debug.getPss()
        beginPssSampling(pssBefore)
        isWorking = true
        operationStatusView.text = "در حال آماده‌سازی موتور محلی؛ این کار هم‌زمان روی thread پس‌زمینه انجام می‌شود."
        metricsView.text = "در حال اندازه‌گیری بارگذاری، زمان تولید، حافظه و وضعیت حرارتی…"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refreshControls()

        worker.execute {
            var loadMs = 0L
            var generationMs = 0L
            try {
                var generator = imageGenerator
                if (generator == null) {
                    val loadStart = SystemClock.elapsedRealtime()
                    val options = ImageGeneratorOptions.builder()
                        .setImageGeneratorModelDirectory(binsDirectory.absolutePath)
                        .build()
                    generator = ImageGenerator.createFromOptions(applicationContext, options)
                    imageGenerator = generator
                    loadMs = SystemClock.elapsedRealtime() - loadStart
                }
                postStatus("مدل بارگذاری شد؛ در حال تولید تصویر ${steps} گامه…")
                val generationStart = SystemClock.elapsedRealtime()
                val result = generator.generate(prompt, steps, seed)
                generationMs = SystemClock.elapsedRealtime() - generationStart
                val mpImage = result?.generatedImage() ?: throw IOException("موتور تصویری برنگرداند.")
                val bitmap = BitmapExtractor.extract(mpImage)
                if (bitmap.width <= 0 || bitmap.height <= 0) {
                    throw IOException("ابعاد تصویر خروجی معتبر نیست.")
                }
                val pssAfter = Debug.getPss()
                val peakPss = peakPssKb.get().coerceAtLeast(pssAfter)
                val record = ImageRunRecord(
                    timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()),
                    device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}; ${Build.DISPLAY})",
                    totalRamMb = totalRamMb(),
                    steps = steps,
                    seed = seed,
                    width = bitmap.width,
                    height = bitmap.height,
                    loadMs = loadMs,
                    generationMs = generationMs,
                    pssBeforeKb = pssBefore,
                    pssPeakKb = peakPss,
                    pssAfterKb = pssAfter,
                    thermalBefore = thermalBefore,
                    thermalAfter = readThermalStatus(),
                    batteryBefore = batteryBefore,
                    batteryAfter = readBatteryPercent(),
                    prompt = prompt,
                )
                runOnUiThread {
                    endPssSampling()
                    isWorking = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    lastBitmap = bitmap
                    lastRecord = record
                    imageView.setImageBitmap(bitmap)
                    metricsView.text = formatMetrics(record)
                    operationStatusView.text = "تصویر تولید شد. آزمون در حالت پرواز نشان می‌دهد که پس از آماده‌شدن مدل، استنتاج می‌تواند آفلاین اجرا شود."
                    refreshControls()
                }
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                try {
                    imageGenerator?.close()
                } catch (_: Throwable) {
                    // Preserve the generation error.
                }
                imageGenerator = null
                runOnUiThread {
                    endPssSampling()
                    isWorking = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    operationStatusView.text = "تولید ناموفق بود: $message"
                    metricsView.text = "اگر گوشی داغ یا کند شده، فعلاً آزمون را متوقف کن و اجازه بده خنک شود."
                    refreshControls()
                }
            }
        }
    }

    private fun beginPssSampling(initialPssKb: Long) {
        sampleTask?.cancel(false)
        peakPssKb.set(initialPssKb)
        sampleTask = memorySampler.scheduleAtFixedRate({
            try {
                val current = Debug.getPss()
                peakPssKb.updateAndGet { previous -> maxOf(previous, current) }
            } catch (_: Throwable) {
                // A sample can be unavailable during process pressure.
            }
        }, 0, PSS_SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun endPssSampling() {
        sampleTask?.cancel(false)
        sampleTask = null
    }

    private fun unloadGenerator() {
        if (isWorking) return
        val oldGenerator = imageGenerator
        if (oldGenerator == null) {
            operationStatusView.text = "موتوری در حافظه بارگذاری نشده است."
            return
        }
        imageGenerator = null
        isWorking = true
        operationStatusView.text = "در حال آزادکردن حافظهٔ مدل…"
        refreshControls()
        worker.execute {
            try {
                oldGenerator.close()
                runOnUiThread {
                    isWorking = false
                    operationStatusView.text = "موتور از حافظه آزاد شد؛ فایل مدل در فضای ذخیره‌سازی باقی مانده است."
                    refreshControls()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    isWorking = false
                    operationStatusView.text = "آزادکردن حافظه کامل نشد: ${error.message ?: error.javaClass.simpleName}"
                    refreshControls()
                }
            }
        }
    }

    private fun confirmDeleteModel() {
        if (isWorking) return
        AlertDialog.Builder(this)
            .setTitle("حذف مدل محلی")
            .setMessage("فایل‌های مدل و آرشیو دانلودشده از این اپ حذف می‌شوند. تصویرهای ذخیره‌شده در گالری حذف نمی‌شوند.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("حذف") { _, _ -> deleteModelFiles() }
            .show()
    }

    private fun deleteModelFiles() {
        if (isWorking) return
        val oldGenerator = imageGenerator
        imageGenerator = null
        isWorking = true
        operationStatusView.text = "در حال آزادکردن موتور و حذف فایل‌های مدل…"
        refreshControls()
        worker.execute {
            try {
                try {
                    oldGenerator?.close()
                } catch (_: Throwable) {
                    // Continue deleting the local files.
                }
                modelRoot.deleteRecursively()
                archiveFile().delete()
                modelReady = false
                runOnUiThread {
                    isWorking = false
                    operationStatusView.text = "فایل‌های مدل حذف شدند."
                    refreshControls()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    isWorking = false
                    operationStatusView.text = "حذف مدل ناموفق بود: ${error.message ?: error.javaClass.simpleName}"
                    refreshControls()
                }
            }
        }
    }

    private fun saveImageToGallery() {
        val bitmap = lastBitmap ?: return
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "FontLocalImage_$stamp.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Font Local Image POC")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("ساخت فایل در گالری ممکن نشد.")
            contentResolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("ذخیرهٔ PNG کامل نشد.")
                }
            } ?: throw IOException("نوشتن فایل در گالری ممکن نشد.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            operationStatusView.text = "تصویر در گالری، پوشهٔ Pictures/Font Local Image POC ذخیره شد."
            Toast.makeText(this, "تصویر در گالری ذخیره شد.", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            showError("ذخیرهٔ تصویر ناموفق بود: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun copyReport() {
        val record = lastRecord ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("گزارش تولید تصویر", record.toCsv()))
        Toast.makeText(this, "گزارش آزمون کپی شد.", Toast.LENGTH_SHORT).show()
    }

    private fun formatMetrics(record: ImageRunRecord): String = buildString {
        append("مدل: Stable Diffusion 1.5 / MediaPipe Image Generator\n")
        append("اندازهٔ خروجی: ${record.width}×${record.height} — گام‌ها: ${record.steps} — seed: ${record.seed}\n")
        append("بارگذاری موتور در این نوبت: ${record.loadMs} ms\n")
        append("زمان تولید: ${record.generationMs} ms (${String.format(Locale.US, "%.2f", record.generationMs / 1000.0)} s)\n")
        append("PSS فرایند پیش/قله/پس از تولید: ${record.pssBeforeKb} / ${record.pssPeakKb} / ${record.pssAfterKb} KB\n")
        append("حرارت Android پیش/پس: ${record.thermalBefore} → ${record.thermalAfter}\n")
        append("باتری پیش/پس: ${record.batteryBefore}% → ${record.batteryAfter}%\n")
        append("دستگاه: ${record.device}; Android ${record.android}; RAM قابل‌مشاهده ${record.totalRamMb} MB")
    }

    private fun readBatteryPercent(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private fun readThermalStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "ناموجود"
        val power = getSystemService(POWER_SERVICE) as PowerManager
        return when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "عادی"
            PowerManager.THERMAL_STATUS_LIGHT -> "کم"
            PowerManager.THERMAL_STATUS_MODERATE -> "متوسط"
            PowerManager.THERMAL_STATUS_SEVERE -> "شدید"
            PowerManager.THERMAL_STATUS_CRITICAL -> "بحرانی"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "اضطراری"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "خاموشی"
            else -> "نامشخص"
        }
    }

    private fun totalRamMb(): Long {
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        return memory.totalMem / (1024L * 1024L)
    }

    private fun isModelReadyOnDisk(): Boolean {
        return completionMarker.isFile &&
            binsDirectory.isDirectory &&
            File(binsDirectory, "bpe_simple_vocab_16e6.txt").isFile
    }

    private fun archiveFile(): File {
        val folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("حافظهٔ دانلود برنامه در دسترس نیست.")
        return File(folder, MODEL_ARCHIVE_NAME)
    }

    private fun postStatus(message: String) {
        mainHandler.post {
            if (::operationStatusView.isInitialized) operationStatusView.text = message
        }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("خطا")
            .setMessage(message)
            .setPositiveButton("باشه", null)
            .show()
    }

    private fun makeSmallInput(value: String, maxLength: Int): EditText = EditText(this).apply {
        setText(value)
        setSingleLine(true)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
        setTextSize(15f)
        setTextColor(Color.rgb(29, 41, 39))
        gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0)
        setBackgroundColor(Color.WHITE)
    }

    private fun makeText(text: String, sizeSp: Int, bold: Boolean, color: Int): TextView = TextView(this).apply {
        this.text = text
        setTextSize(sizeSp.toFloat())
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.START
        textDirection = View.TEXT_DIRECTION_RTL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun makeButton(text: String, color: Int): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        minimumHeight = dp(48)
    }

    private fun addSectionTitle(parent: LinearLayout, text: String) {
        addGap(parent, 8)
        parent.addView(makeText(text, 19, true, Color.rgb(24, 53, 49)))
    }

    private fun addGap(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        bottomMargin = dp(6)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun percent(part: Long, total: Long): Int =
        if (total <= 0L) 0 else ((part * 100L) / total).toInt().coerceIn(0, 100)

    private fun formatBytes(value: Long): String = when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.2f GB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1f MB", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1f KB", value / 1_000.0)
        else -> "$value B"
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(downloadPoll)
        sampleTask?.cancel(false)
        val currentGenerator = imageGenerator
        imageGenerator = null
        if (currentGenerator != null && !isWorking) {
            worker.execute {
                try {
                    currentGenerator.close()
                } catch (_: Throwable) {
                    // The Activity is already being destroyed.
                }
            }
        }
        worker.shutdown()
        memorySampler.shutdownNow()
        super.onDestroy()
    }

    private data class ImageRunRecord(
        val timestamp: String,
        val device: String,
        val android: String,
        val totalRamMb: Long,
        val steps: Int,
        val seed: Int,
        val width: Int,
        val height: Int,
        val loadMs: Long,
        val generationMs: Long,
        val pssBeforeKb: Long,
        val pssPeakKb: Long,
        val pssAfterKb: Long,
        val thermalBefore: String,
        val thermalAfter: String,
        val batteryBefore: Int,
        val batteryAfter: Int,
        val prompt: String,
    ) {
        fun toCsv(): String {
            fun quote(value: String): String = "\"${value.replace("\"", "\"\"")}\""
            return listOf(
                "timestamp,device,android,total_ram_mb,model,steps,seed,width,height,load_ms,generation_ms,pss_before_kb,pss_peak_kb,pss_after_kb,thermal_before,thermal_after,battery_before,battery_after,prompt",
                listOf(
                    quote(timestamp), quote(device), quote(android), totalRamMb.toString(),
                    quote("MediaPipe SD1.5"), steps.toString(), seed.toString(), width.toString(),
                    height.toString(), loadMs.toString(), generationMs.toString(), pssBeforeKb.toString(),
                    pssPeakKb.toString(), pssAfterKb.toString(), quote(thermalBefore), quote(thermalAfter),
                    batteryBefore.toString(), batteryAfter.toString(), quote(prompt),
                ).joinToString(","),
            ).joinToString("\n")
        }
    }

    companion object {
        private const val INVALID_DOWNLOAD_ID = -1L
        private const val KEY_DOWNLOAD_ID = "pending_image_model_download"
        private const val DOWNLOAD_POLL_MS = 1_000L
        private const val PSS_SAMPLE_INTERVAL_MS = 500L
        private const val MODEL_DIRECTORY = "sd15_mediapipe_model"
        private const val MODEL_ARCHIVE_NAME = "sd15-mediapipe.zip"
        private const val MODEL_DOWNLOAD_URL = "https://huggingface.co/cuesetac/sd15-mediapipe/resolve/617a8c6c8c919fa269fe076213f999b004681412/sd15-mediapipe.zip?download=true"
        private const val EXPECTED_MODEL_SHA256 = "b8de2f5c339b63d9b9fb6c17d56b73128597e1189bda43965d86bc829b3cfc96"
        private const val MIN_ARCHIVE_BYTES = 1_500_000_000L
        private const val MIN_FREE_BYTES = 5_000_000_000L
        private const val MAX_EXTRACTED_BYTES = 5_000_000_000L
        private const val HASH_BUFFER_SIZE = 256 * 1024
        private const val HASH_STATUS_INTERVAL = 256L * 1024L * 1024L
        private const val EXTRACT_BUFFER_SIZE = 256 * 1024
        private const val EXTRACT_STATUS_INTERVAL = 256L * 1024L * 1024L
        private const val DEFAULT_PROMPT = "Square professional studio product photo of a handmade caramel-brown leather handbag with visible stitching, on a clean warm beige background, soft natural window light, realistic leather texture, no people, no text, no logos."
    }
}
