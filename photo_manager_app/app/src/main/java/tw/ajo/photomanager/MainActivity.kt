@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n\npackage tw.ajo.photomanager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var resumeVersion by mutableIntStateOf(0)
    private lateinit var repository: AlbumRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AlbumRepository(this)
        setContent {
            AjoAlbumTheme(window) {
                AlbumApp(repository, resumeVersion)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeVersion += 1
    }
}

@Composable
private fun AjoAlbumTheme(window: Window, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val lightScheme = lightColorScheme(
        primary = Color(0xFF526B7D),
        onPrimary = Color.White,
        background = Color(0xFFFAFAF8),
        onBackground = Color(0xFF202326),
        surface = Color(0xFFFAFAF8),
        onSurface = Color(0xFF202326),
        surfaceVariant = Color(0xFFF0F1EF),
        onSurfaceVariant = Color(0xFF656A6E)
    )
    val darkScheme = darkColorScheme(
        primary = Color(0xFFAEC5D5),
        background = Color(0xFF111315),
        onBackground = Color(0xFFF0F1F2),
        surface = Color(0xFF111315),
        onSurface = Color(0xFFF0F1F2),
        surfaceVariant = Color(0xFF24272A),
        onSurfaceVariant = Color(0xFFBEC2C5)
    )
    DisposableEffect(dark) {
        window.statusBarColor = if (dark) 0xFF111315.toInt() else 0xFFFAFAF8.toInt()
        window.navigationBarColor = if (dark) 0xFF111315.toInt() else 0xFFFAFAF8.toInt()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        onDispose { }
    }
    MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, content = content)
}

@Composable
private fun AlbumApp(repository: AlbumRepository, resumeVersion: Int) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var groupMode by remember { mutableStateOf(GroupMode.DAY) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var detailItem by remember { mutableStateOf<MediaItem?>(null) }
    var detailInfo by remember { mutableStateOf<DetailInfo?>(null) }
    var showExif by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var permissionDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        if (!repository.hasAnyMediaPermission()) return
        loading = media.isEmpty()
        val base = repository.loadBase()
        media = base
        loading = false
        repository.refineOriginalTimes(base) { updated, done, total ->
            withContext(Dispatchers.Main) {
                media = updated
                progress = done to total
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (repository.hasAnyMediaPermission()) scope.launch { reload() }
    }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            selected = emptySet()
            selectionMode = false
            scope.launch {
                delay(250)
                reload()
            }
        }
    }

    LaunchedEffect(resumeVersion) {
        if (!repository.hasAnyMediaPermission()) {
            permissionDialog = true
        } else {
            reload()
        }
    }

    if (permissionDialog && !repository.hasAnyMediaPermission()) {
        AlertDialog(
            onDismissRequest = { permissionDialog = false },
            title = { Text("允許ㄚ喬的相簿讀取照片") },
            text = { Text("權限只在需要時跳出一次。照片與影片仍留在手機原本的位置，不會另外複製。") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    permissionDialog = false
                    permissionLauncher.launch(repository.requiredPermissions())
                }) { Text("允許") }
            },
            dismissButton = {
                TextButton(onClick = { permissionDialog = false }) { Text("稍後") }
            }
        )
    }

    infoDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text("ㄚ喬的相簿") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    infoDialog = null
                }) { Text("知道了") }
            }
        )
    }

    val currentDetail = detailItem
    if (currentDetail != null) {
        LaunchedEffect(currentDetail.key) {
            detailInfo = withContext(Dispatchers.IO) { repository.readDetail(currentDetail) }
        }
        if (showExif && detailInfo != null) {
            ExifScreen(
                detail = detailInfo!!,
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showExif = false
                }
            )
        } else {
            DetailScreen(
                item = currentDetail,
                detail = detailInfo,
                favorite = repository.isFavorite(currentDetail.key),
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    detailItem = null
                    detailInfo = null
                },
                onFavorite = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    repository.toggleFavorite(currentDetail.key)
                    detailItem = currentDetail.copy()
                },
                onExif = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showExif = true
                },
                onMap = { lat, lon ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    openGoogleMaps(context, lat, lon)
                },
                onAdjust = {
                    infoDialog = "相簿時間調整與批次整理會在下一階段接上。這一版先把原始拍攝時間、瀏覽與 EXIF 核心做穩。"
                }
            )
        }
        return
    }

    val filtered = remember(media, searchText) {
        val query = searchText.trim()
        if (query.isBlank()) media
        else media.filter {
            it.name.contains(query, true) ||
                formatDateTime(it.wallTime).contains(query, true) ||
                formatDateOnly(it.wallTime).contains(query, true)
        }
    }
    val sections = remember(filtered, groupMode) { buildSections(filtered, groupMode) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            when {
                selectionMode -> SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = filtered.isNotEmpty() && selected.size == filtered.size,
                    onClose = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectionMode = false
                        selected = emptySet()
                    },
                    onAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selected = if (filtered.isNotEmpty() && selected.size == filtered.size) {
                            emptySet()
                        } else {
                            filtered.map { it.key }.toSet()
                        }
                    }
                )
                searchOpen -> SearchBar(
                    query = searchText,
                    onQuery = { searchText = it },
                    onClose = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        searchOpen = false
                        searchText = ""
                    }
                )
                else -> CenterAlignedTopAppBar(
                    title = { Text("圖庫", fontSize = 23.sp, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        Box {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                menuOpen = true
                            }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("地圖相簿・即將開放") },
                                    leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        menuOpen = false
                                        infoDialog = "地圖相簿的入口先保留，後續版本再正式開放。"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("重複與相似照片・後續開放") },
                                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        menuOpen = false
                                        infoDialog = "完全重複、相似照片與 2～4 張比對會在相簿核心穩定後加入。"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("照片整理工具・下一階段") },
                                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        menuOpen = false
                                        infoDialog = "時間、GPS、檔名與 Metadata 批次整理會整合回這裡。"
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectionMode = true
                        }) { Text("選取") }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                SelectionBottomBar(
                    enabled = selected.isNotEmpty(),
                    onShare = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        shareItems(context, media.filter { selected.contains(it.key) })
                    },
                    onFavorite = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        media.filter { selected.contains(it.key) }.forEach { repository.setFavorite(it.key, true) }
                    },
                    onOrganize = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        infoDialog = "批次整理工具會在下一階段接回。"
                    },
                    onDelete = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val chosen = media.filter { selected.contains(it.key) }
                        val request = repository.trashRequest(chosen)
                        if (request != null) {
                            trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                        } else {
                            scope.launch { reload() }
                        }
                    }
                )
            } else {
                ModeBar(
                    mode = groupMode,
                    onMode = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        groupMode = it
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode && !searchOpen) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        searchOpen = true
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Default.Search, contentDescription = "搜尋")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(progress.second > 0 && progress.first < progress.second) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "正在整理原始拍攝時間 " + progress.first.toString() + "/" + progress.second.toString(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { progress.first.toFloat() / progress.second.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                    )
                }
            }

            when {
                !repository.hasAnyMediaPermission() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        permissionDialog = true
                    }) { Text("開啟照片權限") }
                }
                loading && media.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                sections.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchText.isNotBlank()) "沒有找到符合的照片" else "目前沒有可顯示的照片",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> AlbumGrid(
                    sections = sections,
                    selectionMode = selectionMode,
                    selected = selected,
                    isFavorite = { repository.isFavorite(it) },
                    onClick = { item ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (selectionMode) {
                            selected = toggleSelected(selected, item.key)
                        } else {
                            detailItem = item
                        }
                    },
                    onLongClick = { item ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectionMode = true
                        selected = toggleSelected(selected, item.key)
                    },
                    onSelectSection = { list ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val keys = list.map { it.key }.toSet()
                        selected = if (keys.all { selected.contains(it) }) selected - keys else selected + keys
                    }
                )
            }
        }
    }
}

