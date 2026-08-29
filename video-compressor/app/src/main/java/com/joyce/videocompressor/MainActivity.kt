package com.joyce.videocompressor

import android.content.ContentValues
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
import net.qiujuer.lame.Lame
import net.qiujuer.lame.LameOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    private var sourceDisplayName: String = "影片"
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
        qualityGroup.setOnCheckedChangeListener { _, _ ->
            updateEstimate()
            compressButton.text = if (isMp3Mode()) "③ 匯出 MP3" else "③ 開始壓縮"
        }
        compressButton.setOnClickListener {
            if (isMp3Mode()) startMp3Export() else startCompression()
        }
    }

    private fun isMp3Mode(): Boolean = qualityGroup.checkedRadioButtonId == R.id.qMp3

    private fun selectedProfile(): Profile = when (qualityGroup.checkedRadioButtonId) {
        R.id.q720 -> Profile(720, 2_500_000)
        R.id.q480 -> Profile(480, 1_100_000)
        else -> Profile(1080, 5_000_000)
    }

    private fun loadVideoInfo(uri: Uri) {
        sourceDisplayName = queryName(uri) ?: "影片"
        sourceSizeBytes = querySize(uri)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            sourceDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
            fileInfo.text = "$sourceDisplayName\n原始大小：${formatBytes(sourceSizeBytes)}　解析度：${width}×${height}"
        } catch (_: Exception) {
            fileInfo.text = "$sourceDisplayName\n原始大小：${formatBytes(sourceSizeBytes)}"
        } finally {
            retriever.release()
        }
        updateEstimate()
    }

    private fun updateEstimate() {
        if (selectedUri == null || sourceDurationMs <= 0) return
        val estimatedBytes = if (isMp3Mode()) {
            (192_000L * sourceDurationMs / 1000L / 8L)
        } else {
            val p = selectedProfile()
            ((p.videoBitrate + 128_000L) * sourceDurationMs / 1000L / 8L)
        }
        val saving = if (sourceSizeBytes > 0) ((1.0 - estimatedBytes.toDouble() / sourceSizeBytes) * 100).toInt() else 0
        val savingText = if (saving > 0) "，約省 $saving%" else ""
        estimateText.text = if (isMp3Mode()) {
            "預估 MP3：約 ${formatBytes(estimatedBytes)}$savingText\n192 kbps，只有聲音、不保留畫面"
        } else {
            "預估新影片：約 ${formatBytes(estimatedBytes)}$savingText\n（實際大小會依影片內容略有不同）"
        }
    }

    @OptIn(UnstableApi::class)
    private fun startCompression() {
        val uri = selectedUri ?: return
        val p = selectedProfile()
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        tempOutput = File(cacheDir, "compressed_$date.mp4").also { if (it.exists()) it.delete() }

        val videoSettings = VideoEncoderSettings.Builder().setBitrate(p.videoBitrate).build()
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
                        val base = baseName(sourceDisplayName)
                        val savedUri = saveVideoToGallery(file, "${base}_${p.height}p.mp4")
                        statusText.text = "完成：${formatBytes(finalSize)}\n已存到「影片 / 影片瘦身」"
                        Toast.makeText(this@MainActivity, "影片壓縮完成", Toast.LENGTH_LONG).show()
                        if (savedUri != null) file.delete()
                    } catch (e: Exception) {
                        statusText.text = "已壓縮，但存檔失敗：${e.message}"
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

        setBusy(true, "正在壓縮… 請不要關閉 App")
        transformer?.start(editedItem, tempOutput!!.absolutePath)
        pollProgress()
    }

    private fun startMp3Export() {
        val uri = selectedUri ?: return
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val output = File(cacheDir, "audio_$date.mp3").also { if (it.exists()) it.delete() }
        setBusy(true, "正在匯出 MP3… 請不要關閉 App")

        Thread {
            try {
                decodeAudioToMp3(uri, output)
                if (!output.exists() || output.length() == 0L) throw IllegalStateException("沒有產生音檔")
                val finalSize = output.length()
                val savedUri = saveAudioToGallery(output, "${baseName(sourceDisplayName)}_音檔.mp3")
                if (savedUri != null) output.delete()
                runOnUiThread {
                    statusText.text = "完成：${formatBytes(finalSize)}\n已存到「音樂 / 影片瘦身」"
                    Toast.makeText(this, "MP3 匯出完成", Toast.LENGTH_LONG).show()
                    finishUi()
                }
            } catch (e: Exception) {
                output.delete()
                runOnUiThread {
                    statusText.text = "MP3 匯出失敗：${e.message ?: "未知錯誤"}"
                    finishUi()
                }
            }
        }.start()
    }

    private fun decodeAudioToMp3(uri: Uri, outputFile: File) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var lameOutput: LameOutputStream? = null
        var fileOutput: FileOutputStream? = null

        try {
            extractor.setDataSource(this, uri, null)
            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    inputFormat = f
                    break
                }
            }
            if (audioTrack < 0 || inputFormat == null) throw IllegalArgumentException("這支影片沒有音軌")

            extractor.selectTrack(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: throw IllegalArgumentException("無法辨識音訊格式")
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                sourceDurationMs * 1000L
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            fileOutput = FileOutputStream(outputFile)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputChannels = 2
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var lastProgress = -1

            fun initLame(format: MediaFormat) {
                if (lameOutput != null) return
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else AudioFormat.ENCODING_PCM_16BIT
                val lameChannels = outputChannels.coerceAtMost(2)
                val lame = Lame(sampleRate, lameChannels, sampleRate, 192, Lame.LameQuality.GOOD)
                lameOutput = LameOutputStream(lame, fileOutput, 32768)
            }

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex) ?: throw IllegalStateException("無法取得解碼緩衝區")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> initLame(decoder.outputFormat)
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (lameOutput == null) initLame(decoder.outputFormat)
                        if (info.size > 0) {
                            val outputBuffer = decoder.getOutputBuffer(outputIndex)
                                ?: throw IllegalStateException("無法取得音訊資料")
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            outputBuffer.get(bytes)
                            var samples = pcmToShorts(bytes, pcmEncoding)
                            if (outputChannels > 2) samples = stereoFromMultiChannel(samples, outputChannels)
                            lameOutput?.write(samples, samples.size)

                            if (durationUs > 0) {
                                val progress = ((info.presentationTimeUs * 100L) / durationUs).toInt().coerceIn(0, 99)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    runOnUiThread {
                                        progressBar.progress = progress
                                        statusText.text = "正在匯出 MP3… $progress%"
                                    }
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }

            lameOutput?.close()
            lameOutput = null
            fileOutput = null
        } finally {
            try { lameOutput?.close() } catch (_: Exception) {}
            try { fileOutput?.close() } catch (_: Exception) {}
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun pcmToShorts(bytes: ByteArray, encoding: Int): ShortArray {
        return if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            ShortArray(floats.remaining()) { i ->
                val v = floats.get(i).coerceIn(-1f, 1f)
                (v * 32767f).toInt().toShort()
            }
        } else {
            val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            ShortArray(shorts.remaining()).also { shorts.get(it) }
        }
    }

    private fun stereoFromMultiChannel(input: ShortArray, channels: Int): ShortArray {
        val frames = input.size / channels
        val out = ShortArray(frames * 2)
        for (i in 0 until frames) {
            out[i * 2] = input[i * channels]
            out[i * 2 + 1] = input[i * channels + 1]
        }
        return out
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

    private fun setBusy(busy: Boolean, text: String = "") {
        selectButton.isEnabled = !busy
        compressButton.isEnabled = !busy && selectedUri != null
        qualityGroup.isEnabled = !busy
        for (i in 0 until qualityGroup.childCount) qualityGroup.getChildAt(i).isEnabled = !busy
        progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) {
            progressBar.progress = 0
            statusText.text = text
        }
    }

    private fun finishUi() {
        transformer = null
        progressBar.progress = 100
        setBusy(false)
    }

    private fun saveVideoToGallery(file: File, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
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

    private fun saveAudioToGallery(file: File, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/影片瘦身")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
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

    private fun baseName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        return base.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "未知"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format(Locale.TAIWAN, "%.2f GB", mb / 1024.0)
        else if (mb >= 1) String.format(Locale.TAIWAN, "%.1f MB", mb)
        else String.format(Locale.TAIWAN, "%.0f KB", bytes / 1024.0)
    }

    override fun onDestroy() {
        transformer?.cancel()
        super.onDestroy()
    }

    data class Profile(val height: Int, val videoBitrate: Int)
}
