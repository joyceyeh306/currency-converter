package tw.ajo.photomanager

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
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
    val folderHint: String,
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

data class CustomAlbum(
    val id: String,
    val name: String,
    val mediaKeys: Set<String>
)

data class MediaLocation(
    val item: MediaItem,
    val lat: Double,
    val lon: Double
)

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

data class FilenameRenamePreview(
    val item: MediaItem,
    val newName: String,
    val error: String?,
    val changed: Boolean
)

data class FilenameRenameResult(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val details: List<String>
)

data class PhotoTimePreview(
    val item: MediaItem,
    val currentTime: LocalDateTime,
    val newTime: LocalDateTime,
    val error: String?,
    val changed: Boolean
)

data class PhotoTimeResult(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val details: List<String>
)

data class GpsEditPreview(
    val item: MediaItem,
    val currentLat: Double?,
    val currentLon: Double?,
    val newLat: Double?,
    val newLon: Double?,
    val remove: Boolean,
    val error: String?,
    val changed: Boolean
)

data class GpsEditResult(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val details: List<String>
)

class AlbumRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val filename14 = Pattern.compile("((?:19|20)\\d{12})")
    private val filenameFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)
    private val strictFilenameDateTime = Regex(
        """(?<!\d)((?:19|20)\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\d|3[01])(?:[T _.-]?)([01]\d|2[0-3])[-_.:]?([0-5]\d)[-_.:]?([0-5]\d)(?!\d)"""
    )
    private val exifFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val MP4_EPOCH_OFFSET_SECONDS = 2_082_844_800L
    private val favoritePrefs = context.getSharedPreferences("ajo_album_favorites", Context.MODE_PRIVATE)
    private val timeIndexPrefs = context.getSharedPreferences("ajo_album_time_index", Context.MODE_PRIVATE)
    private val placePrefs = context.getSharedPreferences("ajo_album_place_cache", Context.MODE_PRIVATE)
    private val placeSearchPrefs = context.getSharedPreferences("ajo_album_place_search_index", Context.MODE_PRIVATE)
    private val placeSearchCoordPrefs = context.getSharedPreferences("ajo_album_place_search_coord", Context.MODE_PRIVATE)
    private val albumPrefs = context.getSharedPreferences("ajo_album_custom_albums", Context.MODE_PRIVATE)
    private val gpsPrefs = context.getSharedPreferences("ajo_album_gps_index", Context.MODE_PRIVATE)
    private val collectionPrefs = context.getSharedPreferences("ajo_album_collection_order", Context.MODE_PRIVATE)
    private val archivePrefs = context.getSharedPreferences("ajo_album_archive", Context.MODE_PRIVATE)
    private val devicePrefs = context.getSharedPreferences("ajo_album_device_index", Context.MODE_PRIVATE)
    private val keywordPrefs = context.getSharedPreferences("ajo_album_keywords", Context.MODE_PRIVATE)
    private val organizerPrefs = context.getSharedPreferences("ajo_album_organizer_history", Context.MODE_PRIVATE)
    private var keywordCache: MutableMap<String, Set<String>>? = null

    fun hasImagePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasVideoPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAllMediaPermissions(): Boolean =
        hasImagePermission() && hasVideoPermission()

    fun hasAnyMediaPermission(): Boolean =
        hasImagePermission() || hasVideoPermission()

    fun requiredPermissions(): Array<String> {
        val result = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasImagePermission()) {
                result.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (!hasVideoPermission()) {
                result.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else if (!hasImagePermission()) {
            result.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (
            Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            result.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

        return result.toTypedArray()
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

    fun sourceLabel(item: MediaItem): String? {
        val name = item.name.lowercase(Locale.ROOT)
        val folder = item.folderHint.lowercase(Locale.ROOT)

        val isScreenshot =
            name.contains("screenshot") ||
            name.contains("screen_shot") ||
            name.contains("screen-shot") ||
            name.contains("screen capture") ||
            name.contains("screen_capture") ||
            folder.contains("screenshot") ||
            folder.contains("螢幕截圖")

        if (isScreenshot) return "螢幕截圖"

        val isLine =
            folder == "line" ||
            folder.contains("/line") ||
            folder.contains("line/") ||
            name.startsWith("line_") ||
            name.startsWith("line-")

        if (isLine) return "LINE 圖片"

        return null
    }

    fun smartSourceTag(item: MediaItem): String? {
        val name = item.name.lowercase(Locale.ROOT)
        val folder = item.folderHint.lowercase(Locale.ROOT)

        if (sourceLabel(item) == "螢幕截圖") return "screenshot"
        if (sourceLabel(item) == "LINE 圖片") return "line"
        if (folder.contains("instagram") || name.startsWith("instagram_") || name.startsWith("ig_")) {
            return "instagram"
        }
        if (folder.contains("download")) return "downloads"
        if (folder.contains("camera") || name.startsWith("img_") || name.startsWith("vid_")) {
            return "camera"
        }
        return null
    }

    fun customAlbums(): List<CustomAlbum> {
        val ids = albumPrefs.getString("album_ids", "")
            .orEmpty()
            .split("|")
            .filter { it.isNotBlank() }

        return ids.mapNotNull { id ->
            val name = albumPrefs.getString("name_${id}", null)?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            CustomAlbum(
                id = id,
                name = name,
                mediaKeys = albumPrefs.getStringSet("keys_${id}", emptySet())?.toSet() ?: emptySet()
            )
        }
    }

    fun createCustomAlbum(name: String): CustomAlbum {
        val clean = name.trim().ifBlank { "未命名相簿" }
        val id = System.currentTimeMillis().toString()
        val currentIds = albumPrefs.getString("album_ids", "").orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
            .toMutableList()
        currentIds.add(id)
        albumPrefs.edit()
            .putString("album_ids", currentIds.joinToString("|"))
            .putString("name_${id}", clean)
            .putStringSet("keys_${id}", emptySet())
            .apply()
        return CustomAlbum(id, clean, emptySet())
    }

    fun addToCustomAlbum(albumId: String, keys: Set<String>) {
        if (keys.isEmpty()) return
        val current = albumPrefs.getStringSet("keys_${albumId}", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        current.addAll(keys)
        albumPrefs.edit().putStringSet("keys_${albumId}", current).apply()
    }

    fun removeFromCustomAlbum(albumId: String, keys: Set<String>) {
        if (keys.isEmpty()) return
        val current = albumPrefs.getStringSet("keys_${albumId}", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        current.removeAll(keys)
        albumPrefs.edit().putStringSet("keys_${albumId}", current).apply()
    }

    fun customAlbumKeys(albumId: String): Set<String> =
        albumPrefs.getStringSet("keys_${albumId}", emptySet())?.toSet() ?: emptySet()

    private fun keywordIndex(): MutableMap<String, Set<String>> {
        keywordCache?.let { return it }
        val loaded = mutableMapOf<String, Set<String>>()
        keywordPrefs.all.forEach { (prefKey, value) ->
            if (!prefKey.startsWith("kw_")) return@forEach
            val mediaKey = prefKey.removePrefix("kw_")
            @Suppress("UNCHECKED_CAST")
            val set = (value as? Set<String>)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
            if (set.isNotEmpty()) loaded[mediaKey] = set
        }
        keywordCache = loaded
        return loaded
    }

    fun keywordsFor(key: String): Set<String> =
        keywordIndex()[key].orEmpty()

    fun allKeywordCounts(keys: Set<String>? = null): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        keywordIndex().forEach { (mediaKey, words) ->
            if (keys != null && mediaKey !in keys) return@forEach
            words.forEach { word -> counts[word] = (counts[word] ?: 0) + 1 }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase(Locale.ROOT) })
            .map { it.key to it.value }
    }

    fun addKeywords(keys: Set<String>, rawKeywords: Collection<String>) {
        if (keys.isEmpty()) return
        val cleanInput = rawKeywords
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        if (cleanInput.isEmpty()) return

        val index = keywordIndex()
        val canonical = allKeywordCounts()
            .associate { it.first.lowercase(Locale.ROOT) to it.first }
            .toMutableMap()
        val words = cleanInput.map { input ->
            canonical[input.lowercase(Locale.ROOT)] ?: input.also {
                canonical[it.lowercase(Locale.ROOT)] = it
            }
        }.toSet()

        val editor = keywordPrefs.edit()
        keys.forEach { key ->
            val next = index[key].orEmpty().toMutableSet()
            next.addAll(words)
            index[key] = next.toSet()
            editor.putStringSet("kw_$key", next)
        }
        editor.apply()
        appendOrganizerHistory("加入關鍵字「${words.joinToString("、")}」到 ${keys.size} 項")
    }

    fun removeKeyword(keys: Set<String>, keyword: String) {
        if (keys.isEmpty() || keyword.isBlank()) return
        val index = keywordIndex()
        val editor = keywordPrefs.edit()
        var changed = false
        keys.forEach { key ->
            val current = index[key].orEmpty()
            val next = current.filterNot { it.equals(keyword, ignoreCase = true) }.toSet()
            if (next != current) {
                changed = true
                if (next.isEmpty()) {
                    index.remove(key)
                    editor.remove("kw_$key")
                } else {
                    index[key] = next
                    editor.putStringSet("kw_$key", next)
                }
            }
        }
        if (changed) {
            editor.apply()
            appendOrganizerHistory("移除關鍵字「${keyword.trim()}」自 ${keys.size} 項")
        }
    }

    fun matchesKeyword(key: String, query: String): Boolean {
        val needle = query.trim()
        if (needle.isBlank()) return false
        return keywordsFor(key).any { it.contains(needle, ignoreCase = true) }
    }

    fun organizerHistory(): List<String> =
        organizerPrefs.getString("history", "")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(80)
            .toList()

    private fun appendOrganizerHistory(message: String) {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.TAIWAN))
        val previous = organizerHistory()
        val next = (listOf("$stamp　$message") + previous).take(80)
        organizerPrefs.edit().putString("history", next.joinToString("\n")).apply()
    }
    fun removeMediaReferences(keys: Set<String>) {
        if (keys.isEmpty()) return

        customAlbums().forEach { album ->
            val remaining = album.mediaKeys - keys
            if (remaining.size != album.mediaKeys.size) {
                albumPrefs.edit().putStringSet("keys_${album.id}", remaining).apply()
            }
        }

        val archived = archivedKeys().toMutableSet()
        archived.removeAll(keys)
        archivePrefs.edit().putStringSet("archived", archived).apply()

        val favorites = favoritePrefs.getStringSet("favorites", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        favorites.removeAll(keys)
        favoritePrefs.edit().putStringSet("favorites", favorites).apply()

        val index = keywordIndex()
        val keywordEditor = keywordPrefs.edit()
        keys.forEach { key ->
            index.remove(key)
            keywordEditor.remove("kw_$key")
        }
        keywordEditor.apply()
    }

    suspend fun updateImportedDeviceIndex(media: List<MediaItem>) = withContext(Dispatchers.IO) {
        val images = media.filter { item ->
            if (item.kind != MediaKind.IMAGE) return@filter false
            when (smartSourceTag(item)) {
                "screenshot", "line", "instagram", "downloads" -> false
                else -> true
            }
        }

        val editor = devicePrefs.edit()
        var changed = false

        images.forEach { item ->
            if (freshDeviceIdentity(item) != null) return@forEach
            val identity = readCameraIdentity(item)
            val make = identity?.first.orEmpty().replace("|", " ").trim()
            val model = identity?.second.orEmpty().replace("|", " ").trim()
            val encoded = if (make.isBlank() && model.isBlank()) {
                "${item.dateModified}|NONE|NONE"
            } else {
                "${item.dateModified}|$make|$model"
            }
            editor.putString("device_${item.key}", encoded)
            changed = true
        }
        if (changed) editor.apply()

        val currentCandidates = images
            .asSequence()
            .sortedByDescending { it.wallTime }
            .take(300)
            .mapNotNull { item ->
                val identity = cachedDeviceIdentity(item) ?: return@mapNotNull null
                val make = identity.first.trim()
                val model = identity.second.trim()
                if (make.isBlank() && model.isBlank()) return@mapNotNull null
                Triple(make, model, smartSourceTag(item) == "camera")
            }
            .toList()

        val manufacturer = Build.MANUFACTURER.orEmpty().trim().lowercase(Locale.ROOT)
        val buildModel = Build.MODEL.orEmpty().trim().lowercase(Locale.ROOT)

        fun score(candidate: Triple<String, String, Boolean>): Int {
            val make = candidate.first.lowercase(Locale.ROOT)
            val model = candidate.second.lowercase(Locale.ROOT)
            var value = 0
            if (candidate.third) value += 3
            if (manufacturer.isNotBlank() && make.contains(manufacturer)) value += 5
            if (
                buildModel.isNotBlank() &&
                (model.contains(buildModel) || buildModel.contains(model))
            ) {
                value += 10
            }
            return value
        }

        val scored = currentCandidates.filter { score(it) > 0 }
        val current = scored
            .groupBy { it.first.trim().lowercase(Locale.ROOT) + "|" + it.second.trim().lowercase(Locale.ROOT) }
            .maxByOrNull { (_, entries) ->
                entries.size * 20 + entries.maxOfOrNull { score(it) }.orEmptyInt()
            }
            ?.value
            ?.firstOrNull()

        if (current != null) {
            devicePrefs.edit()
                .putString("current_make", current.first)
                .putString("current_model", current.second)
                .apply()
        }
    }

    fun importedFromOtherDeviceKeys(media: List<MediaItem>): Set<String> {
        val currentMake = devicePrefs.getString("current_make", "").orEmpty().trim()
        val currentModel = devicePrefs.getString("current_model", "").orEmpty().trim()
        val currentMakeNorm = currentMake.lowercase(Locale.ROOT)
        val currentModelNorm = currentModel.lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.orEmpty().trim().lowercase(Locale.ROOT)

        return media.asSequence()
            .filter { it.kind == MediaKind.IMAGE }
            .filter {
                when (smartSourceTag(it)) {
                    "screenshot", "line", "instagram", "downloads" -> false
                    else -> true
                }
            }
            .filter { item ->
                val identity = cachedDeviceIdentity(item) ?: return@filter false
                val make = identity.first.trim()
                val model = identity.second.trim()
                if (make.isBlank() && model.isBlank()) return@filter false

                val makeNorm = make.lowercase(Locale.ROOT)
                val modelNorm = model.lowercase(Locale.ROOT)

                if (currentModelNorm.isNotBlank()) {
                    modelNorm.isNotBlank() && modelNorm != currentModelNorm
                } else if (currentMakeNorm.isNotBlank()) {
                    makeNorm.isNotBlank() && makeNorm != currentMakeNorm
                } else {
                    makeNorm.isNotBlank() && manufacturer.isNotBlank() && makeNorm != manufacturer
                }
            }
            .map { it.key }
            .toSet()
    }

    private fun freshDeviceIdentity(item: MediaItem): String? {
        val raw = devicePrefs.getString("device_${item.key}", null) ?: return null
        val parts = raw.split("|", limit = 3)
        if (parts.size != 3 || parts[0].toLongOrNull() != item.dateModified) return null
        return raw
    }

    private fun cachedDeviceIdentity(item: MediaItem): Pair<String, String>? {
        val raw = freshDeviceIdentity(item) ?: return null
        val parts = raw.split("|", limit = 3)
        if (parts[1] == "NONE" && parts[2] == "NONE") return null
        return parts[1] to parts[2]
    }

    private fun readCameraIdentity(item: MediaItem): Pair<String, String>? {
        return try {
            resolver.openInputStream(item.uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
                if (make.isBlank() && model.isBlank()) null else make to model
            }
        } catch (_: Exception) {
            null
        }
    }

    fun collectionOrder(defaultIds: List<String>): List<String> {
        val saved = collectionPrefs.getString("order", "").orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
        val valid = saved.filter { defaultIds.contains(it) }.toMutableList()
        defaultIds.forEach { if (!valid.contains(it)) valid.add(it) }
        return valid
    }

    fun saveCollectionOrder(ids: List<String>) {
        collectionPrefs.edit().putString("order", ids.joinToString("|")).apply()
    }

    fun archivedKeys(): Set<String> =
        archivePrefs.getStringSet("archived", emptySet())?.toSet() ?: emptySet()

    fun isArchived(key: String): Boolean =
        archivePrefs.getStringSet("archived", emptySet())?.contains(key) == true

    fun setArchived(keys: Set<String>, value: Boolean) {
        if (keys.isEmpty()) return
        val current = archivePrefs.getStringSet("archived", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        if (value) current.addAll(keys) else current.removeAll(keys)
        archivePrefs.edit().putStringSet("archived", current).apply()
    }

    private fun freshGpsCacheValue(item: MediaItem): String? {
        val cached = gpsPrefs.getString("gps_${item.key}", null) ?: return null
        val parts = cached.split("|")
        if (parts.size != 3 || parts[0].toLongOrNull() != item.dateModified) return null
        return cached
    }

    fun hasFreshGpsCache(item: MediaItem): Boolean = freshGpsCacheValue(item) != null

    fun cachedGpsFor(item: MediaItem): Pair<Double, Double>? {
        val cached = freshGpsCacheValue(item) ?: return null
        val parts = cached.split("|")
        if (parts[1] == "NONE") return null
        val lat = parts[1].toDoubleOrNull()
        val lon = parts[2].toDoubleOrNull()
        return if (lat != null && lon != null) lat to lon else null
    }

    fun hasAnyGpsCache(): Boolean =
        gpsPrefs.all.keys.any { it.startsWith("gps_") }

    fun isMapIndexComplete(media: List<MediaItem>): Boolean {
        if (media.isEmpty()) return true
        val signature = mapIndexSignature(media)
        if (gpsPrefs.getString("map_index_signature", null) == signature) return true

        // alpha7 already cached GPS item-by-item. If every item is fresh, adopt that
        // cache as the completed index without making the user rebuild it once more.
        if (media.all { hasFreshGpsCache(it) }) {
            gpsPrefs.edit().putString("map_index_signature", signature).apply()
            return true
        }
        return false
    }

    fun markMapIndexComplete(media: List<MediaItem>) {
        gpsPrefs.edit()
            .putString("map_index_signature", mapIndexSignature(media))
            .apply()
    }

    private fun mapIndexSignature(media: List<MediaItem>): String {
        var hash = 1125899906842597L
        media.sortedBy { it.key }.forEach { item ->
            hash = 31L * hash + item.key.hashCode().toLong()
            hash = 31L * hash + item.dateModified
        }
        return media.size.toString() + ":" + hash.toString()
    }

    suspend fun gpsFor(item: MediaItem): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (hasFreshGpsCache(item)) {
            return@withContext cachedGpsFor(item)
        }

        val detail = readDetail(item)
        val lat = detail.lat
        val lon = detail.lon
        val value = if (lat != null && lon != null) {
            "${item.dateModified}|${lat}|${lon}"
        } else {
            "${item.dateModified}|NONE|NONE"
        }
        gpsPrefs.edit().putString("gps_${item.key}", value).apply()
        if (lat != null && lon != null) lat to lon else null
    }

    fun hasSearchPlaceCache(item: MediaItem): Boolean {
        val raw = placeSearchPrefs.getString("item_${item.key}", null) ?: return false
        val split = raw.indexOf('|')
        if (split <= 0) return false
        val modified = raw.substring(0, split).toLongOrNull() ?: return false
        return modified == item.dateModified
    }

    fun cachedSearchPlace(item: MediaItem): String? {
        val raw = placeSearchPrefs.getString("item_${item.key}", null) ?: return null
        val split = raw.indexOf('|')
        if (split <= 0) return null

        val modified = raw.substring(0, split).toLongOrNull() ?: return null
        if (modified != item.dateModified) return null

        val value = raw.substring(split + 1)
        return value.takeIf { it.isNotBlank() && it != "NONE" }
    }

    fun cachedSearchPlaces(items: List<MediaItem>): Map<String, String> {
        return buildMap {
            items.forEach { item ->
                cachedSearchPlace(item)?.let { place ->
                    put(item.key, place)
                }
            }
        }
    }

    suspend fun searchPlaceFor(item: MediaItem): String? = withContext(Dispatchers.IO) {
        if (hasSearchPlaceCache(item)) {
            return@withContext cachedSearchPlace(item)
        }

        val gps = gpsFor(item)
        if (gps == null) {
            placeSearchPrefs.edit()
                .putString("item_${item.key}", "${item.dateModified}|NONE")
                .apply()
            return@withContext null
        }

        val place = resolveSearchPlace(gps.first, gps.second)
            ?: return@withContext null

        placeSearchPrefs.edit()
            .putString("item_${item.key}", "${item.dateModified}|$place")
            .apply()
        place
    }

    private fun resolveSearchPlace(lat: Double, lon: Double): String? {
        // 地點搜尋只需要城市／行政區等文字，比精密座標容許更粗的快取。
        // 約 0.001 度可大幅減少同一景點連拍照片的重複反查。
        val cacheKey = String.format(Locale.US, "%.3f,%.3f", lat, lon)
        placeSearchCoordPrefs.getString(cacheKey, null)?.let { cached ->
            if (cached.isNotBlank()) return cached
        }

        val address = try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.TAIWAN)
                .getFromLocation(lat, lon, 1)
                ?.firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return null

        val parts = listOf(
            address.countryName,
            address.countryCode,
            address.adminArea,
            address.subAdminArea,
            address.locality,
            address.subLocality,
            address.thoroughfare,
            address.subThoroughfare,
            address.featureName
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()

        val place = parts.joinToString("・").takeIf { it.isNotBlank() }
        if (place != null) {
            placeSearchCoordPrefs.edit().putString(cacheKey, place).apply()
        }
        return place
    }

    suspend fun resolvePlace(item: MediaItem): String? = withContext(Dispatchers.IO) {
        val detail = readDetail(item)
        val lat = detail.lat ?: return@withContext null
        val lon = detail.lon ?: return@withContext null
        resolvePlace(lat, lon)
    }

    suspend fun resolvePlace(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val cacheKey = String.format(Locale.US, "%.5f,%.5f", lat, lon)
        placePrefs.getString(cacheKey, null)?.let { cached ->
            if (cached.isNotBlank()) return@withContext cached
        }

        val address = try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.TAIWAN)
                .getFromLocation(lat, lon, 1)
                ?.firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return@withContext null

        val countryCode = address.countryCode.orEmpty()
        val parts = if (countryCode.equals("TW", ignoreCase = true)) {
            listOf(
                address.adminArea,
                address.subLocality ?: address.subAdminArea ?: address.locality
            )
        } else {
            listOf(
                address.locality ?: address.subAdminArea ?: address.adminArea,
                address.countryName
            )
        }
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()

        val place = parts.joinToString("・").takeIf { it.isNotBlank() }
        if (place != null) {
            placePrefs.edit().putString(cacheKey, place).apply()
        }
        place
    }

    fun readDetail(item: MediaItem): DetailInfo {
        if (item.kind == MediaKind.VIDEO) {
            var lat: Double? = null
            var lon: Double? = null
            val rows = mutableListOf(
                ExifRow("檔名", item.name, "DISPLAY_NAME"),
                ExifRow("格式", item.mime, "MIME_TYPE"),
                ExifRow("影片長度", formatDuration(item.duration), "DURATION"),
                ExifRow(
                    "解析度",
                    item.width.toString() + " × " + item.height.toString(),
                    "WIDTH × HEIGHT"
                ),
                ExifRow("檔案大小", formatBytes(item.size), "SIZE")
            )

            try {
                val mediaUri = if (
                    Build.VERSION.SDK_INT >= 29 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_MEDIA_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    MediaStore.setRequireOriginal(item.uri)
                } else {
                    item.uri
                }

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, mediaUri)
                    val location = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_LOCATION
                    )
                    val parsed = parseIso6709Location(location)
                    if (parsed != null) {
                        lat = parsed.first
                        lon = parsed.second
                        rows.add(ExifRow("GPS 緯度", lat.toString(), "METADATA_KEY_LOCATION"))
                        rows.add(ExifRow("GPS 經度", lon.toString(), "METADATA_KEY_LOCATION"))
                    }
                } finally {
                    retriever.release()
                }
            } catch (_: Exception) {
            }

            return DetailInfo(
                item = item,
                make = "",
                model = "",
                lens = "",
                focal35 = "",
                aperture = "",
                exposure = "",
                iso = "",
                hasGps = lat != null && lon != null,
                lat = lat,
                lon = lon,
                altitude = null,
                rows = rows
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
            val exifUri = if (
                Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_MEDIA_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                MediaStore.setRequireOriginal(item.uri)
            } else {
                item.uri
            }

            resolver.openInputStream(exifUri)?.use { stream ->
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

    fun createFilenameWriteRequest(items: List<MediaItem>): PendingIntent? {
        if (items.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.createWriteRequest(
                resolver,
                items.map { it.uri }.distinct().take(2000)
            )
        } else {
            null
        }
    }

    fun createPhotoTimeWriteRequest(items: List<MediaItem>): PendingIntent? {
        if (items.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.createWriteRequest(
                resolver,
                items.map { it.uri }.distinct().take(2000)
            )
        } else {
            null
        }
    }

    fun createGpsWriteRequest(items: List<MediaItem>): PendingIntent? {
        if (items.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.createWriteRequest(
                resolver,
                items.map { it.uri }.distinct().take(2000)
            )
        } else {
            null
        }
    }

    fun previewGpsEdits(
        items: List<MediaItem>,
        currentLocations: Map<String, Pair<Double, Double>?>,
        target: Pair<Double, Double>?,
        remove: Boolean
    ): List<GpsEditPreview> {
        return items.map { item ->
            val current = currentLocations[item.key]
            val error = when {
                item.kind != MediaKind.IMAGE ->
                    "目前 GPS 位置修改只支援照片"
                !supportsExifTimeWrite(item) ->
                    "這個圖片格式暫不支援安全寫入 GPS"
                !remove && target == null ->
                    "尚未指定新位置"
                else -> null
            }

            val newLat = if (remove) null else target?.first
            val newLon = if (remove) null else target?.second
            val changed = if (error != null) {
                false
            } else if (remove) {
                current != null
            } else {
                val lat = newLat!!
                val lon = newLon!!
                current == null ||
                    kotlin.math.abs(current.first - lat) > 0.0000005 ||
                    kotlin.math.abs(current.second - lon) > 0.0000005
            }

            GpsEditPreview(
                item = item,
                currentLat = current?.first,
                currentLon = current?.second,
                newLat = newLat,
                newLon = newLon,
                remove = remove,
                error = error,
                changed = changed
            )
        }
    }

    fun applyGpsEdits(
        previews: List<GpsEditPreview>
    ): GpsEditResult {
        val runnable = previews.filter { it.error == null && it.changed }
        if (runnable.isEmpty()) {
            return GpsEditResult(
                total = previews.size,
                succeeded = 0,
                failed = previews.count { it.error != null },
                details = listOf("沒有需要修改的 GPS 位置。")
            )
        }

        var succeeded = 0
        var failed = 0
        val details = mutableListOf<String>()

        runnable.forEach { preview ->
            val item = preview.item
            try {
                writeImageGps(
                    item = item,
                    lat = preview.newLat,
                    lon = preview.newLon,
                    remove = preview.remove
                )

                gpsPrefs.edit()
                    .remove("gps_${item.key}")
                    .remove("map_index_signature")
                    .apply()
                placeSearchPrefs.edit()
                    .remove("item_${item.key}")
                    .apply()

                succeeded += 1
                val before = if (
                    preview.currentLat != null && preview.currentLon != null
                ) {
                    String.format(
                        Locale.US,
                        "%.6f, %.6f",
                        preview.currentLat,
                        preview.currentLon
                    )
                } else {
                    "無位置"
                }
                val after = if (preview.remove) {
                    "已移除"
                } else {
                    String.format(
                        Locale.US,
                        "%.6f, %.6f",
                        preview.newLat,
                        preview.newLon
                    )
                }
                details.add(item.name + "：" + before + " → " + after)
            } catch (security: SecurityException) {
                failed += 1
                details.add(item.name + "：系統尚未授權修改")
            } catch (e: Exception) {
                failed += 1
                details.add(
                    item.name + "：修改失敗（" + (e.message ?: "未知錯誤") + "）"
                )
            }
        }

        if (succeeded > 0) {
            appendOrganizerHistory(
                "修改 GPS 位置 " + succeeded + " 項" +
                    if (failed > 0) "（另有 " + failed + " 項失敗）" else ""
            )
        }

        return GpsEditResult(
            total = previews.size,
            succeeded = succeeded,
            failed = failed,
            details = details
        )
    }

    private fun writeImageGps(
        item: MediaItem,
        lat: Double?,
        lon: Double?,
        remove: Boolean
    ) {
        resolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            if (remove) {
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
            } else {
                require(lat != null && lon != null) { "沒有指定 GPS 座標" }
                require(lat in -90.0..90.0 && lon in -180.0..180.0) {
                    "GPS 座標超出範圍"
                }
                exif.setLatLong(lat, lon)
            }
            exif.saveAttributes()
        } ?: throw IllegalStateException("無法開啟照片")

        val verified = readDetail(item)
        if (remove) {
            if (verified.lat != null || verified.lon != null) {
                throw IllegalStateException("移除後驗證失敗")
            }
        } else {
            val actualLat = verified.lat
            val actualLon = verified.lon
            if (
                actualLat == null || actualLon == null ||
                kotlin.math.abs(actualLat - lat!!) > 0.00001 ||
                kotlin.math.abs(actualLon - lon!!) > 0.00001
            ) {
                throw IllegalStateException(
                    "修改後驗證失敗（實際：" +
                        if (actualLat != null && actualLon != null) {
                            String.format(Locale.US, "%.6f, %.6f", actualLat, actualLon)
                        } else {
                            "讀不到"
                        } +
                        "）"
                )
            }
        }
    }

    fun previewPhotoTimes(
        requests: List<Pair<MediaItem, LocalDateTime>>
    ): List<PhotoTimePreview> {
        return requests.map { (item, target) ->
            val error = when {
                item.kind == MediaKind.IMAGE && !supportsExifTimeWrite(item) ->
                    "這個圖片格式暫不支援安全寫入拍攝時間"
                item.kind == MediaKind.VIDEO && !supportsVideoTimeWrite(item) ->
                    "目前影片只支援 MP4／MOV"
                else -> null
            }
            PhotoTimePreview(
                item = item,
                currentTime = item.wallTime,
                newTime = target,
                error = error,
                changed = item.wallTime != target
            )
        }
    }

    fun previewFilenamePhotoTimes(
        items: List<MediaItem>
    ): List<PhotoTimePreview> {
        return items.map { item ->
            when {
                item.kind == MediaKind.IMAGE && !supportsExifTimeWrite(item) -> {
                    PhotoTimePreview(
                        item = item,
                        currentTime = item.wallTime,
                        newTime = item.wallTime,
                        error = "這個圖片格式暫不支援安全寫入拍攝時間",
                        changed = false
                    )
                }
                item.kind == MediaKind.VIDEO && !supportsVideoTimeWrite(item) -> {
                    PhotoTimePreview(
                        item = item,
                        currentTime = item.wallTime,
                        newTime = item.wallTime,
                        error = "目前影片只支援 MP4／MOV",
                        changed = false
                    )
                }
                else -> {
                    val filenameTime = parseFilenameTimeStrict(item.name)
                    if (filenameTime == null) {
                        PhotoTimePreview(
                            item = item,
                            currentTime = item.wallTime,
                            newTime = item.wallTime,
                            error = "無完整檔名時間",
                            changed = false
                        )
                    } else {
                        PhotoTimePreview(
                            item = item,
                            currentTime = item.wallTime,
                            newTime = filenameTime,
                            error = null,
                            changed = item.wallTime != filenameTime
                        )
                    }
                }
            }
        }
    }

    fun previewGpsPhotoTimes(
        items: List<MediaItem>
    ): List<PhotoTimePreview> {
        val timeZoneCache = mutableMapOf<String, String?>()

        return items.map { item ->
            when {
                item.kind == MediaKind.IMAGE && !supportsExifTimeWrite(item) -> {
                    PhotoTimePreview(
                        item = item,
                        currentTime = item.wallTime,
                        newTime = item.wallTime,
                        error = "這個圖片格式暫不支援安全寫入拍攝時間",
                        changed = false
                    )
                }
                item.kind == MediaKind.VIDEO -> {
                    PhotoTimePreview(
                        item = item,
                        currentTime = item.wallTime,
                        newTime = item.wallTime,
                        error = "無 GPS 時間－略過",
                        changed = false
                    )
                }
                else -> {
                    val gps = readGpsCaptureData(item)

                    if (gps.utcTime == null) {
                        PhotoTimePreview(
                            item = item,
                            currentTime = item.wallTime,
                            newTime = item.wallTime,
                            error = if (item.kind == MediaKind.VIDEO)
                                "無影片 UTC 時間"
                            else
                                "無 GPS 時間",
                            changed = false
                        )
                    } else if (gps.lat == null || gps.lon == null) {
                        PhotoTimePreview(
                            item = item,
                            currentTime = item.wallTime,
                            newTime = item.wallTime,
                            error = "無 GPS 座標",
                            changed = false
                        )
                    } else {
                        val coordinateKey = String.format(
                            Locale.US,
                            "%.4f,%.4f",
                            gps.lat,
                            gps.lon
                        )

                        val zoneId = if (timeZoneCache.containsKey(coordinateKey)) {
                            timeZoneCache[coordinateKey]
                        } else {
                            val lookedUp = lookupTimeZoneId(gps.lat, gps.lon)
                            timeZoneCache[coordinateKey] = lookedUp
                            lookedUp
                        }

                        if (zoneId.isNullOrBlank()) {
                            PhotoTimePreview(
                                item = item,
                                currentTime = item.wallTime,
                                newTime = item.wallTime,
                                error = "無法取得拍攝地時區",
                                changed = false
                            )
                        } else {
                            val localTime = try {
                                gps.utcTime
                                    .atZone(java.time.ZoneOffset.UTC)
                                    .withZoneSameInstant(ZoneId.of(zoneId))
                                    .toLocalDateTime()
                            } catch (_: Exception) {
                                null
                            }

                            if (localTime == null) {
                                PhotoTimePreview(
                                    item = item,
                                    currentTime = item.wallTime,
                                    newTime = item.wallTime,
                                    error = "拍攝地時區無法換算",
                                    changed = false
                                )
                            } else {
                                PhotoTimePreview(
                                    item = item,
                                    currentTime = item.wallTime,
                                    newTime = localTime,
                                    error = null,
                                    changed = item.wallTime != localTime
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private data class GpsCaptureData(
        val utcTime: LocalDateTime?,
        val lat: Double?,
        val lon: Double?
    )

    private fun readGpsCaptureData(item: MediaItem): GpsCaptureData {
        return readImageGpsCaptureData(item)
    }

    private fun originalMediaUri(item: MediaItem): Uri {
        return if (
            Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            MediaStore.setRequireOriginal(item.uri)
        } else {
            item.uri
        }
    }

    private fun readImageGpsCaptureData(item: MediaItem): GpsCaptureData {
        return try {
            resolver.openInputStream(originalMediaUri(item))?.use { stream ->
                val exif = ExifInterface(stream)
                val gpsMillis = exif.gpsDateTime
                val utcTime =
                    if (gpsMillis == null || gpsMillis <= 0L) {
                        null
                    } else {
                        LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(gpsMillis),
                            java.time.ZoneOffset.UTC
                        )
                    }

                val ll = FloatArray(2)
                val hasLocation = exif.getLatLong(ll)

                GpsCaptureData(
                    utcTime = utcTime,
                    lat = if (hasLocation) ll[0].toDouble() else null,
                    lon = if (hasLocation) ll[1].toDouble() else null
                )
            } ?: GpsCaptureData(null, null, null)
        } catch (_: Exception) {
            GpsCaptureData(null, null, null)
        }
    }


    private fun lookupTimeZoneId(
        lat: Double,
        lon: Double
    ): String? {
        val url = URL(
            "https://timeapi.io/api/TimeZone/coordinate" +
                "?latitude=" + String.format(Locale.US, "%.6f", lat) +
                "&longitude=" + String.format(Locale.US, "%.6f", lon)
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AjoAlbum/" + BuildConfig.VERSION_NAME)
        }

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val body = connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val zone = JSONObject(body)
                    .optString("timeZone")
                    .trim()

                if (zone.isBlank()) null else zone
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun applyPhotoTimes(
        previews: List<PhotoTimePreview>
    ): PhotoTimeResult {
        val runnable = previews.filter { it.error == null && it.changed }
        if (runnable.isEmpty()) {
            return PhotoTimeResult(
                total = previews.size,
                succeeded = 0,
                failed = previews.count { it.error != null },
                details = listOf("沒有需要修改的拍攝時間。")
            )
        }

        var succeeded = 0
        var failed = 0
        val details = mutableListOf<String>()
        val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

        runnable.forEach { preview ->
            val item = preview.item
            val target = preview.newTime
            try {
                when (item.kind) {
                    MediaKind.IMAGE -> writeImageCaptureTime(item, target)
                    MediaKind.VIDEO -> writeVideoCaptureTime(item, target)
                }

                try {
                    val dateTaken = target
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    resolver.update(
                        item.uri,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
                        },
                        null,
                        null
                    )
                } catch (_: Exception) {
                    // 檔案 metadata 已成功；MediaStore 索引可在重新掃描時更新。
                }

                if (item.kind == MediaKind.IMAGE) {
                    val modified = currentModifiedMillis(item)
                    if (modified > 0L) {
                        saveIndex(item.key, modified, target)
                    } else {
                        timeIndexPrefs.edit().remove(cacheKey(item.key)).apply()
                    }
                }

                succeeded += 1
                details.add(
                    item.name + "：" +
                        preview.currentTime.format(displayFormatter) +
                        " → " + target.format(displayFormatter)
                )
            } catch (security: SecurityException) {
                failed += 1
                details.add(item.name + "：系統尚未授權修改")
            } catch (e: Exception) {
                failed += 1
                details.add(
                    item.name + "：修改失敗（" + (e.message ?: "未知錯誤") + "）"
                )
            }
        }

        if (succeeded > 0) {
            appendOrganizerHistory(
                "修改拍攝時間 " + succeeded + " 項" +
                    if (failed > 0) "（另有 " + failed + " 項失敗）" else ""
            )
        }

        return PhotoTimeResult(
            total = previews.size,
            succeeded = succeeded,
            failed = failed,
            details = details
        )
    }

    private fun writeImageCaptureTime(
        item: MediaItem,
        target: LocalDateTime
    ) {
        resolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            val value = target.format(exifFormatter)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, value)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, value)
            exif.setAttribute(ExifInterface.TAG_DATETIME, value)
            exif.saveAttributes()
        } ?: throw IllegalStateException("無法開啟照片")

        val verified = readOriginalTime(item.uri)
        if (verified != target) {
            val actual = verified?.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
            ) ?: "讀不到"
            throw IllegalStateException("修改後驗證失敗（實際：" + actual + "）")
        }
    }

    private fun supportsVideoTimeWrite(item: MediaItem): Boolean {
        val mime = item.mime.lowercase(Locale.ROOT)
        val name = item.name.lowercase(Locale.ROOT)
        return mime == "video/mp4" ||
            mime == "video/quicktime" ||
            name.endsWith(".mp4") ||
            name.endsWith(".mov")
    }

    private fun writeVideoCaptureTime(
        item: MediaItem,
        target: LocalDateTime
    ) {
        val safeKey = item.key.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val originalFile = File(context.cacheDir, "video_time_" + safeKey + "_original")
        val editedFile = File(context.cacheDir, "video_time_" + safeKey + "_edited")

        originalFile.delete()
        editedFile.delete()

        try {
            resolver.openInputStream(item.uri)?.use { input ->
                FileOutputStream(originalFile).use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            } ?: throw IllegalStateException("無法讀取影片")

            originalFile.copyTo(editedFile, overwrite = true)

            val patched = patchMp4ContainerTimes(editedFile, target)
            if (patched <= 0) {
                throw IllegalStateException("找不到可修改的影片時間欄位")
            }

            val verified = readMp4CreationTime(editedFile)
            if (verified != target) {
                val actual = verified?.format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
                ) ?: "讀不到"
                throw IllegalStateException(
                    "影片容器驗證失敗（實際：" + actual + "）"
                )
            }

            try {
                writeFileBackToUri(editedFile, item.uri)
            } catch (writeError: Exception) {
                try {
                    writeFileBackToUri(originalFile, item.uri)
                } catch (_: Exception) {
                }
                throw writeError
            }
        } finally {
            originalFile.delete()
            editedFile.delete()
        }
    }

    private fun patchMp4ContainerTimes(
        file: File,
        target: LocalDateTime
    ): Int {
        val mp4Seconds = target
            .atZone(ZoneId.systemDefault())
            .toEpochSecond() + MP4_EPOCH_OFFSET_SECONDS

        RandomAccessFile(file, "rw").use { raf ->
            val patched = patchMp4Boxes(
                raf = raf,
                start = 0L,
                end = raf.length(),
                mp4Seconds = mp4Seconds
            )
            if (patched <= 0) {
                throw IllegalStateException("找不到 MP4／MOV 建立時間欄位")
            }
            return patched
        }
    }

    private fun patchMp4Boxes(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
        mp4Seconds: Long
    ): Int {
        var position = start
        var patched = 0

        while (position + 8L <= end) {
            raf.seek(position)
            val size32 = readUnsignedInt(raf)
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            val type = String(typeBytes, Charsets.ISO_8859_1)

            var headerSize = 8L
            val boxSize = when (size32) {
                0L -> end - position
                1L -> {
                    if (position + 16L > end) break
                    headerSize = 16L
                    raf.readLong()
                }
                else -> size32
            }

            if (boxSize < headerSize || boxSize <= 0L) break
            val boxEnd = position + boxSize
            if (boxEnd < position || boxEnd > end) break

            val contentStart = position + headerSize

            when (type) {
                "mvhd", "tkhd", "mdhd" -> {
                    if (writeMp4FullBoxTimes(
                            raf = raf,
                            contentStart = contentStart,
                            boxEnd = boxEnd,
                            mp4Seconds = mp4Seconds
                        )
                    ) {
                        patched += 1
                    }
                }
                "moov", "trak", "mdia" -> {
                    patched += patchMp4Boxes(
                        raf = raf,
                        start = contentStart,
                        end = boxEnd,
                        mp4Seconds = mp4Seconds
                    )
                }
            }

            position = boxEnd
        }

        return patched
    }

    private fun writeMp4FullBoxTimes(
        raf: RandomAccessFile,
        contentStart: Long,
        boxEnd: Long,
        mp4Seconds: Long
    ): Boolean {
        if (contentStart + 12L > boxEnd) return false

        raf.seek(contentStart)
        val version = raf.readUnsignedByte()
        raf.skipBytes(3)

        return when (version) {
            0 -> {
                if (mp4Seconds !in 0L..0xFFFF_FFFFL) {
                    throw IllegalStateException("指定時間超出這支影片可表示的範圍")
                }
                if (contentStart + 12L > boxEnd) return false
                raf.writeInt(mp4Seconds.toInt())
                raf.writeInt(mp4Seconds.toInt())
                true
            }
            1 -> {
                if (contentStart + 20L > boxEnd) return false
                raf.writeLong(mp4Seconds)
                raf.writeLong(mp4Seconds)
                true
            }
            else -> false
        }
    }

    private fun readMp4CreationTime(file: File): LocalDateTime? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val seconds = findMp4CreationSeconds(
                    raf = raf,
                    start = 0L,
                    end = raf.length()
                ) ?: return null
                val unixSeconds = seconds - MP4_EPOCH_OFFSET_SECONDS
                LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(unixSeconds),
                    ZoneId.systemDefault()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findMp4CreationSeconds(
        raf: RandomAccessFile,
        start: Long,
        end: Long
    ): Long? {
        var position = start

        while (position + 8L <= end) {
            raf.seek(position)
            val size32 = readUnsignedInt(raf)
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            val type = String(typeBytes, Charsets.ISO_8859_1)

            var headerSize = 8L
            val boxSize = when (size32) {
                0L -> end - position
                1L -> {
                    if (position + 16L > end) return null
                    headerSize = 16L
                    raf.readLong()
                }
                else -> size32
            }

            if (boxSize < headerSize || boxSize <= 0L) return null
            val boxEnd = position + boxSize
            if (boxEnd < position || boxEnd > end) return null
            val contentStart = position + headerSize

            if (type == "mvhd") {
                if (contentStart + 12L > boxEnd) return null
                raf.seek(contentStart)
                val version = raf.readUnsignedByte()
                raf.skipBytes(3)
                return when (version) {
                    0 -> readUnsignedInt(raf)
                    1 -> if (contentStart + 20L <= boxEnd) raf.readLong() else null
                    else -> null
                }
            }

            if (type == "moov") {
                val nested = findMp4CreationSeconds(
                    raf = raf,
                    start = contentStart,
                    end = boxEnd
                )
                if (nested != null) return nested
            }

            position = boxEnd
        }

        return null
    }

    private fun readUnsignedInt(raf: RandomAccessFile): Long {
        return raf.readInt().toLong() and 0xFFFF_FFFFL
    }

    private fun writeFileBackToUri(file: File, uri: Uri) {
        val pfd = resolver.openFileDescriptor(uri, "rw")
            ?: throw IllegalStateException("無法開啟影片寫入")
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { output ->
            FileInputStream(file).use { input ->
                output.channel.truncate(0)
                val written = input.copyTo(output, 1024 * 1024)
                output.flush()
                if (written != file.length()) {
                    throw IllegalStateException("影片寫回不完整")
                }
            }
        }
    }

    private fun supportsExifTimeWrite(item: MediaItem): Boolean {
        val mime = item.mime.lowercase(Locale.ROOT)
        return mime == "image/jpeg" ||
            mime == "image/jpg" ||
            mime == "image/png" ||
            mime == "image/webp"
    }

    private fun currentModifiedMillis(item: MediaItem): Long {
        return try {
            resolver.query(
                item.uri,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.longOrZero(0) * 1000L else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun previewFilenameRenames(
        requests: List<Pair<MediaItem, String>>
    ): List<FilenameRenamePreview> {
        val locationCache = mutableMapOf<String, String?>()
        val targetKeys = mutableMapOf<String, Int>()

        val initial = requests.map { (item, rawName) ->
            val newName = rawName.trim()
            val error = validateFilename(item, newName)
            val location = locationCache.getOrPut(item.key) { relativePathFor(item) }
                ?: item.folderHint
            val duplicateKey = location.lowercase(Locale.ROOT) + "|" +
                newName.lowercase(Locale.ROOT)
            if (error == null) {
                targetKeys[duplicateKey] = (targetKeys[duplicateKey] ?: 0) + 1
            }
            FilenameRenamePreview(
                item = item,
                newName = newName,
                error = error,
                changed = newName != item.name
            )
        }

        return initial.map { preview ->
            if (preview.error != null || !preview.changed) {
                preview
            } else {
                val location = locationCache[preview.item.key] ?: preview.item.folderHint
                val duplicateKey = location.lowercase(Locale.ROOT) + "|" +
                    preview.newName.lowercase(Locale.ROOT)
                val internalDuplicate = (targetKeys[duplicateKey] ?: 0) > 1
                val existingConflict = if (!internalDuplicate) {
                    mediaNameExists(
                        item = preview.item,
                        candidate = preview.newName,
                        relativePath = locationCache[preview.item.key]
                    )
                } else {
                    false
                }
                when {
                    internalDuplicate -> preview.copy(error = "批次中有重複的新檔名")
                    existingConflict -> preview.copy(error = "同一資料夾已有相同檔名")
                    else -> preview
                }
            }
        }
    }

    fun applyFilenameRenames(
        previews: List<FilenameRenamePreview>
    ): FilenameRenameResult {
        val runnable = previews.filter {
            it.error == null && it.changed
        }
        if (runnable.isEmpty()) {
            return FilenameRenameResult(
                total = previews.size,
                succeeded = 0,
                failed = previews.count { it.error != null },
                details = listOf("沒有需要修改的檔名。")
            )
        }

        var succeeded = 0
        var failed = 0
        val details = mutableListOf<String>()

        runnable.forEach { preview ->
            val item = preview.item
            val newName = preview.newName
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                val changedRows = resolver.update(item.uri, values, null, null)
                if (changedRows <= 0) {
                    failed += 1
                    details.add(item.name + "：修改失敗（系統沒有接受修改）")
                    return@forEach
                }

                val verified = resolver.query(
                    item.uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }

                if (verified == newName) {
                    succeeded += 1
                    details.add(item.name + " → " + newName)
                } else {
                    failed += 1
                    val actualText = verified?.takeIf { it.isNotBlank() }
                        ?.let { "（系統實際檔名：" + it + "）" }
                        .orEmpty()
                    details.add(item.name + "：修改後驗證失敗" + actualText)
                }
            } catch (security: SecurityException) {
                failed += 1
                details.add(item.name + "：系統尚未授權修改")
            } catch (e: Exception) {
                failed += 1
                details.add(item.name + "：修改失敗（" + (e.message ?: "未知錯誤") + "）")
            }
        }

        if (succeeded > 0) {
            appendOrganizerHistory(
                "重新命名 " + succeeded + " 項" +
                    if (failed > 0) "（另有 " + failed + " 項失敗）" else ""
            )
        }

        return FilenameRenameResult(
            total = previews.size,
            succeeded = succeeded,
            failed = failed,
            details = details
        )
    }

    private fun validateFilename(item: MediaItem, newName: String): String? {
        if (newName.isBlank()) return "檔名不能空白"
        if (newName == "." || newName == "..") return "這個檔名不能使用"
        if (newName.startsWith(".")) return "檔名不能以「.」開頭"
        if (newName.endsWith(".") || newName.endsWith(" ")) {
            return "檔名不能以句點或空白結尾"
        }
        if (newName.any { it.code < 32 }) return "檔名含有控制字元"
        if (newName.any { it in charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|') }) {
            return "檔名含有不能使用的字元"
        }
        if (newName.toByteArray(Charsets.UTF_8).size > 240) {
            return "檔名太長"
        }

        val originalExtension = extensionOf(item.name)
        val newExtension = extensionOf(newName)
        if (!originalExtension.equals(newExtension, ignoreCase = true)) {
            return if (originalExtension.isBlank()) {
                "原檔沒有副檔名，不能新增副檔名"
            } else {
                "副檔名必須保留為 .$originalExtension"
            }
        }

        val base = baseNameOf(newName)
        if (base.isBlank()) return "主檔名不能空白"
        return null
    }

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.lastIndex) name.substring(dot + 1) else ""
    }

    private fun baseNameOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length) name.substring(0, dot) else name
    }

    private fun relativePathFor(item: MediaItem): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            resolver.query(
                item.uri,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mediaNameExists(
        item: MediaItem,
        candidate: String,
        relativePath: String?
    ): Boolean {
        return try {
            val filesUri = if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val selection: String
            val args: Array<String>
            if (Build.VERSION.SDK_INT >= 29 && !relativePath.isNullOrBlank()) {
                selection =
                    MediaStore.MediaColumns.DISPLAY_NAME + " = ? COLLATE NOCASE AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + " = ? AND " +
                    MediaStore.Files.FileColumns._ID + " <> ?"
                args = arrayOf(candidate, relativePath, item.id.toString())
            } else {
                selection =
                    MediaStore.MediaColumns.DISPLAY_NAME + " = ? COLLATE NOCASE AND " +
                    MediaStore.Files.FileColumns._ID + " <> ?"
                args = arrayOf(candidate, item.id.toString())
            }

            resolver.query(
                filesUri,
                arrayOf(MediaStore.Files.FileColumns._ID),
                selection,
                args,
                null
            )?.use { it.moveToFirst() } == true
        } catch (_: Exception) {
            false
        }
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
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
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
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
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
                val folderHint = if (hasDuration) {
                    cursor.stringOrEmpty(10)
                } else {
                    cursor.stringOrEmpty(9)
                }
                val itemUri = ContentUris.withAppendedId(baseUri, id)
                val key = kind.name + ":" + id.toString()

                val cached = if (kind == MediaKind.IMAGE) readIndexedTime(key, modified) else null
                val filenameTime = parseFilenameTime(name)
                val mediaStoreTaken = if (dateTaken > 0L) {
                    LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(dateTaken),
                        ZoneId.systemDefault()
                    )
                } else {
                    null
                }
                val fallbackMillis = when {
                    modified > 0L -> modified
                    added > 0L -> added
                    else -> System.currentTimeMillis()
                }
                val fallback = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(fallbackMillis),
                    ZoneId.systemDefault()
                )
                val effective = when {
                    kind == MediaKind.VIDEO && mediaStoreTaken != null -> mediaStoreTaken
                    cached != null -> cached
                    filenameTime != null -> filenameTime
                    mediaStoreTaken != null -> mediaStoreTaken
                    else -> fallback
                }
                val source = when {
                    kind == MediaKind.VIDEO && mediaStoreTaken != null -> "Android MediaStore 拍攝時間"
                    cached != null -> "EXIF 原始拍攝時間"
                    filenameTime != null -> "檔名時間"
                    mediaStoreTaken != null -> "Android MediaStore 拍攝時間"
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
                        folderHint = folderHint,
                        wallTime = effective,
                        timeSource = source
                    )
                )
            }
        }
        return output
    }

    private fun Int?.orEmptyInt(): Int = this ?: 0

    private fun parseIso6709Location(raw: String?): Pair<Double, Double>? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null

        val match = Regex(
            """^([+-]\d{1,2}(?:\.\d+)?)([+-]\d{1,3}(?:\.\d+)?)(?:[+-]\d+(?:\.\d+)?)?/?$"""
        ).find(value) ?: return null

        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
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

    private fun parseFilenameTimeStrict(name: String): LocalDateTime? {
        val baseName = name.substringBeforeLast('.')
        val match = strictFilenameDateTime.find(baseName) ?: return null

        return try {
            LocalDateTime.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt(),
                match.groupValues[5].toInt(),
                match.groupValues[6].toInt()
            )
        } catch (_: Exception) {
            null
        }
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
