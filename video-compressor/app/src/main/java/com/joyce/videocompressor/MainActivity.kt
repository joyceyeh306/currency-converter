package com.joyce.videocompressor

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.ContextCompat
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    private var sourceMeta = SourceMeta()
    private var transformer: Transformer? = null
    private var tempOutput: File? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        selectedUri?.let { uri -> loadVideoInfo(uri) }
    }

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            compressButton.isEnabled = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            } else {
                loadVideoInfo(uri)
            }
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
            compressButton.text = if (isMp3Mode()) "③ 匯出 MP3" else "③ 開始處理"
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
        sourceMeta = readSourceMeta(uri)
        sourceDisplayName = sourceMeta.displayName ?: queryName(uri) ?: "影片"
        sourceSizeBytes = sourceMeta.sizeBytes.takeIf { it > 0 } ?: querySize(uri)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, metadataUri(uri))
            sourceDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            val dateText = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val parsedDate = sourceMeta.dateTakenMs.takeIf { it > 0 } ?: parseMetadataDate(dateText)
            sourceMeta = sourceMeta.copy(
                locationText = location,
                dateText = dateText,
                dateTakenMs = parsedDate,
                rotation = rotation
            )
            val timeLabel = if (parsedDate > 0) "\n原始時間：${formatDateTime(parsedDate)}" else ""
            val locationLabel = if (!location.isNullOrBlank()) "　位置：有" else ""
            fileInfo.text = "$sourceDisplayName\n原始大小：${formatBytes(sourceSizeBytes)}　解析度：${width}×${height}$timeLabel$locationLabel"
        } catch (_: Exception) {
            fileInfo.text = "$sourceDisplayName\n原始大小：${formatBytes(sourceSizeBytes)}"
        } finally {
            retriever.release()
        }
        compressButton.isEnabled = true
        updateEstimate()
    }

    private fun metadataUri(uri: Uri): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            return try { MediaStore.setRequireOriginal(uri) } catch (_: Exception) { uri }
        }
        return uri
    }

    private fun updateEstimate() {
        if (selectedUri == null || sourceDurationMs <= 0) return
        val outputName = outputDisplayName()
        val estimatedBytes = if (isMp3Mode()) {
            192_000L * sourceDurationMs / 1000L / 8L
        } else {
            val p = selectedProfile()
            (p.videoBitrate + 128_000L) * sourceDurationMs / 1000L / 8L
        }
        val saving = if (sourceSizeBytes > 0) ((1.0 - estimatedBytes.toDouble() / sourceSizeBytes) * 100).toInt() else 0
        val savingText = if (saving > 0) "，約省 $saving%" else ""
        estimateText.text = if (isMp3Mode()) {
            "預估 MP3：約 ${formatBytes(estimatedBytes)}$savingText\n輸出：$outputName\n保留原始檔名主體、時間與可讀取的位置資訊"
        } else {
            "預估新影片：約 ${formatBytes(estimatedBytes)}$savingText\n輸出：$outputName\n拍攝時間與可讀取的位置資訊會一併保留"
        }
    }

    private fun outputDisplayName(): String {
        val base = baseName(sourceDisplayName)
        return if (isMp3Mode()) "${base}_MP3.mp3" else "${base}_${selectedProfile().height}p.mp4"
    }

    @OptIn(UnstableApi::class)
    private fun startCompression() {
        val uri = selectedUri ?: return
        if (sourceMeta.relativePath.isNullOrBlank()) {
            statusText.text = "無法取得原影片所在資料夾，請從手機相簿或系統影片選擇器重新選擇影片。"
            return
        }
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
                    val exported = tempOutput ?: return
                    try {
                        val locationCopy = File(cacheDir, "metadata_$date.mp4").also { if (it.exists()) it.delete() }
                        val fileToSave = if (parseLocation(sourceMeta.locationText) != null) {
                            remuxWithLocation(exported, locationCopy, sourceMeta.locationText)
                            exported.delete()
                            locationCopy
                        } else exported

                        val finalSize = fileToSave.length()
                        val savedUri = saveVideoBesideSource(fileToSave, "${baseName(sourceDisplayName)}_${p.height}p.mp4")
                        statusText.text = "完成：${formatBytes(finalSize)}\n已存回原影片所在資料夾"
                        Toast.makeText(this@MainActivity, "影片處理完成", Toast.LENGTH_LONG).show()
                        if (savedUri != null) fileToSave.delete()
                    } catch (e: Exception) {
                        statusText.text = "已完成轉檔，但存檔失敗：${e.message ?: "未知錯誤"}"
                    }
                    finishUi()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    statusText.text = "處理失敗：${exportException.message ?: "未知錯誤"}"
                    finishUi()
                }
            })
            .build()

        setBusy(true, "正在處理影片… 請不要關閉 App")
        transformer?.start(editedItem, tempOutput!!.absolutePath)
        pollProgress()
    }

    private fun startMp3Export() {
        val uri = selectedUri ?: return
        if (sourceMeta.relativePath.isNullOrBlank()) {
            statusText.text = "無法取得原影片所在資料夾，請從手機相簿或系統影片選擇器重新選擇影片。"
            return
        }
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val rawOutput = File(cacheDir, "audio_$date.mp3").also { if (it.exists()) it.delete() }
        val taggedOutput = File(cacheDir, "audio_tagged_$date.mp3").also { if (it.exists()) it.delete() }
        setBusy(true, "正在匯出 MP3… 請不要關閉 App")

        Thread {
            try {
                decodeAudioToMp3(uri, rawOutput)
                if (!rawOutput.exists() || rawOutput.length() == 0L) throw IllegalStateException("沒有產生音檔")
                prependId3Metadata(rawOutput, taggedOutput)
                rawOutput.delete()
                val finalSize = taggedOutput.length()
                val savedUri = saveMp3BesideSource(taggedOutput, "${baseName(sourceDisplayName)}_MP3.mp3")
                if (savedUri != null) taggedOutput.delete()
                runOnUiThread {
                    statusText.text = "完成：${formatBytes(finalSize)}\n已存回原影片所在資料夾"
                    Toast.makeText(this, "MP3 匯出完成", Toast.LENGTH_LONG).show()
                    finishUi()
                }
            } catch (e: Exception) {
                rawOutput.delete()
                taggedOutput.delete()
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
            } else sourceDurationMs * 1000L

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

    private fun remuxWithLocation(input: File, output: File, locationText: String?) {
        val coords = parseLocation(locationText)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.absolutePath)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (coords != null) muxer.setLocation(coords.first, coords.second)

            val trackMap = mutableMapOf<Int, Int>()
            var maxBuffer = 2 * 1024 * 1024
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                trackMap[i] = muxer.addTrack(format)
                extractor.selectTrack(i)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxBuffer = maxOf(maxBuffer, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            muxer.start()
            val buffer = ByteBuffer.allocate(maxBuffer)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(trackMap[track] ?: break, buffer, info)
                extractor.advance()
            }
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun prependId3Metadata(input: File, output: File) {
        val frames = ByteArrayOutputStream()
        frames.write(textFrame("TIT2", baseName(sourceDisplayName)))
        frames.write(txxxFrame("SourceFile", sourceDisplayName))
        val dateValue = sourceMeta.dateText ?: sourceMeta.dateTakenMs.takeIf { it > 0 }?.let { formatDateTime(it) }
        if (!dateValue.isNullOrBlank()) frames.write(txxxFrame("OriginalDate", dateValue))
        if (!sourceMeta.locationText.isNullOrBlank()) frames.write(txxxFrame("Location", sourceMeta.locationText!!))

        val body = frames.toByteArray()
        output.outputStream().use { out ->
            out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
            out.write(toSynchsafe(body.size))
            out.write(body)
            input.inputStream().use { it.copyTo(out) }
        }
    }

    private fun textFrame(id: String, value: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(1)
        payload.write(utf16WithBom(value))
        return id3Frame(id, payload.toByteArray())
    }

    private fun txxxFrame(description: String, value: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(1)
        payload.write(utf16WithBom(description))
        payload.write(byteArrayOf(0, 0))
        payload.write(utf16WithBom(value))
        return id3Frame("TXXX", payload.toByteArray())
    }

    private fun id3Frame(id: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        val size = payload.size
        out.write(byteArrayOf(
            ((size ushr 24) and 0xFF).toByte(),
            ((size ushr 16) and 0xFF).toByte(),
            ((size ushr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte(),
            0, 0
        ))
        out.write(payload)
        return out.toByteArray()
    }

    private fun utf16WithBom(value: String): ByteArray {
        val raw = value.toByteArray(Charset.forName("UTF-16LE"))
        return byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + raw
    }

    private fun toSynchsafe(size: Int): ByteArray = byteArrayOf(
        ((size ushr 21) and 0x7F).toByte(),
        ((size ushr 14) and 0x7F).toByte(),
        ((size ushr 7) and 0x7F).toByte(),
        (size and 0x7F).toByte()
    )

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
            statusText.text = "正在處理影片… ${holder.progress}%"
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

    private fun saveVideoBesideSource(file: File, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IllegalStateException("此功能需要 Android 10 以上")
        val relativePath = sourceMeta.relativePath ?: throw IllegalStateException("無法取得原影片資料夾")
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
            if (sourceMeta.dateTakenMs > 0) put(MediaStore.Video.Media.DATE_TAKEN, sourceMeta.dateTakenMs)
            if (sourceMeta.dateModifiedSec > 0) put(MediaStore.Video.Media.DATE_MODIFIED, sourceMeta.dateModifiedSec)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("無法在原資料夾建立新影片")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("無法寫入新影片")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            if (sourceMeta.dateTakenMs > 0) values.put(MediaStore.Video.Media.DATE_TAKEN, sourceMeta.dateTakenMs)
            if (sourceMeta.dateModifiedSec > 0) values.put(MediaStore.Video.Media.DATE_MODIFIED, sourceMeta.dateModifiedSec)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveMp3BesideSource(file: File, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IllegalStateException("此功能需要 Android 10 以上")
        val relativePath = sourceMeta.relativePath ?: throw IllegalStateException("無法取得原影片資料夾")
        val collection = MediaStore.Files.getContentUri("external")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (sourceMeta.dateModifiedSec > 0) put(MediaStore.MediaColumns.DATE_MODIFIED, sourceMeta.dateModifiedSec)
        }
        val resolver = contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("無法在原資料夾建立 MP3")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("無法寫入 MP3")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (sourceMeta.dateModifiedSec > 0) values.put(MediaStore.MediaColumns.DATE_MODIFIED, sourceMeta.dateModifiedSec)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun readSourceMeta(uri: Uri): SourceMeta {
        var name: String? = null
        var size = 0L
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !c.isNull(nameIndex)) name = c.getString(nameIndex)
                    if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        var relativePath: String? = null
        var dateTaken = 0L
        var dateModified = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val p = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val t = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                        val m = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (p >= 0 && !c.isNull(p)) relativePath = c.getString(p)
                        if (t >= 0 && !c.isNull(t)) dateTaken = c.getLong(t)
                        if (m >= 0 && !c.isNull(m)) dateModified = c.getLong(m)
                    }
                }
            } catch (_: Exception) {}
        }
        return SourceMeta(name, size, relativePath, dateTaken, dateModified)
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

    private fun parseLocation(value: String?): Pair<Float, Float>? {
        if (value.isNullOrBlank()) return null
        val match = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)(?:/.*)?").find(value) ?: return null
        val lat = match.groupValues[1].toFloatOrNull() ?: return null
        val lon = match.groupValues[2].toFloatOrNull() ?: return null
        if (lat !in -90f..90f || lon !in -180f..180f) return null
        return lat to lon
    }

    private fun parseMetadataDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val patterns = listOf(
            "yyyyMMdd'T'HHmmss.SSS'Z'",
            "yyyyMMdd'T'HHmmss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val df = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return df.parse(value)?.time ?: 0L
            } catch (_: Exception) {}
        }
        return 0L
    }

    private fun formatDateTime(ms: Long): String {
        return SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.TAIWAN).format(Date(ms))
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

    data class SourceMeta(
        val displayName: String? = null,
        val sizeBytes: Long = 0L,
        val relativePath: String? = null,
        val dateTakenMs: Long = 0L,
        val dateModifiedSec: Long = 0L,
        val locationText: String? = null,
        val dateText: String? = null,
        val rotation: Int = 0
    )
}
