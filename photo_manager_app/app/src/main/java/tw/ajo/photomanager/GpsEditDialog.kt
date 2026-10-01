@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package tw.ajo.photomanager

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import java.util.Locale

private enum class GpsEditMode(
    val label: String,
    val description: String
) {
    SET_LOCATION(
        "設定／微調位置",
        "地圖拖曳微調；批次時全部設成同一位置"
    ),
    COPY_SOURCE(
        "從照片複製位置",
        "選一張有 GPS 的照片，把位置複製到其他已選照片"
    ),
    REMOVE(
        "移除 GPS 位置",
        "移除照片的經緯度與高度位置資料"
    )
}

@Composable
fun GpsEditDialog(
    selectedItems: List<MediaItem>,
    repository: AlbumRepository,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember(selectedItems.size) { mutableStateOf(GpsEditMode.SET_LOCATION) }
    var currentLocations by remember {
        mutableStateOf<Map<String, Pair<Double, Double>?>>(emptyMap())
    }
    var loadingCurrent by remember { mutableStateOf(true) }
    var coordinateText by remember { mutableStateOf("") }
    var originalPlace by remember { mutableStateOf<String?>(null) }
    var targetPlace by remember { mutableStateOf<String?>(null) }
    var selectedSourceKey by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var pendingApply by remember { mutableStateOf<List<GpsEditPreview>>(emptyList()) }
    var result by remember { mutableStateOf<GpsEditResult?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedItems.map { it.key }) {
        loadingCurrent = true
        originalPlace = null
        targetPlace = null

        val loaded = withContext(Dispatchers.IO) {
            selectedItems.associate { item ->
                item.key to repository.gpsFor(item)
            }
        }
        currentLocations = loaded

        val firstLocatedItem = selectedItems.firstOrNull { item ->
            item.kind == MediaKind.IMAGE && loaded[item.key] != null
        }
        val firstLocation = firstLocatedItem?.let { loaded[it.key] }

        if (firstLocation != null) {
            coordinateText = formatCoordinatePair(firstLocation)
            originalPlace = withContext(Dispatchers.IO) {
                repository.resolvePlace(firstLocation.first, firstLocation.second)
            }
        } else {
            coordinateText = ""
        }

        selectedSourceKey = selectedItems.firstOrNull { item ->
            item.kind == MediaKind.IMAGE && loaded[item.key] != null
        }?.key

        loadingCurrent = false
    }

    val sourceCandidates = remember(selectedItems, currentLocations) {
        selectedItems.filter { item ->
            item.kind == MediaKind.IMAGE && currentLocations[item.key] != null
        }
    }

    val manualTarget = remember(coordinateText) {
        parseCoordinatePair(coordinateText)
    }
    val sourceTarget = selectedSourceKey?.let { currentLocations[it] }
    val originalLocation = remember(selectedItems, currentLocations) {
        selectedItems
            .asSequence()
            .filter { it.kind == MediaKind.IMAGE }
            .mapNotNull { currentLocations[it.key] }
            .firstOrNull()
    }

    val target = when (mode) {
        GpsEditMode.SET_LOCATION -> manualTarget
        GpsEditMode.COPY_SOURCE -> sourceTarget
        GpsEditMode.REMOVE -> null
    }

    LaunchedEffect(mode, target?.first, target?.second) {
        targetPlace = null
        if (target != null) {
            delay(280)
            targetPlace = withContext(Dispatchers.IO) {
                repository.resolvePlace(target.first, target.second)
            }
        }
    }

    val previews = remember(
        selectedItems,
        currentLocations,
        target,
        mode,
        loadingCurrent
    ) {
        if (loadingCurrent) {
            emptyList()
        } else {
            repository.previewGpsEdits(
                items = selectedItems,
                currentLocations = currentLocations,
                target = target,
                remove = mode == GpsEditMode.REMOVE
            )
        }
    }

    val changedCount = previews.count { it.error == null && it.changed }
    val skippedCount = previews.count { it.error != null }
    val unchangedCount = previews.count { it.error == null && !it.changed }

    fun applyChanges(list: List<GpsEditPreview>) {
        processing = true
        notice = null
        scope.launch {
            val applied = withContext(Dispatchers.IO) {
                repository.applyGpsEdits(list)
            }
            result = applied
            processing = false
            pendingApply = emptyList()
            if (applied.succeeded > 0) {
                onChanged()
            }
        }
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val list = pendingApply
        if (activityResult.resultCode == Activity.RESULT_OK && list.isNotEmpty()) {
            applyChanges(list)
        } else {
            pendingApply = emptyList()
            notice = "已取消，GPS 位置沒有修改。"
        }
    }

    fun requestApply() {
        val changed = previews.filter { it.error == null && it.changed }
        if (changed.isEmpty()) return
        val request = repository.createGpsWriteRequest(changed.map { it.item })
        if (request == null) {
            applyChanges(changed)
        } else {
            pendingApply = changed
            writeLauncher.launch(
                IntentSenderRequest.Builder(request.intentSender).build()
            )
        }
    }

    val inputReady = !loadingCurrent &&
        when (mode) {
            GpsEditMode.SET_LOCATION -> manualTarget != null
            GpsEditMode.COPY_SOURCE -> sourceTarget != null
            GpsEditMode.REMOVE -> true
        }

    AlertDialog(
        onDismissRequest = {
            if (!processing) onDismiss()
        },
        title = {
            Column {
                Text(
                    if (selectedItems.size == 1)
                        "修改 GPS 位置"
                    else
                        "批次修改 GPS 位置"
                )
                Text(
                    "已選 " + selectedItems.size + " 項・修改前先預覽",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (result != null) {
                val gpsResult = result!!
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                Modifier.padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("GPS 位置修改完成")
                                Text(
                                    "成功 " + gpsResult.succeeded +
                                        "　失敗 " + gpsResult.failed,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    items(gpsResult.details.take(30)) { line ->
                        Text(
                            line,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 650.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("修改方式", fontSize = 13.sp)

                    val availableModes =
                        if (selectedItems.size > 1)
                            GpsEditMode.entries
                        else
                            listOf(GpsEditMode.SET_LOCATION, GpsEditMode.REMOVE)

                    availableModes.forEach { option ->
                        GpsModeCard(
                            option = option,
                            selected = mode == option,
                            onClick = { mode = option }
                        )

                        if (option == GpsEditMode.SET_LOCATION &&
                            mode == GpsEditMode.SET_LOCATION
                        ) {
                            if (loadingCurrent) {
                                Text(
                                    "正在讀取原本 GPS 位置…",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            } else {
                                GpsTargetMap(
                                    initialTarget = originalLocation,
                                    target = manualTarget,
                                    originalPlace = originalPlace,
                                    onTargetChange = { lat, lon ->
                                        coordinateText = formatCoordinatePair(lat to lon)
                                    }
                                )

                                Text(
                                    "拖曳底下的地圖，中央圖釘固定不動；用 ＋／－ 縮放。",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                OutlinedTextField(
                                    value = coordinateText,
                                    onValueChange = { coordinateText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("緯度, 經度") },
                                    placeholder = { Text("例如 24.150000, 120.680000") },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 12.sp
                                    )
                                )

                                if (manualTarget == null && coordinateText.isNotBlank()) {
                                    Text(
                                        "座標格式不正確。請輸入「緯度, 經度」。",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                if (manualTarget != null) {
                                    Text(
                                        "目前位置：" +
                                            (targetPlace ?: "中文地點讀取中…"),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }

                        if (option == GpsEditMode.COPY_SOURCE &&
                            mode == GpsEditMode.COPY_SOURCE
                        ) {
                            if (loadingCurrent) {
                                Text(
                                    "正在讀取 GPS…",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (sourceCandidates.isEmpty()) {
                                Text(
                                    "已選照片中沒有可作為來源的 GPS 位置。",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            } else {
                                Text(
                                    "選來源照片",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(sourceCandidates, key = { it.key }) { item ->
                                        FilterChip(
                                            selected = selectedSourceKey == item.key,
                                            onClick = { selectedSourceKey = item.key },
                                            label = {
                                                Text(
                                                    item.name,
                                                    maxLines = 1,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        )
                                    }
                                }
                                sourceTarget?.let { location ->
                                    Text(
                                        "來源位置：" +
                                            formatCoordinatePair(location) +
                                            "　" +
                                            (targetPlace ?: "中文地點讀取中…"),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }

                        if (option == GpsEditMode.REMOVE &&
                            mode == GpsEditMode.REMOVE
                        ) {
                            Text(
                                "會移除照片的 GPS 經緯度與高度位置資料；不影響照片畫質。",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    if (!loadingCurrent) {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                        ) {
                            Column(
                                Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("可修改 " + changedCount + " 項", fontSize = 12.sp)
                                if (skippedCount > 0) {
                                    Text(
                                        "略過 " + skippedCount +
                                            " 項：目前只支援可安全寫入 EXIF 的照片",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (unchangedCount > 0) {
                                    Text(
                                        unchangedCount.toString() +
                                            " 項位置原本就相同／沒有位置",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text("預覽", fontSize = 12.sp)

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 145.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            items(previews.take(60), key = { it.item.key }) { preview ->
                                GpsPreviewRow(preview)
                            }
                            if (previews.size > 60) {
                                item {
                                    Text(
                                        "先顯示前 60 項；執行時會處理全部 " +
                                            previews.size + " 項。",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Text(
                            "GPS 修改只改照片 metadata，不重新編碼、不重新壓縮。影片目前只讀取 GPS，不修改位置。",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    notice?.let { message ->
                        Text(
                            message,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (result != null) {
                TextButton(
                    enabled = !processing,
                    onClick = onDismiss
                ) { Text("完成") }
            } else {
                TextButton(
                    enabled = inputReady && changedCount > 0 && !processing,
                    onClick = { confirmOpen = true }
                ) {
                    Text(
                        if (processing) "處理中…"
                        else "修改 " + changedCount + " 項"
                    )
                }
            }
        },
        dismissButton = {
            if (result == null) {
                TextButton(
                    enabled = !processing,
                    onClick = onDismiss
                ) { Text("取消") }
            }
        }
    )

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("確認修改 GPS 位置？") },
            text = {
                Text(
                    "將修改 " + changedCount +
                        " 項照片。完成後會重新讀取 GPS 資料。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOpen = false
                        requestApply()
                    }
                ) { Text("開始修改") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun GpsModeCard(
    option: GpsEditMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (selected) 0.55f else 0.28f
        )
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Column(Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp)) {
                Text(option.label, fontSize = 12.sp)
                Text(
                    option.description,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GpsPreviewRow(preview: GpsEditPreview) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Column(
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                preview.item.name,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (preview.error != null) {
                Text(
                    "略過：" + preview.error,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                val before = if (
                    preview.currentLat != null &&
                    preview.currentLon != null
                ) {
                    formatCoordinatePair(
                        preview.currentLat to preview.currentLon
                    )
                } else {
                    "無位置"
                }
                val after = if (preview.remove) {
                    "移除位置"
                } else {
                    formatCoordinatePair(
                        preview.newLat!! to preview.newLon!!
                    )
                }
                Text(
                    before + " → " + after,
                    fontSize = 10.sp
                )
                if (!preview.changed) {
                    Text(
                        "位置相同，不會修改",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GpsTargetMap(
    initialTarget: Pair<Double, Double>?,
    target: Pair<Double, Double>?,
    originalPlace: String?,
    onTargetChange: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val initial = initialTarget ?: target
    val currentOnTargetChange by rememberUpdatedState(onTargetChange)

    val mapView = remember(initial?.first, initial?.second) {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            setBuiltInZoomControls(true)

            minZoomLevel = 2.5
            maxZoomLevel = 20.0

            if (initial != null) {
                controller.setZoom(17.0)
                controller.setCenter(GeoPoint(initial.first, initial.second))
            } else {
                controller.setZoom(2.5)
                controller.setCenter(GeoPoint(20.0, 0.0))
            }

            addMapListener(
                object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        val center = mapCenter
                        currentOnTargetChange(
                            center.latitude,
                            center.longitude
                        )
                        return false
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        val center = mapCenter
                        currentOnTargetChange(
                            center.latitude,
                            center.longitude
                        )
                        return false
                    }
                }
            )
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(215.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        AndroidView(
            factory = { mapView },
            update = { map ->
                map.overlays.clear()

                map.overlays.add(
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                if (p != null) {
                                    map.controller.animateTo(p)
                                    return true
                                }
                                return false
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                if (p != null) {
                                    map.controller.animateTo(p)
                                    return true
                                }
                                return false
                            }
                        }
                    )
                )

                // 原 GPS 是地圖上的固定參考標記；拖動地圖後仍留在原座標。
                if (initialTarget != null) {
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(
                                initialTarget.first,
                                initialTarget.second
                            )
                            setAnchor(
                                Marker.ANCHOR_CENTER,
                                Marker.ANCHOR_CENTER
                            )
                            icon = originalGpsMarkerDrawable(context)
                            title = "原 GPS 位置"
                        }
                    )
                }

                map.invalidate()
            },
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
        )

        // 原位置資訊固定在地圖框內，不再放在 AndroidView 上方，
        // 因此不會被原生地圖繪製層蓋掉。
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(7.dp)
                .zIndex(3f),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
            tonalElevation = 2.dp
        ) {
            Column(
                Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (initialTarget == null) {
                    Text(
                        "原位置：沒有 GPS",
                        fontSize = 9.sp
                    )
                } else {
                    Text(
                        "原位置：" + (originalPlace ?: "中文地點讀取不到"),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatCoordinatePair(initialTarget),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        // 中央圖釘代表將要寫入的新位置，永遠固定在畫面中央。
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "目前選定位置",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 20.dp)
                .zIndex(3f),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun originalGpsMarkerDrawable(
    context: android.content.Context
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (28f * density).toInt().coerceAtLeast(28)
    val bitmap = Bitmap.createBitmap(
        size,
        size,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(230, 190, 70, 55)
    }
    canvas.drawCircle(
        size / 2f,
        size / 2f,
        size * 0.43f,
        fill
    )

    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    canvas.drawCircle(
        size / 2f,
        size / 2f,
        size * 0.43f,
        ring
    )

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 11f * density
    }
    val y =
        size / 2f -
            (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText("原", size / 2f, y, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun parseCoordinatePair(raw: String): Pair<Double, Double>? {
    val parts = raw
        .trim()
        .split(Regex("""\s*[,，]\s*|\s+"""))
        .filter { it.isNotBlank() }

    if (parts.size != 2) return null

    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null

    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
}

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)

private fun formatCoordinatePair(value: Pair<Double, Double>): String =
    formatCoordinate(value.first) + ", " + formatCoordinate(value.second)
