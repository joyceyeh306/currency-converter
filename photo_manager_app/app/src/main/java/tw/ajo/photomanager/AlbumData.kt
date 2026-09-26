package tw.ajo.photomanager

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

enum class GroupMode(val label: String) { YEAR("年"), MONTH("月"), DAY("日"), ALL("全部") }
enum class MediaKind { IMAGE, VIDEO }
enum class MediaFilter(val label: String) { ALL("全部"), PHOTO("照片"), VIDEO("影片") }
enum class SortField(val label: String) {
    CAPTURE_TIME("拍攝時間"),
    FILE_NAME("檔名"),
    FILE_SIZE("檔案大小"),
    MODIFIED_TIME("修改時間"),
    ADDED_TIME("加入手機時間"),
    RESOLUTION("解析度")
}

data class MediaItem(
    val key: String,
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val kind: MediaKind,
    val dateTaken: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Long,
    val wallTime: LocalDateTime,
    val timeSource: String
)

data class AlbumSection(val key: String, val title: String, val items: List<MediaItem>)
data class MonthBucket(
    val key: String,
    val year: Int,
    val month: Int,
    val items: List<MediaItem>
)

data class ExifRow(val label: String, val value: String, val rawTag: String)

data class DetailInfo(
    val item: MediaItem,
    val make: String,
    val model: String,
    val lens: String,
    val focal35: String,
    val aperture: String,
    val exposure: String,
    val iso: String,
    val hasGps: Boolean,
    val lat: Double?,
    val lon: Double?,
    val altitude: Double?,
    val rows: List<ExifRow>
)

class AlbumRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val filename14 = Pattern.compile("((?:19|20)\\d{12})")
    private val filenameFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)
    private val exifFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val favoritePrefs = context.getSharedPreferences("ajo_album_favorites", Context.MODE_PRIVATE)
    private val timeIndexPrefs = context.getSharedPreferences("ajo_album_time_index", Context.MODE_PRIVATE)

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasAnyMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun loadBase(): List<MediaItem> = withContext(Dispatchers.IO) {
        val all = mutableListOf<MediaItem>()
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        ) {
            all.addAll(queryImages())
        }
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        ) {
            all.addAll(queryVideos())
        }
        all.sortedByDescending { it.wallTime }
    }

    suspend fun refineOriginalTimes(
        source: List<MediaItem>,
        onBatch: suspend (List<MediaItem>) -> Unit
    ) {
        val pendingImages = source.filter {
            it.kind == MediaKind.IMAGE && !hasFreshIndex(it.key, it.dateModified)
        }
        if (pendingImages.isEmpty()) return

        var working = source
        val updates = mutableMapOf<String, MediaItem>()
        var processed = 0

        for (item in pendingImages) {
            val original = withContext(Dispatchers.IO) { readOriginalTime(item.uri) }
            saveIndex(item.key, item.dateModified, original)
            if (original != null && (original != item.wallTime || item.timeSource != "EXIF 原始拍攝時間")) {
                updates[item.key] = item.copy(
                    wallTime = original,
                    timeSource = "EXIF 原始拍攝時間"
                )
            }
            processed += 1
            if (processed % 80 == 0 || processed == pendingImages.size) {
                if (updates.isNotEmpty()) {
                    val batch = updates.toMap()
                    working = working.map { batch[it.key] ?: it }.sortedByDescending { it.wallTime }
                    updates.clear()
                    onBatch(working)
                }
            }
        }
    }

    fun readDetail(item: MediaItem): DetailInfo {
        if (item.kind == MediaKind.VIDEO) {
            return DetailInfo(
                item = item,
                make = "",
                model = "",
                lens = "",
                focal35 = "",
                aperture = "",
                exposure = "",
                iso = "",
                hasGps = false,
                lat = null,
                lon = null,
                altitude = null,
                rows = listOf(
                    ExifRow("檔名", item.name, "DISPLAY_NAME"),
                    ExifRow("格式", item.mime, "MIME_TYPE"),
                    ExifRow("影片長度", formatDuration(item.duration), "DURATION"),
                    ExifRow("解析度", item.width.toString() + " × " + item.height.toString(), "WIDTH × HEIGHT"),
                    ExifRow("檔案大小", formatBytes(item.size), "SIZE")
                )
            )
        }

        var make = ""
        var model = ""
        var lens = ""
        var focal35 = ""
        var aperture = ""
        var exposure = ""
        var iso = ""
        var hasGps = false
        var lat: Double? = null
        var lon: Double? = null
        var altitude: Double? = null
        val rows = mutableListOf<ExifRow>()

        try {
            resolver.openInputStream(item.uri)?.use { stream ->
                val exif = ExifInterface(stream)

                fun add(label: String, tag: String): String {
                    val value = exif.getAttribute(tag).orEmpty()
                    if (value.isNotBlank()) rows.add(ExifRow(label, value, tag))
                    return value
                }

                add("原始拍攝時間", ExifInterface.TAG_DATETIME_ORIGINAL)
                add("數位化時間", ExifInterface.TAG_DATETIME_DIGITIZED)
                add("影像時間", ExifInterface.TAG_DATETIME)
                add("時區", ExifInterface.TAG_OFFSET_TIME)
                add("原始拍攝時區", ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                add("數位化時區", ExifInterface.TAG_OFFSET_TIME_DIGITIZED)
                make = add("相機品牌", ExifInterface.TAG_MAKE)
                model = add("相機型號", ExifInterface.TAG_MODEL)
                add("軟體", ExifInterface.TAG_SOFTWARE)
                lens = add("鏡頭", ExifInterface.TAG_LENS_MODEL)
                add("焦距", ExifInterface.TAG_FOCAL_LENGTH)
                focal35 = add("35mm 等效焦距", ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
                aperture = add("光圈", ExifInterface.TAG_F_NUMBER)
                exposure = add("快門", ExifInterface.TAG_EXPOSURE_TIME)
                iso = add("ISO", ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                add("曝光補償", ExifInterface.TAG_EXPOSURE_BIAS_VALUE)
                add("測光模式", ExifInterface.TAG_METERING_MODE)
                add("曝光模式", ExifInterface.TAG_EXPOSURE_MODE)
                add("白平衡", ExifInterface.TAG_WHITE_BALANCE)
                add("閃光燈", ExifInterface.TAG_FLASH)
                add("色彩空間", ExifInterface.TAG_COLOR_SPACE)
                add("方向", ExifInterface.TAG_ORIENTATION)
                add("像素寬度", ExifInterface.TAG_PIXEL_X_DIMENSION)
                add("像素高度", ExifInterface.TAG_PIXEL_Y_DIMENSION)
                add("影像說明", ExifInterface.TAG_IMAGE_DESCRIPTION)
                add("攝影者", ExifInterface.TAG_ARTIST)
                add("版權", ExifInterface.TAG_COPYRIGHT)

                val ll = FloatArray(2)
                if (exif.getLatLong(ll)) {
                    hasGps = true
                    lat = ll[0].toDouble()
                    lon = ll[1].toDouble()
                    rows.add(ExifRow("GPS 緯度", lat.toString(), ExifInterface.TAG_GPS_LATITUDE))
                    rows.add(ExifRow("GPS 經度", lon.toString(), ExifInterface.TAG_GPS_LONGITUDE))
                    val alt = exif.getAltitude(Double.NaN)
                    if (!alt.isNaN()) {
                        altitude = alt
                        rows.add(ExifRow("海拔", alt.toString(), ExifInterface.TAG_GPS_ALTITUDE))
                    }
                    add("GPS 日期", ExifInterface.TAG_GPS_DATESTAMP)
                    add("GPS 時間", ExifInterface.TAG_GPS_TIMESTAMP)
                    add("拍攝方向", ExifInterface.TAG_GPS_IMG_DIRECTION)
                }
            }
        } catch (_: Exception) {
        }

        return DetailInfo(
            item = item,
            make = make,
            model = model,
            lens = lens,
            focal35 = focal35,
            aperture = aperture,
            exposure = exposure,
            iso = iso,
            hasGps = hasGps,
            lat = lat,
            lon = lon,
            altitude = altitude,
            rows = rows
        )
    }

    fun isFavorite(key: String): Boolean {
        return favoritePrefs.getStringSet("favorites", emptySet())?.contains(key) == true
    }

    fun setFavorite(key: String, value: Boolean) {
        val set = favoritePrefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (value) set.add(key) else set.remove(key)
        favoritePrefs.edit().putStringSet("favorites", set).apply()
    }

    fun toggleFavorite(key: String) {
        setFavorite(key, !isFavorite(key))
    }

    fun trashRequest(items: List<MediaItem>): PendingIntent? {
        if (items.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= 30) {
            return MediaStore.createTrashRequest(resolver, items.map { it.uri }, true)
        }
        items.forEach {
            try {
                resolver.delete(it.uri, null, null)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun queryImages(): List<MediaItem> {
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        return queryMedia(uri, projection, MediaKind.IMAGE, false)
    }

    private fun queryVideos(): List<MediaItem> {
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION
        )
        return queryMedia(uri, projection, MediaKind.VIDEO, true)
    }

    private fun queryMedia(
        baseUri: Uri,
        projection: Array<String>,
        kind: MediaKind,
        hasDuration: Boolean
    ): List<MediaItem> {
        val output = mutableListOf<MediaItem>()
        resolver.query(
            baseUri,
            projection,
            null,
            null,
            MediaStore.MediaColumns.DATE_TAKEN + " DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.longOrZero(0)
                val name = cursor.stringOrEmpty(1)
                val dateTaken = cursor.longOrZero(2)
                val added = cursor.longOrZero(3) * 1000L
                val modified = cursor.longOrZero(4) * 1000L
                val mime = cursor.stringOrEmpty(5)
                val size = cursor.longOrZero(6)
                val width = cursor.intOrZero(7)
                val height = cursor.intOrZero(8)
                val duration = if (hasDuration) cursor.longOrZero(9) else 0L
                val itemUri = ContentUris.withAppendedId(baseUri, id)
                val key = kind.name + ":" + id.toString()

                val cached = if (kind == MediaKind.IMAGE) readIndexedTime(key, modified) else null
                val filenameTime = parseFilenameTime(name)
                val fallbackMillis = when {
                    dateTaken > 0L -> dateTaken
                    modified > 0L -> modified
                    added > 0L -> added
                    else -> System.currentTimeMillis()
                }
                val fallback = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(fallbackMillis),
                    ZoneId.systemDefault()
                )
                val effective = cached ?: filenameTime ?: fallback
                val source = when {
                    cached != null -> "EXIF 原始拍攝時間"
                    filenameTime != null -> "檔名時間"
                    else -> "Android MediaStore"
                }

                output.add(
                    MediaItem(
                        key = key,
                        id = id,
                        uri = itemUri,
                        name = name,
                        mime = mime,
                        kind = kind,
                        dateTaken = dateTaken,
                        dateAdded = added,
                        dateModified = modified,
                        size = size,
                        width = width,
                        height = height,
                        duration = duration,
                        wallTime = effective,
                        timeSource = source
                    )
                )
            }
        }
        return output
    }

    private fun cacheKey(key: String): String = "time_" + key

    private fun hasFreshIndex(key: String, modified: Long): Boolean {
        val raw = timeIndexPrefs.getString(cacheKey(key), null) ?: return false
        val separator = raw.indexOf('|')
        if (separator <= 0) return false
        val storedModified = raw.substring(0, separator).toLongOrNull() ?: return false
        return storedModified == modified
    }

    private fun readIndexedTime(key: String, modified: Long): LocalDateTime? {
        val raw = timeIndexPrefs.getString(cacheKey(key), null) ?: return null
        val separator = raw.indexOf('|')
        if (separator <= 0) return null
        val storedModified = raw.substring(0, separator).toLongOrNull() ?: return null
        if (storedModified != modified) return null
        val value = raw.substring(separator + 1)
        if (value == "NONE") return null
        return try {
            LocalDateTime.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveIndex(key: String, modified: Long, time: LocalDateTime?) {
        val value = modified.toString() + "|" + (time?.toString() ?: "NONE")
        timeIndexPrefs.edit().putString(cacheKey(key), value).apply()
    }

    private fun parseFilenameTime(name: String): LocalDateTime? {
        val digits = name.replace(Regex("[^0-9]"), "")
        val matcher = filename14.matcher(digits)
        if (!matcher.find()) return null
        return try {
            LocalDateTime.parse(matcher.group(1), filenameFormatter)
        } catch (_: Exception) {
            null
        }
    }

    private fun readOriginalTime(uri: Uri): LocalDateTime? {
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
                LocalDateTime.parse(raw.take(19), exifFormatter)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun Cursor.stringOrEmpty(index: Int): String =
        if (isNull(index)) "" else getString(index).orEmpty()

    private fun Cursor.longOrZero(index: Int): Long =
        if (isNull(index)) 0L else getLong(index)

    private fun Cursor.intOrZero(index: Int): Int =
        if (isNull(index)) 0 else getInt(index)
}

fun filterMedia(items: List<MediaItem>, filter: MediaFilter): List<MediaItem> {
    return when (filter) {
        MediaFilter.ALL -> items
        MediaFilter.PHOTO -> items.filter { it.kind == MediaKind.IMAGE }
        MediaFilter.VIDEO -> items.filter { it.kind == MediaKind.VIDEO }
    }
}

fun sortMedia(items: List<MediaItem>, field: SortField, descending: Boolean): List<MediaItem> {
    val comparator = when (field) {
        SortField.CAPTURE_TIME -> compareBy<MediaItem> { it.wallTime }
        SortField.FILE_NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortField.FILE_SIZE -> compareBy<MediaItem> { it.size }
        SortField.MODIFIED_TIME -> compareBy<MediaItem> { it.dateModified }
        SortField.ADDED_TIME -> compareBy<MediaItem> { it.dateAdded }
        SortField.RESOLUTION -> compareBy<MediaItem> { it.width.toLong() * it.height.toLong() }
    }
    return if (descending) items.sortedWith(comparator.reversed()) else items.sortedWith(comparator)
}

fun buildSections(items: List<MediaItem>, mode: GroupMode): List<AlbumSection> {
    if (mode == GroupMode.ALL) return listOf(AlbumSection("all", "全部照片", items))

    val grouped = items.groupBy {
        when (mode) {
            GroupMode.YEAR -> it.wallTime.format(DateTimeFormatter.ofPattern("yyyy"))
            GroupMode.MONTH -> it.wallTime.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            GroupMode.DAY -> it.wallTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            GroupMode.ALL -> "all"
        }
    }

    return grouped.entries
        .sortedByDescending { it.key }
        .map { entry ->
            val first = entry.value.first().wallTime
            val title = when (mode) {
                GroupMode.YEAR -> first.year.toString() + "年"
                GroupMode.MONTH -> first.year.toString() + "年" + first.monthValue.toString() + "月"
                GroupMode.DAY -> first.year.toString() + "年" + first.monthValue.toString() + "月" + first.dayOfMonth.toString() + "日"
                GroupMode.ALL -> "全部照片"
            }
            AlbumSection(entry.key, title, entry.value)
        }
}

fun buildMonthBuckets(items: List<MediaItem>): List<Pair<Int, List<MonthBucket>>> {
    val buckets = items
        .groupBy { it.wallTime.format(DateTimeFormatter.ofPattern("yyyy-MM")) }
        .map { (key, list) ->
            val dt = list.maxByOrNull { it.wallTime }?.wallTime ?: list.first().wallTime
            MonthBucket(key, dt.year, dt.monthValue, list.sortedByDescending { it.wallTime })
        }
        .sortedByDescending { it.key }

    return buckets.groupBy { it.year }
        .entries
        .sortedByDescending { it.key }
        .map { it.key to it.value.sortedByDescending { bucket -> bucket.month } }
}

fun formatDateTime(value: LocalDateTime): String =
    value.format(DateTimeFormatter.ofPattern("yyyy年M月d日  HH:mm:ss", Locale.TAIWAN))

fun formatDateOnly(value: LocalDateTime): String =
    value.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.TAIWAN))

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "影片"
    val total = ms / 1000L
    val hour = total / 3600L
    val minute = (total % 3600L) / 60L
    val second = total % 60L
    return if (hour > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hour, minute, second)
    } else {
        String.format(Locale.US, "%d:%02d", minute, second)
    }
}
