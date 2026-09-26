@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package tw.ajo.photomanager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startKey: String,
    repository: AlbumRepository,
    onBack: (String) -> Unit,
    onShare: (MediaItem) -> Unit,
    onOrganize: (MediaItem) -> Unit,
    onTrash: (MediaItem) -> Unit
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack(startKey) }
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val initialPage = items.indexOfFirst { it.key == startKey }.let { if (it >= 0) it else 0 }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { items.size }
    )

    var controlsVisible by remember { mutableStateOf(true) }
    var infoVisible by remember { mutableStateOf(false) }
    var showExif by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<DetailInfo?>(null) }
    var favoriteVersion by remember { mutableIntStateOf(0) }
    var zoomed by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    val current = items.getOrNull(pagerState.currentPage) ?: items.first()
    val favorite = remember(current.key, favoriteVersion) {
        repository.isFavorite(current.key)
    }

    LaunchedEffect(current.key) {
        detail = null
        zoomed = false
        if (infoVisible) {
            detail = withContext(Dispatchers.IO) { repository.readDetail(current) }
        }
    }

    LaunchedEffect(infoVisible, current.key) {
        if (infoVisible && detail == null) {
            detail = withContext(Dispatchers.IO) { repository.readDetail(current) }
        }
    }

    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    DisposableEffect(Unit) {
        val window = activity?.window
        val oldStatus = window?.statusBarColor
        val oldNav = window?.navigationBarColor
        if (window != null) {
            window.statusBarColor = Color.Black.toArgb()
            window.navigationBarColor = Color.Black.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        onDispose {
            if (window != null && oldStatus != null && oldNav != null) {
                window.statusBarColor = oldStatus
                window.navigationBarColor = oldNav
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    if (showExif && detail != null) {
        ViewerExifScreen(
            detail = detail!!,
            onBack = { showExif = false }
        )
        return
    }

    BackHandler {
        when {
            infoVisible -> infoVisible = false
            moreOpen -> moreOpen = false
            else -> onBack(current.key)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            if (item.kind == MediaKind.IMAGE) {
                ZoomablePhoto(
                    item = item,
                    onZoomed = { isZoomed ->
                        if (page == pagerState.currentPage) zoomed = isZoomed
                    },
                    onSingleTap = {
                        if (page == pagerState.currentPage) {
                            controlsVisible = !controlsVisible
                        }
                    }
                )
            } else {
                VideoPreview(
                    item = item,
                    onSingleTap = {
                        if (page == pagerState.currentPage) {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onPlay = { openExternalMedia(context, item) }
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack(current.key)
                    }
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }

                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        formatDateOnly(current.wallTime),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        current.wallTime.toLocalTime().toString(),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp
                    )
                }

                Box {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            moreOpen = true
                        }
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = moreOpen,
                        onDismissRequest = { moreOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("用其他 App 開啟") },
                            leadingIcon = {
                                Icon(Icons.Default.OpenInNew, contentDescription = null)
                            },
                            onClick = {
                                moreOpen = false
                                openExternalMedia(context, current)
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ViewerBottomBar(
                favorite = favorite,
                onShare = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShare(current)
                },
                onFavorite = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    repository.toggleFavorite(current.key)
                    favoriteVersion += 1
                },
                onInfo = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    infoVisible = true
                },
                onOrganize = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOrganize(current)
                },
                onDelete = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTrash(current)
                }
            )
        }

        if (items.size > 1 && controlsVisible) {
            Text(
                (pagerState.currentPage + 1).toString() + " / " + items.size.toString(),
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 58.dp)
                    .background(
                        Color.Black.copy(alpha = 0.42f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }

    if (infoVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { infoVisible = false },
            sheetState = sheetState
        ) {
            if (detail == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                PhotoInfoSheet(
                    detail = detail!!,
                    onExif = {
                        infoVisible = false
                        showExif = true
                    },
                    onMap = { lat, lon ->
                        openGoogleMaps(context, lat, lon)
                    }
                )
            }
        }
    }
}

@Composable
private fun ViewerBottomBar(
    favorite: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
    onOrganize: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.58f))
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewerAction(Icons.Default.Share, "分享", onShare)
        ViewerAction(
            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            "收藏",
            onFavorite
        )
        ViewerAction(Icons.Default.Info, "資訊", onInfo)
        ViewerAction(Icons.Default.Tune, "整理", onOrganize)
        ViewerAction(Icons.Default.DeleteOutline, "刪除", onDelete)
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(label, color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ZoomablePhoto(
    item: MediaItem,
    onZoomed: (Boolean) -> Unit,
    onSingleTap: () -> Unit
) {
    var scale by remember(item.key) { mutableFloatStateOf(1f) }
    var offset by remember(item.key) { mutableStateOf(Offset.Zero) }
    var container by remember(item.key) { mutableStateOf(IntSize.Zero) }

    fun clampOffset(value: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f) return Offset.Zero
        val maxX = max(0f, container.width * (currentScale - 1f) / 2f)
        val maxY = max(0f, container.height * (currentScale - 1f) / 2f)
        return Offset(
            value.x.coerceIn(-maxX, maxX),
            value.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(item.key) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        scale = if (scale > 1.05f) 1f else 2.5f
                        if (scale == 1f) offset = Offset.Zero
                        onZoomed(scale > 1.05f)
                    }
                )
            }
            .pointerInput(item.key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val shouldHandle = pressedCount >= 2 || scale > 1.05f

                        if (shouldHandle) {
                            if (pressedCount >= 2) {
                                val zoom = event.calculateZoom()
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                if (scale <= 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            }

                            if (scale > 1.05f) {
                                offset = clampOffset(
                                    offset + event.calculatePan(),
                                    scale
                                )
                            }

                            onZoomed(scale > 1.05f)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun VideoPreview(
    item: MediaItem,
    onSingleTap: () -> Unit,
    onPlay: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.key) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { onPlay() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            onClick = onPlay,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.56f)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "播放影片",
                tint = Color.White,
                modifier = Modifier
                    .padding(14.dp)
                    .size(36.dp)
            )
        }
    }
}