private fun toggleSelected(source: Set<String>, key: String): Set<String> =
    if (source.contains(key)) source - key else source + key

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onAll: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = { Text("已選 " + selectedCount.toString() + " 項", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "取消") }
        },
        actions = {
            TextButton(onClick = onAll) { Text(if (allSelected) "取消全選" else "全選") }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("搜尋日期、檔名；地點與照片文字後續加入") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "關閉") }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun ModeBar(mode: GroupMode, onMode: (GroupMode) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.navigationBarsPadding()
    ) {
        GroupMode.entries.forEach { item ->
            NavigationBarItem(
                selected = item == mode,
                onClick = { onMode(item) },
                icon = { },
                label = {
                    Text(
                        item.label,
                        fontSize = 14.sp,
                        fontWeight = if (item == mode) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@Composable
private fun SelectionBottomBar(
    enabled: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onOrganize: () -> Unit,
    onDelete: () -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.navigationBarsPadding()
    ) {
        NavigationBarItem(
            selected = false, enabled = enabled, onClick = onShare,
            icon = { Icon(Icons.Default.Share, contentDescription = null) }, label = { Text("分享") }
        )
        NavigationBarItem(
            selected = false, enabled = enabled, onClick = onFavorite,
            icon = { Icon(Icons.Default.FavoriteBorder, contentDescription = null) }, label = { Text("收藏") }
        )
        NavigationBarItem(
            selected = false, enabled = enabled, onClick = onOrganize,
            icon = { Icon(Icons.Default.Tune, contentDescription = null) }, label = { Text("整理") }
        )
        NavigationBarItem(
            selected = false, enabled = enabled, onClick = onDelete,
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) }, label = { Text("刪除") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGrid(
    sections: List<AlbumSection>,
    selectionMode: Boolean,
    selected: Set<String>,
    isFavorite: (String) -> Boolean,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    onSelectSection: (List<MediaItem>) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 14.dp)
    ) {
        sections.forEach { section ->
            item(key = "header-" + section.key) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = 14.dp, end = 10.dp, top = 18.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        section.title,
                        modifier = Modifier.weight(1f),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selectionMode) {
                        val keys = section.items.map { it.key }
                        val count = keys.count { selected.contains(it) }
                        TextButton(onClick = { onSelectSection(section.items) }) {
                            val label = when {
                                count == keys.size && keys.isNotEmpty() -> keys.size.toString() + " 項 ✓"
                                count > 0 -> count.toString() + "/" + keys.size.toString() + " 項"
                                else -> "全日 " + keys.size.toString() + " 項"
                            }
                            Text(label)
                        }
                    } else {
                        Text(
                            section.items.size.toString() + " 項",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(
                items = section.items.chunked(4),
                key = { row -> "row-" + section.key + "-" + row.first().key }
            ) { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { item ->
                        PhotoCell(
                            item = item,
                            selected = selected.contains(item.key),
                            selectionMode = selectionMode,
                            favorite = isFavorite(item.key),
                            modifier = Modifier.weight(1f),
                            onClick = { onClick(item) },
                            onLongClick = { onLongClick(item) }
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    favorite: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier.padding(0.75.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (favorite && !selectionMode) {
            Box(
                Modifier.padding(5.dp).size(23.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (item.kind == MediaKind.VIDEO) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.size(3.dp))
                Text(formatDuration(item.duration), color = Color.White, fontSize = 10.sp)
            }
        }

        if (selectionMode) {
            Box(
                Modifier.padding(6.dp).size(25.dp).clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.35f)
                    )
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "已選取",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        if (selected) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            )
        }
    }
}

@Composable
private fun DetailScreen(
    item: MediaItem,
    detail: DetailInfo?,
    favorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onExif: () -> Unit,
    onMap: (Double, Double) -> Unit,
    onAdjust: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onFavorite) {
                        Icon(
                            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(430.dp).background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Text("加入說明", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                formatDateTime(item.wallTime),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "原始拍攝時間 · " + item.timeSource,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        TextButton(onClick = onAdjust) { Text("調整") }
                    }
                    Text(
                        item.name,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    HorizontalDivider(Modifier.padding(vertical = 18.dp))

                    if (detail == null) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    } else {
                        CameraCard(detail)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onExif, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(7.dp))
                            Text("查看完整 EXIF")
                        }
                        if (detail.hasGps && detail.lat != null && detail.lon != null) {
                            HorizontalDivider(Modifier.padding(vertical = 18.dp))
                            LocationCard(detail, onMap)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCard(detail: DetailInfo) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            val camera = listOf(detail.make, detail.model).filter { it.isNotBlank() }.joinToString(" ")
            Text(
                if (camera.isBlank()) "照片資訊" else camera,
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
            Spacer(Modifier.height(13.dp))
            Text(
                detail.item.width.toString() + " × " + detail.item.height.toString() +
                    " · " + formatBytes(detail.item.size) +
                    " · " + detail.item.mime.substringAfterLast('/').uppercase(Locale.US),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val values = mutableListOf<String>()
            if (detail.iso.isNotBlank()) values.add("ISO " + detail.iso)
            if (detail.focal35.isNotBlank()) values.add(detail.focal35 + " mm")
            if (detail.aperture.isNotBlank()) values.add("f/" + detail.aperture)
            if (detail.exposure.isNotBlank()) values.add(detail.exposure)
            if (values.isNotEmpty()) {
                Text(values.joinToString("   "), fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}

@Composable
private fun LocationCard(detail: DetailInfo, onMap: (Double, Double) -> Unit) {
    val lat = detail.lat ?: return
    val lon = detail.lon ?: return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("拍攝位置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                String.format(Locale.US, "%.6f, %.6f", lat, lon),
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 14.sp
            )
            if (detail.altitude != null) {
                Text(
                    "海拔 " + String.format(Locale.US, "%.1f", detail.altitude) + " m",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            TextButton(
                onClick = { onMap(lat, lon) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = 5.dp)
            ) { Text("用 Google Maps 開啟") }
        }
    }
}

@Composable
private fun ExifScreen(detail: DetailInfo, onBack: () -> Unit) {
    var rawMode by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("完整 EXIF", fontWeight = FontWeight.SemiBold) },
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
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) {
            items(detail.rows.filter { it.value.isNotBlank() }) { row ->
                Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                    Text(
                        if (rawMode) row.rawTag else row.label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(row.value, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android MediaStore",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
                SystemRow("DATE_TAKEN", detail.item.dateTaken.toString())
                SystemRow("DATE_MODIFIED", detail.item.dateModified.toString())
                SystemRow("MIME_TYPE", detail.item.mime)
                SystemRow("SIZE", detail.item.size.toString())
                SystemRow(
                    "WIDTH × HEIGHT",
                    detail.item.width.toString() + " × " + detail.item.height.toString()
                )
            }
        }
    }
}

@Composable
private fun SystemRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp)
    }
}

private fun shareItems(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = if (items.first().mime.isBlank()) "*/*" else items.first().mime
            putExtra(Intent.EXTRA_STREAM, items.first().uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "分享照片"))
}

private fun openGoogleMaps(context: Context, lat: Double, lon: Double) {
    val uri = Uri.parse(
        "https://www.google.com/maps/search/?api=1&query=" + lat.toString() + "," + lon.toString()
    )
    val maps = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
    if (maps.resolveActivity(context.packageManager) != null) {
        context.startActivity(maps)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
