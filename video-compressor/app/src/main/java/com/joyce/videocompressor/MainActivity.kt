package com.joyce.videocompressor

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var selectButton: Button
    private lateinit var compressButton: Button
    private lateinit var qualityGroup: RadioGroup
    private lateinit var fileInfo: TextView
    private lateinit var estimateText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    private var selectedUri: Uri? = null
    private var sourceSizeBytes: Long = 0L
    private var sourceDurationMs: Long = 0L
    private var transformer: Transformer? = null
    private var tempOutput: File? = null

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            loadVideoInfo(uri)
            compressButton.isEnabled = true
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectButton = findViewById(R.id.selectButton)
        compressButton = findViewById(R.id.compressButton)
        qualityGroup = findViewById(R.id.qualityGroup)
        fileInfo = findViewById(R.id.fileInfo)
        estimateText = findViewById(R.id.estimateText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)

        selectButton.setOnClickListener { picker.launch("video/*") }
        qualityGroup.setOnCheckedChangeListener { _, _ -> updateEstimate() }
        compressButton.setOnClickListener { startCompression() }
    }

    private fun selectedProfile(): Profile = when (qualityGroup.checkedRadioButtonId) {
        R.id.q720 -> Profile(720, 2_500_000)
        R.id.q480 -> Profile(480, 1_100_000)
        else -> Profile(1080, 5_000_000)
    }

    private fun loadVideoInfo(uri: Uri) {
        val name = queryName(uri) ?: "影片"
        sourceSizeBytes = querySize(uri)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            sourceDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
            fileInfo.text = "$name\n原始大小：${formatBytes(sourceSizeBytes)}　解析度：${width}×${height}"
        } catch (_: Exception) {
            fileInfo.text = "$name\n原始大小：${formatBytes(sourceSizeBytes)}"
        } finally {
            retriever.release()
        }
        updateEstimate()
    }

    private fun updateEstimate() {
        if (selectedUri == null || sourceDurationMs <= 0) return
        val p = selectedProfile()
        val audioBitrate = 128_000
        val estimatedBytes = ((p.videoBitrate + audioBitrate) * (sourceDurationMs / 1000.0) / 8.0).toLong()
        val saving = if (sourceSizeBytes > 0) ((1.0 - estimatedBytes.toDouble() / sourceSizeBytes) * 100).toInt() else 0
        val savingText = if (saving > 0) "，約省 $saving%" else ""
        estimateText.text = "預估新檔：約 ${formatBytes(estimatedBytes)}$savingText\n（實際大小會依影片內容略有不同）"
    }

    @OptIn(UnstableApi::class)
    private fun startCompression() {
        val uri = selectedUri ?: return
        val p = selectedProfile()
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        tempOutput = File(cacheDir, "compressed_$date.mp4").also { if (it.exists()) it.delete() }

        val videoSettings = VideoEncoderSettings.Builder()
            .setBitrate(p.videoBitrate)
            .build()
        val encoderFactory = DefaultEncoderFactory.Builder(this)
            .setRequestedVideoEncoderSettings(videoSettings)
            .build()

        val videoEffects: List<Effect> = listOf(Presentation.createForHeight(p.height))
        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        transformer = Transformer.Builder(this)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val file = tempOutput ?: return
                    try {
                        val finalSize = file.length()
                        val savedUri = saveToGallery(file)
                        statusText.text = "完成：${formatBytes(finalSize)}\n已存到「影片 / 影片瘦身」"
                        Toast.makeText(this@MainActivity, "壓縮完成", Toast.LENGTH_LONG).show()
                        if (savedUri != null) file.delete()
                    } catch (e: Exception) {
                        statusText.text = "已壓縮，但存入相簿失敗：${e.message}"
                    }
                    finishUi()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    statusText.text = "壓縮失敗：${exportException.message ?: "未知錯誤"}"
                    finishUi()
                }
            })
            .build()

        compressButton.isEnabled = false
        selectButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "正在壓縮… 請不要關閉 App"
        transformer?.start(editedItem, tempOutput!!.absolutePath)
        pollProgress()
    }

    @OptIn(UnstableApi::class)
    private fun pollProgress() {
        val t = transformer ?: return
        val holder = ProgressHolder()
        val state = t.getProgress(holder)
        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
            progressBar.progress = holder.progress
            statusText.text = "正在壓縮… ${holder.progress}%"
        }
        if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
            progressBar.postDelayed({ pollProgress() }, 500)
        }
    }

    private fun finishUi() {
        transformer = null
        progressBar.progress = 100
        compressButton.isEnabled = true
        selectButton.isEnabled = true
    }

    private fun saveToGallery(file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/影片瘦身")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    private fun querySize(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
        }
        return 0L
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "未知"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format(Locale.TAIWAN, "%.2f GB", mb / 1024.0)
        else String.format(Locale.TAIWAN, "%.0f MB", mb)
    }

    override fun onDestroy() {
        transformer?.cancel()
        super.onDestroy()
    }

    data class Profile(val height: Int, val videoBitrate: Int)
}