@Composable
private fun PhotoInfoSheet(
    detail: DetailInfo,
    onExif: () -> Unit,
    onMap: (Double, Double) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            bottom = 34.dp
        )
    ) {
        item {
            Text(
                formatDateTime(detail.item.wallTime),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                detail.item.timeSource,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                detail.item.name,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 18.dp))

            CameraInfoCard(detail)

            TextButton(
                onClick = onExif,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text("查看完整 EXIF")
            }

            if (detail.hasGps && detail.lat != null && detail.lon != null) {
                HorizontalDivider(Modifier.padding(vertical = 18.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "拍攝位置",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            String.format(
                                Locale.US,
                                "%.6f, %.6f",
                                detail.lat,
                                detail.lon
                            ),
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        if (detail.altitude != null) {
                            Text(
                                "海拔 " +
                                    String.format(Locale.US, "%.1f", detail.altitude) +
                                    " m",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        TextButton(
                            onClick = { onMap(detail.lat, detail.lon) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("用 Google Maps 開啟")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraInfoCard(detail: DetailInfo) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            val camera = listOf(detail.make, detail.model)
                .filter { it.isNotBlank() }
                .joinToString(" ")

            Text(
                if (camera.isBlank()) {
                    if (detail.item.kind == MediaKind.VIDEO) "影片資訊" else "照片資訊"
                } else {
                    camera
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (detail.lens.isNotBlank()) {
                Text(
                    detail.lens,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Text(
                detail.item.width.toString() +
                    " × " +
                    detail.item.height.toString() +
                    " · " +
                    formatBytes(detail.item.size),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 13.dp)
            )

            if (detail.item.kind == MediaKind.VIDEO) {
                Text(
                    "長度 " + formatDuration(detail.item.duration),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                val exposure = mutableListOf<String>()
                if (detail.iso.isNotBlank()) exposure.add("ISO " + detail.iso)
                if (detail.focal35.isNotBlank()) exposure.add(detail.focal35 + " mm")
                if (detail.aperture.isNotBlank()) exposure.add("f/" + detail.aperture)
                if (detail.exposure.isNotBlank()) exposure.add(detail.exposure)
                if (exposure.isNotEmpty()) {
                    Text(
                        exposure.joinToString("   "),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerExifScreen(
    detail: DetailInfo,
    onBack: () -> Unit
) {
    var rawMode by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "完整 EXIF",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { rawMode = !rawMode }) {
                        Text(if (rawMode) "易讀" else "原始")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = 18.dp,
                vertical = 12.dp
            )
        ) {
            items(detail.rows.filter { it.value.isNotBlank() }) { row ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp)
                ) {
                    Text(
                        if (rawMode) row.rawTag else row.label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        row.value,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            item {
                Spacer(Modifier.size(10.dp))
                Text(
                    "Android MediaStore",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        top = 12.dp,
                        bottom = 8.dp
                    )
                )
                ExifSystemRow("DATE_TAKEN", detail.item.dateTaken.toString())
                ExifSystemRow("DATE_ADDED", detail.item.dateAdded.toString())
                ExifSystemRow("DATE_MODIFIED", detail.item.dateModified.toString())
                ExifSystemRow("MIME_TYPE", detail.item.mime)
                ExifSystemRow("SIZE", detail.item.size.toString())
                ExifSystemRow(
                    "WIDTH × HEIGHT",
                    detail.item.width.toString() +
                        " × " +
                        detail.item.height.toString()
                )
            }
        }
    }
}

@Composable
private fun ExifSystemRow(label: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontSize = 15.sp)
    }
}

fun shareItems(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return

    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items.first().mime.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, items.first().uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(items.map { it.uri })
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    context.startActivity(
        Intent.createChooser(intent, "分享照片")
    )
}

private fun openExternalMedia(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, item.mime.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(intent)
    }
}

private fun openGoogleMaps(
    context: Context,
    lat: Double,
    lon: Double
) {
    val uri = Uri.parse(
        "https://www.google.com/maps/search/?api=1&query=" +
            lat.toString() +
            "," +
            lon.toString()
    )

    val maps = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }

    if (maps.resolveActivity(context.packageManager) != null) {
        context.startActivity(maps)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
