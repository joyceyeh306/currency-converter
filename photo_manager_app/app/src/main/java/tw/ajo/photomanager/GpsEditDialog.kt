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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

private enum class GpsEditMode(
    val label: String,
    val description: String
) {
    SET_LOCATION(
        "設定／微調位置",
        "單張修改；批次時全部設成同一位置"
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
    var initializedTarget by remember { mutableStateOf(false) }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var selectedSourceKey by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var pendingApply by remember { mutableStateOf<List<GpsEditPreview>>(emptyList()) }
    var result by remember { mutableStateOf<GpsEditResult?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedItems.map { it.key }) {
        loadingCurrent = true
        currentLocations = withContext(Dispatchers.IO) {
            selectedItems.associate { item ->
                item.key to repository.gpsFor(item)
            }
        }
        loadingCurrent = false
    }

    val sourceCandidates = remember(selectedItems, currentLocations) {
        selectedItems.filter { item ->
            item.kind == MediaKind.IMAGE && currentLocations[item.key] != null
        }
    }

    LaunchedEffect(loadingCurrent, sourceCandidates) {
        if (!loadingCurrent) {
            if (selectedSourceKey == null) {
                selectedSourceKey = sourceCandidates.firstOrNull()?.key
            }
            if (!initializedTarget) {
                val firstLocation = selectedItems
                    .asSequence()
                    .filter { it.kind == MediaKind.IMAGE }
                    .mapNotNull { currentLocations[it.key] }
                    .firstOrNull()
                if (firstLocation != null) {
                    latText = formatCoordinate(firstLocation.first)
                    lonText = formatCoordinate(firstLocation.second)
                }
                initializedTarget = true
            }
        }
    }

    val manualLat = latText.trim().toDoubleOrNull()
    val manualLon = lonText.trim().toDoubleOrNull()
    val manualTarget = if (
        manualLat != null && manualLon != null &&
        manualLat in -90.0..90.0 &&
        manualLon in -180.0..180.0
    ) {
        manualLat to manualLon
    } else {
        null
    }

    val sourceTarget = selectedSourceKey?.let { currentLocations[it] }
    val target = when (mode) {
        GpsEditMode.SET_LOCATION -> manualTarget
        GpsEditMode.COPY_SOURCE -> sourceTarget
        GpsEditMode.REMOVE -> null
    }

    val previews = remember(
        selectedItems,
        currentLocations,
        target,
        mode
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
            LazyColumn(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                result?.let { gpsResult ->
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
                                gpsResult.details.take(20).forEach { line ->
                                    Text(
                                        line,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (result == null) {
                    item {
                        Text("修改方式", fontSize = 13.sp)
                    }

                    items(
                        if (selectedItems.size > 1)
                            GpsEditMode.entries
                        else
                            listOf(GpsEditMode.SET_LOCATION, GpsEditMode.REMOVE),
                        key = { it.name }
                    ) { option ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { mode = option },
                            shape = RoundedCornerShape(15.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = if (mode == option) 0.55f else 0.28f
                            )
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                RadioButton(
                                    selected = mode == option,
                                    onClick = { mode = option }
                                )
                                Column(Modifier.padding(start = 3.dp)) {
                                    Text(option.label, fontSize = 13.sp)
                                    Text(
                                        option.description,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (loadingCurrent) {
                        item {
                            Text(
                                "正在讀取目前 GPS 位置…",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (mode == GpsEditMode.SET_LOCATION) {
                        item {
                            GpsTargetMap(
                                target = manualTarget,
                                onTargetChange = { lat, lon ->
                                    latText = formatCoordinate(lat)
                                    lonText = formatCoordinate(lon)
                                }
                            )
                        }
                        item {
                            Text(
                                "拖曳圖釘或直接點地圖即可微調；也可以直接輸入經緯度。地圖圖磚需要網路。",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = latText,
                                    onValueChange = { latText = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("緯度") }
                                )
                                OutlinedTextField(
                                    value = lonText,
                                    onValueChange = { lonText = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("經度") }
                                )
                            }
                        }
                        if (manualTarget == null) {
                            item {
                                Text(
                                    "請在地圖上指定位置，或輸入有效座標：緯度 -90～90、經度 -180～180。",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else if (mode == GpsEditMode.COPY_SOURCE) {
                        item {
                            if (sourceCandidates.isEmpty()) {
                                Text(
                                    "已選照片中沒有可作為來源的 GPS 位置。",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("選來源照片", fontSize = 12.sp)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        items(
                                            sourceCandidates,
                                            key = { it.key }
                                        ) { item ->
                                            FilterChip(
                                                selected = selectedSourceKey == item.key,
                                                onClick = { selectedSourceKey = item.key },
                                                label = {
                                                    Text(
                                                        item.name,
                                                        maxLines = 1,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    sourceTarget?.let { location ->
                                        Text(
                                            "來源位置：" +
                                                formatCoordinate(location.first) +
                                                ", " +
                                                formatCoordinate(location.second),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "會移除照片的 GPS 經緯度與高度位置資料；不會改動照片畫質。",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!loadingCurrent) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(15.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text("可修改 " + changedCount + " 項")
                                    if (skippedCount > 0) {
                                        Text(
                                            "略過 " + skippedCount +
                                                " 項：目前只支援可安全寫入 EXIF 的照片",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (unchangedCount > 0) {
                                        Text(
                                            unchangedCount + " 項位置原本就相同／沒有位置",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text("預覽", fontSize = 13.sp)
                        }

                        items(previews.take(60), key = { it.item.key }) { preview ->
                            Surface(
                                shape = RoundedCornerShape(13.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        preview.item.name,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (preview.error != null) {
                                        Text(
                                            "略過：" + preview.error,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        val before = if (
                                            preview.currentLat != null &&
                                            preview.currentLon != null
                                        ) {
                                            formatCoordinate(preview.currentLat) +
                                                ", " +
                                                formatCoordinate(preview.currentLon)
                                        } else {
                                            "無位置"
                                        }
                                        val after = if (preview.remove) {
                                            "移除位置"
                                        } else {
                                            formatCoordinate(preview.newLat!!) +
                                                ", " +
                                                formatCoordinate(preview.newLon!!)
                                        }
                                        Text(
                                            before + " → " + after,
                                            fontSize = 11.sp
                                        )
                                        if (!preview.changed) {
                                            Text(
                                                "位置相同，不會修改",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (previews.size > 60) {
                            item {
                                Text(
                                    "先顯示前 60 項；執行時會處理全部 " +
                                        previews.size + " 項。",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            HorizontalDivider()
                        }

                        item {
                            Text(
                                "GPS 修改只改照片 metadata，不重新編碼、不重新壓縮影像。影片目前只讀取 GPS，不修改位置。",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    notice?.let { message ->
                        item {
                            Text(
                                message,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
private fun GpsTargetMap(
    target: Pair<Double, Double>?,
    onTargetChange: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 2.5
            maxZoomLevel = 20.0
            if (target != null) {
                controller.setZoom(17.0)
                controller.setCenter(GeoPoint(target.first, target.second))
            } else {
                controller.setZoom(2.5)
                controller.setCenter(GeoPoint(20.0, 0.0))
            }
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(target?.first, target?.second) {
        if (target != null) {
            mapView.controller.animateTo(GeoPoint(target.first, target.second))
        }
    }

    AndroidView(
        factory = { mapView },
        update = { map ->
            map.overlays.clear()

            map.overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                onTargetChange(p.latitude, p.longitude)
                                return true
                            }
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                onTargetChange(p.latitude, p.longitude)
                                return true
                            }
                            return false
                        }
                    }
                )
            )

            if (target != null) {
                val marker = Marker(map).apply {
                    position = GeoPoint(target.first, target.second)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    isDraggable = true
                    setOnMarkerDragListener(
                        object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker?) {
                            }

                            override fun onMarkerDragEnd(marker: Marker?) {
                                marker?.position?.let { point ->
                                    onTargetChange(
                                        point.latitude,
                                        point.longitude
                                    )
                                }
                            }

                            override fun onMarkerDragStart(marker: Marker?) {
                            }
                        }
                    )
                }
                map.overlays.add(marker)
            }

            map.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(245.dp)
    )
}

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)
