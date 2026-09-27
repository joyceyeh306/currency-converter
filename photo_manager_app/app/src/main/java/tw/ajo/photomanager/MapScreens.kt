@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tw.ajo.photomanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.yield
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.floor

private data class LocationCluster(
    val lat: Double,
    val lon: Double,
    val items: List<MediaLocation>
)

@Composable
fun AlbumMapScreen(
    media: List<MediaItem>,
    repository: AlbumRepository,
    focus: Pair<Double, Double>?,
    nearbyRadiusMeters: Double?,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var locations by remember { mutableStateOf<List<MediaLocation>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var scanning by remember { mutableStateOf(false) }
    var firstIndexBuild by remember { mutableStateOf(false) }
    var zoom by remember { mutableDoubleStateOf(5.5) }
    var selected by remember { mutableStateOf<List<MediaLocation>>(emptyList()) }

    LaunchedEffect(media) {
        selected = emptyList()
        val complete = repository.isMapIndexComplete(media)
        if (complete) {
            locations = media.mapNotNull { item ->
                repository.cachedGpsFor(item)?.let { gps ->
                    MediaLocation(item, gps.first, gps.second)
                }
            }
            progress = 1f
            scanning = false
            return@LaunchedEffect
        }

        firstIndexBuild = !repository.hasAnyGpsCache()
        scanning = true
        progress = 0f

        val found = media.mapNotNull { item ->
            if (repository.hasFreshGpsCache(item)) {
                repository.cachedGpsFor(item)?.let { gps ->
                    MediaLocation(item, gps.first, gps.second)
                }
            } else null
        }.toMutableList()

        val pending = media.filterNot { repository.hasFreshGpsCache(it) }
        if (pending.isEmpty()) {
            locations = found
            repository.markMapIndexComplete(media)
            progress = 1f
            scanning = false
            return@LaunchedEffect
        }

        pending.forEachIndexed { index, item ->
            repository.gpsFor(item)?.let { gps ->
                found.add(MediaLocation(item, gps.first, gps.second))
            }
            if (index % 24 == 0 || index == pending.lastIndex) {
                locations = found.toList()
                progress = (index + 1).toFloat() / pending.size.toFloat()
                yield()
            }
        }

        repository.markMapIndexComplete(media)
        scanning = false
    }

    val visibleLocations = remember(locations, focus, nearbyRadiusMeters) {
        val center = focus
        val radius = nearbyRadiusMeters
        if (center != null && radius != null) {
            locations.filter { distanceMeters(center.first, center.second, it.lat, it.lon) <= radius }
        } else {
            locations
        }
    }

    val clusters = remember(visibleLocations, zoom) {
        clusterLocations(visibleLocations, zoom)
    }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = false
            minZoomLevel = 2.5
            maxZoomLevel = 20.0
            controller.setZoom(5.5)
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = false
                override fun onZoom(event: ZoomEvent?): Boolean {
                    zoom = event?.zoomLevel ?: zoom
                    return false
                }
            })
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(focus, nearbyRadiusMeters, locations.isNotEmpty()) {
        when {
            focus != null -> {
                val targetZoom = if (nearbyRadiusMeters != null) 17.0 else 15.0
                mapView.controller.setZoom(targetZoom)
                mapView.controller.animateTo(GeoPoint(focus.first, focus.second))
            }
            locations.isNotEmpty() -> {
                val first = locations.first()
                mapView.controller.setZoom(6.0)
                mapView.controller.animateTo(GeoPoint(first.lat, first.lon))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (nearbyRadiusMeters != null) "附近照片" else "照片地圖",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when {
                                scanning -> "整理定位 ${(progress * 100).toInt()}%"
                                nearbyRadiusMeters != null ->
                                    "${nearbyRadiusMeters.toInt()} 公尺內・${visibleLocations.size} 項"
                                else -> locations.size.toString() + " 個有定位的項目"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { mapView },
                update = { map ->
                    map.overlays.removeAll { it is Marker }
                    clusters.forEach { cluster ->
                        val marker = Marker(map).apply {
                            position = GeoPoint(cluster.lat, cluster.lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = if (cluster.items.size == 1) {
                                smallPinDrawable(context)
                            } else {
                                clusterDrawable(context, cluster.items.size)
                            }
                            title = if (cluster.items.size == 1) {
                                cluster.items.first().item.name
                            } else {
                                cluster.items.size.toString() + " 項"
                            }
                            setOnMarkerClickListener { _, mapRef ->
                                if (cluster.items.size > 8 && mapRef.zoomLevelDouble < 14.5) {
                                    mapRef.controller.animateTo(position)
                                    mapRef.controller.setZoom(
                                        (mapRef.zoomLevelDouble + 2.0).coerceAtMost(18.0)
                                    )
                                } else {
                                    selected = cluster.items
                                }
                                true
                            }
                        }
                        map.overlays.add(marker)
                    }
                    map.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            if (scanning) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (firstIndexBuild) "第一次建立地圖索引" else "更新定位資料",
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (!scanning && visibleLocations.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Text(
                            if (nearbyRadiusMeters != null) {
                                "這個範圍內沒有其他含 GPS 的照片"
                            } else {
                                "目前沒有找到含 GPS 的照片或影片"
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (selected.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                selected.size.toString() + " 項",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 5.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { selected = emptyList() }) {
                                Text("關閉")
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                bottom = 12.dp
                            )
                        ) {
                            items(selected, key = { it.item.key }) { location ->
                                Surface(
                                    onClick = { onOpenMedia(location.item) },
                                    shape = RoundedCornerShape(13.dp),
                                    modifier = Modifier
                                        .padding(end = 7.dp)
                                        .size(width = 92.dp, height = 105.dp)
                                ) {
                                    Column {
                                        AsyncImage(
                                            model = location.item.uri,
                                            contentDescription = location.item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(76.dp)
                                        )
                                        Text(
                                            if (location.item.kind == MediaKind.VIDEO) "影片" else "照片",
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniLocationMap(
    lat: Double,
    lon: Double,
    placeLabel: String?,
    onOpenAlbumMap: () -> Unit,
    onGoogleMaps: () -> Unit
) {
    val context = LocalContext.current
    val point = remember(lat, lon) { GeoPoint(lat, lon) }
    val miniMap = remember(lat, lon) {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            isTilesScaledToDpi = false
            controller.setZoom(17.0)
            controller.setCenter(point)
            overlays.add(
                Marker(this).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = smallPinDrawable(context)
                }
            )
        }
    }

    DisposableEffect(miniMap) {
        miniMap.onResume()
        onDispose {
            miniMap.onPause()
            miniMap.onDetach()
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column {
            AndroidView(
                factory = { miniMap },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(178.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .clickable { onOpenAlbumMap() }
            )

            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                Text(
                    placeLabel ?: "拍攝位置",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    String.format(java.util.Locale.US, "%.6f, %.6f", lat, lon),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row {
                    TextButton(onClick = onOpenAlbumMap) {
                        Text("查看附近照片")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onGoogleMaps) {
                        Text("Google Maps")
                    }
                }
            }
        }
    }
}

private fun distanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val result = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, result)
    return result[0].toDouble()
}

private fun clusterLocations(
    locations: List<MediaLocation>,
    zoom: Double
): List<LocationCluster> {
    if (locations.isEmpty()) return emptyList()
    val cell = when {
        zoom < 4.5 -> 18.0
        zoom < 6.5 -> 7.0
        zoom < 8.5 -> 2.0
        zoom < 10.5 -> 0.55
        zoom < 12.5 -> 0.16
        zoom < 14.5 -> 0.045
        zoom < 16.5 -> 0.012
        else -> 0.003
    }

    return locations.groupBy {
        floor(it.lat / cell).toInt() to floor(it.lon / cell).toInt()
    }.values.map { group ->
        LocationCluster(
            lat = group.map { it.lat }.average(),
            lon = group.map { it.lon }.average(),
            items = group
        )
    }
}

private fun clusterDrawable(
    context: Context,
    count: Int
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (if (count > 99) 40 else 35) * density
    val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(205, 48, 60, 70)
    }
    canvas.drawCircle(size / 2f, size / 2f, size * 0.41f, circlePaint)

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(190, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    canvas.drawCircle(size / 2f, size / 2f, size * 0.41f, ringPaint)

    val text = if (count > 999) "999+" else count.toString()
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = (if (count > 99) 9.5f else 11.5f) * density
    }
    val y = size / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(text, size / 2f, y, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun smallPinDrawable(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val w = 22f * density
    val h = 29f * density
    val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(225, 48, 60, 70)
    }
    val path = Path().apply {
        moveTo(w / 2f, h)
        cubicTo(w * 0.42f, h * 0.77f, w * 0.15f, h * 0.63f, w * 0.15f, h * 0.37f)
        cubicTo(w * 0.15f, h * 0.14f, w * 0.31f, 0f, w / 2f, 0f)
        cubicTo(w * 0.69f, 0f, w * 0.85f, h * 0.14f, w * 0.85f, h * 0.37f)
        cubicTo(w * 0.85f, h * 0.63f, w * 0.58f, h * 0.77f, w / 2f, h)
        close()
    }
    canvas.drawPath(path, fill)

    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(w / 2f, h * 0.36f, 3.2f * density, dot)
    return BitmapDrawable(context.resources, bitmap)
}
