@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package tw.ajo.photomanager

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var resumeVersion by mutableIntStateOf(0)
    private lateinit var repository: AlbumRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AlbumRepository(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .build()
        )
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

    MaterialTheme(
        colorScheme = if (dark) darkScheme else lightScheme,
        content = content
    )
}

@Composable
private fun AlbumApp(repository: AlbumRepository, resumeVersion: Int) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("ajo_album_settings", Context.MODE_PRIVATE)
    }

    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    var groupMode by remember {
        mutableStateOf(
            runCatching {
                GroupMode.valueOf(prefs.getString("groupMode", GroupMode.DAY.name)!!)
            }.getOrDefault(GroupMode.DAY)
        )
    }
    var sortField by remember {
        mutableStateOf(
            runCatching {
                SortField.valueOf(prefs.getString("sortField", SortField.CAPTURE_TIME.name)!!)
            }.getOrDefault(SortField.CAPTURE_TIME)
        )
    }
    var sortDescending by remember {
        mutableStateOf(prefs.getBoolean("sortDescending", true))
    }
    var mediaFilter by remember {
        mutableStateOf(
            runCatching {
                MediaFilter.valueOf(prefs.getString("mediaFilter", MediaFilter.ALL.name)!!)
            }.getOrDefault(MediaFilter.ALL)
        )
    }
    var customColumns by remember {
        val saved = prefs.getInt("gridColumns", 0)
        mutableStateOf(if (saved in listOf(2, 3, 4, 6, 8, 12, 16, 24, 40)) saved else null)
    }

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var viewerKey by remember { mutableStateOf<String?>(null) }

    var menuOpen by remember { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }
    var densityDialog by remember { mutableStateOf(false) }
    var yearDialog by remember { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf(false) }
    var exitDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<String?>(null) }
    var previousGroupMode by remember { mutableStateOf<GroupMode?>(null) }
    var targetMonthKey by remember { mutableStateOf<String?>(null) }
    var targetYear by remember { mutableStateOf<Int?>(null) }
    var targetMediaKey by remember { mutableStateOf<String?>(null) }
    var suppressClickKey by remember { mutableStateOf<String?>(null) }

    val gridState = rememberLazyGridState()
    val yearGridState = rememberLazyGridState()
    val reloadMutex = remember { Mutex() }

    suspend fun reload() = reloadMutex.withLock {
        if (!repository.hasAnyMediaPermission()) return@withLock
        loading = media.isEmpty()
        val base = repository.loadBase()
        media = base
        loading = false

        repository.refineOriginalTimes(base) { updated ->
            withContext(Dispatchers.Main) {
                media = updated
            }
        }
    }

    fun visibleMediaKey(): String? {
        return gridState.layoutInfo.visibleItemsInfo
            .firstNotNullOfOrNull { info ->
                val raw = info.key?.toString() ?: return@firstNotNullOfOrNull null
                if (raw.startsWith("media:")) raw.removePrefix("media:") else null
            }
    }

    fun closeSearchKeepPosition() {
        if (groupMode != GroupMode.YEAR) {
            targetMediaKey = visibleMediaKey()
        }
        searchOpen = false
        searchText = ""
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (repository.hasAnyMediaPermission()) {
            scope.launch { reload() }
        }
    }

    var pendingTrashKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingViewerNextKey by remember { mutableStateOf<String?>(null) }
    var trashDialogActive by remember { mutableStateOf(false) }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        trashDialogActive = false

        if (result.resultCode == Activity.RESULT_OK) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val deleted = pendingTrashKeys
            if (deleted.isNotEmpty()) {
                media = media.filterNot { deleted.contains(it.key) }
                selected = selected - deleted
            }

            if (viewerKey != null && deleted.contains(viewerKey)) {
                viewerKey = pendingViewerNextKey
            } else if (selectionMode) {
                selectionMode = false
                selected = emptySet()
            }

            scope.launch {
                delay(350)
                reload()
            }
        }

        pendingTrashKeys = emptySet()
        pendingViewerNextKey = null
    }

    LaunchedEffect(resumeVersion) {
        if (!repository.hasAllMediaPermissions()) {
            permissionDialog = true
        }
        if (repository.hasAnyMediaPermission() && !trashDialogActive) {
            reload()
        }
    }

    if (permissionDialog && !repository.hasAllMediaPermissions()) {
        AlertDialog(
            onDismissRequest = { permissionDialog = false },
            title = { Text("允許ㄚ喬的相簿讀取照片與影片") },
            text = {
                Text(
                    if (repository.hasImagePermission() && !repository.hasVideoPermission()) {
                        "目前只有照片權限，所以影片沒有出現在相簿中。補上影片權限後，照片與影片會一起顯示；照片原檔不會被複製。"
                    } else {
                        "請允許照片與影片存取。照片與影片仍留在手機原本的位置，不會另外複製。"
                    }
                )
            },
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

    if (sortDialog) {
        SortDialog(
            field = sortField,
            descending = sortDescending,
            onField = {
                sortField = it
                sortDescending = it != SortField.FILE_NAME
                prefs.edit()
                    .putString("sortField", it.name)
                    .putBoolean("sortDescending", sortDescending)
                    .apply()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDirection = {
                sortDescending = it
                prefs.edit().putBoolean("sortDescending", it).apply()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDismiss = { sortDialog = false }
        )
    }

    if (densityDialog) {
        DensityDialog(
            current = customColumns,
            onChoose = { value ->
                customColumns = value
                prefs.edit().putInt("gridColumns", value ?: 0).apply()
                densityDialog = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDismiss = { densityDialog = false }
        )
    }

    val years = remember(media) {
        media.map { it.wallTime.year }.distinct().sortedDescending()
    }

    if (yearDialog) {
        YearPickerDialog(
            years = years,
            onChoose = { year ->
                yearDialog = false
                targetYear = year
                previousGroupMode = groupMode
                groupMode = GroupMode.YEAR
                prefs.edit().putString("groupMode", GroupMode.YEAR.name).apply()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDismiss = { yearDialog = false }
        )
    }

    if (exitDialog) {
        AlertDialog(
            onDismissRequest = { exitDialog = false },
            title = { Text("要離開「ㄚ喬的相簿」嗎？") },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) { Text("離開") }
            },
            dismissButton = {
                TextButton(onClick = { exitDialog = false }) { Text("取消") }
            }
        )
    }

    infoDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text("ㄚ喬的相簿") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) { Text("知道了") }
            }
        )
    }

    val filteredByType = remember(media, mediaFilter) {
        filterMedia(media, mediaFilter)
    }

    val searched = remember(filteredByType, searchText) {
        val query = searchText.trim()
        if (query.isBlank()) {
            filteredByType
        } else {
            filteredByType.filter {
                it.name.contains(query, true) ||
                    formatDateTime(it.wallTime).contains(query, true) ||
                    formatDateOnly(it.wallTime).contains(query, true) ||
                    it.wallTime.year.toString().contains(query)
            }
        }
    }

    val ordered = remember(searched, sortField, sortDescending) {
        sortMedia(searched, sortField, sortDescending)
    }

    val currentViewerKey = viewerKey
    if (currentViewerKey != null) {
        ViewerScreen(
            items = ordered,
            startKey = currentViewerKey,
            repository = repository,
            onBack = { key ->
                viewerKey = null
                targetMediaKey = key
            },
            onShare = { item ->
                shareItems(context, listOf(item))
            },
            onOrganize = {
                infoDialog = "照片整理工具會在下一階段接回時間、GPS、檔名與 Metadata。"
            },
            onTrash = { item ->
                val index = ordered.indexOfFirst { it.key == item.key }
                pendingViewerNextKey = when {
                    index >= 0 && index + 1 < ordered.size -> ordered[index + 1].key
                    index > 0 -> ordered[index - 1].key
                    else -> null
                }
                pendingTrashKeys = setOf(item.key)

                val request = repository.trashRequest(listOf(item))
                if (request != null) {
                    trashDialogActive = true
                    trashLauncher.launch(
                        IntentSenderRequest.Builder(request.intentSender).build()
                    )
                } else {
                    media = media.filterNot { it.key == item.key }
                    viewerKey = pendingViewerNextKey
                    pendingTrashKeys = emptySet()
                    pendingViewerNextKey = null
                    scope.launch {
                        delay(250)
                        reload()
                    }
                }
            }
        )
        return
    }

    BackHandler {
        when {
            menuOpen -> menuOpen = false
            selectionMode -> {
                selectionMode = false
                selected = emptySet()
            }
            searchOpen -> {
                closeSearchKeepPosition()
            }
            previousGroupMode != null -> {
                groupMode = previousGroupMode!!
                previousGroupMode = null
                prefs.edit().putString("groupMode", groupMode.name).apply()
            }
            else -> exitDialog = true
        }
    }

    val sections = remember(ordered, groupMode) {
        buildSections(ordered, groupMode)
    }

    val defaultColumns = when (groupMode) {
        GroupMode.MONTH -> 8
        GroupMode.DAY -> 4
        GroupMode.ALL -> 4
        GroupMode.YEAR -> 3
    }
    val columns = customColumns ?: defaultColumns

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            when {
                selectionMode -> SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = ordered.isNotEmpty() && selected.size == ordered.size,
                    onClose = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectionMode = false
                        selected = emptySet()
                    },
                    onAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selected = if (ordered.isNotEmpty() && selected.size == ordered.size) {
                            emptySet()
                        } else {
                            ordered.map { it.key }.toSet()
                        }
                    }
                )

                searchOpen -> SearchTopBar(
                    query = searchText,
                    onQuery = { searchText = it },
                    onClose = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        closeSearchKeepPosition()
                    }
                )

                else -> CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "圖庫",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        Box {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                menuOpen = true
                            }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "檢視方式：" + groupMode.label,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = { }
                                )
                                GroupMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        trailingIcon = {
                                            if (mode == groupMode) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            groupMode = mode
                                            previousGroupMode = null
                                            prefs.edit().putString("groupMode", mode.name).apply()
                                            menuOpen = false
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    )
                                }

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = { Text("跳到年份") },
                                    onClick = {
                                        menuOpen = false
                                        yearDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "縮圖密度：" +
                                                (customColumns?.let { it.toString() + " 欄" } ?: "自動")
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        densityDialog = true
                                    }
                                )

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "顯示內容：" + mediaFilter.label,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = { }
                                )
                                MediaFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.label) },
                                        trailingIcon = {
                                            if (filter == mediaFilter) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            mediaFilter = filter
                                            prefs.edit().putString("mediaFilter", filter.name).apply()
                                            menuOpen = false
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    )
                                }

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = { Text("地圖相簿・即將開放") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Map, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        infoDialog = "地圖相簿入口先保留，後續版本再正式開放。"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("重複與相似照片・後續開放") },
                                    leadingIcon = {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        infoDialog = "重複、相似照片與照片比對會在後續版本加入。"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("照片整理工具・下一階段") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Tune, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        infoDialog = "時間、GPS、檔名與 Metadata 批次整理會整合回這裡。"
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            sortDialog = true
                        }) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("排序")
                        }
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectionMode = true
                        }) {
                            Text("選取")
                        }
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
                        media.filter { selected.contains(it.key) }
                            .forEach { repository.setFavorite(it.key, true) }
                    },
                    onOrganize = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        infoDialog = "批次整理工具會在下一階段接回。"
                    },
                    onDelete = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val chosen = media.filter { selected.contains(it.key) }
                        pendingTrashKeys = chosen.map { it.key }.toSet()
                        pendingViewerNextKey = null
                        val request = repository.trashRequest(chosen)
                        if (request != null) {
                            trashDialogActive = true
                            trashLauncher.launch(
                                IntentSenderRequest.Builder(request.intentSender).build()
                            )
                        } else {
                            media = media.filterNot { pendingTrashKeys.contains(it.key) }
                            selected = emptySet()
                            selectionMode = false
                            pendingTrashKeys = emptySet()
                            scope.launch {
                                delay(250)
                                reload()
                            }
                        }
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !repository.hasAnyMediaPermission() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { permissionDialog = true }) {
                            Text("開啟照片權限")
                        }
                    }
                }

                loading && media.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                ordered.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchText.isNotBlank()) {
                                "沒有找到符合的照片"
                            } else {
                                "目前沒有可顯示的照片"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                groupMode == GroupMode.YEAR -> {
                    YearOverview(
                        groups = buildMonthBuckets(ordered),
                        state = yearGridState,
                        selectionMode = selectionMode,
                        selected = selected,
                        targetYear = targetYear,
                        onTargetConsumed = { targetYear = null },
                        onMonthClick = { bucket ->
                            if (selectionMode) {
                                val keys = bucket.items.map { it.key }.toSet()
                                selected = if (keys.all { selected.contains(it) }) {
                                    selected - keys
                                } else {
                                    selected + keys
                                }
                            } else {
                                previousGroupMode = GroupMode.YEAR
                                groupMode = GroupMode.MONTH
                                prefs.edit().putString("groupMode", GroupMode.MONTH.name).apply()
                                targetMonthKey = bucket.key
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }

                else -> {
                    RegularGrid(
                        sections = sections,
                        columns = columns,
                        state = gridState,
                        selectionMode = selectionMode,
                        selected = selected,
                        targetMonthKey = targetMonthKey,
                        targetMediaKey = targetMediaKey,
                        onTargetConsumed = { targetMonthKey = null },
                        onMediaTargetConsumed = { targetMediaKey = null },
                        isFavorite = { repository.isFavorite(it) },
                        onClick = { item ->
                            if (suppressClickKey == item.key) {
                                suppressClickKey = null
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (selectionMode) {
                                    selected = toggleSelected(selected, item.key)
                                } else {
                                    viewerKey = item.key
                                }
                            }
                        },
                        onLongPressStart = { key, add ->
                            suppressClickKey = key
                            scope.launch {
                                delay(700)
                                if (suppressClickKey == key) suppressClickKey = null
                            }
                            if (!selectionMode) selectionMode = true
                            selected = if (add) selected + key else selected - key
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragSelect = { key, add ->
                            if (!selectionMode) selectionMode = true
                            selected = if (add) selected + key else selected - key
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onSelectSection = { list ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val keys = list.map { it.key }.toSet()
                            selected = if (keys.all { selected.contains(it) }) {
                                selected - keys
                            } else {
                                selected + keys
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun toggleSelected(source: Set<String>, key: String): Set<String> {
    return if (source.contains(key)) source - key else source + key
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onAll: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                "已選 " + selectedCount.toString() + " 項",
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "取消")
            }
        },
        actions = {
            TextButton(onClick = onAll) {
                Text(if (allSelected) "取消全選" else "全選")
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun SearchTopBar(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = {
                Text("搜尋日期、檔名；地點與照片文字後續加入")
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "關閉")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )
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
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionAction(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = Icons.Default.Share,
                label = "分享",
                onClick = onShare
            )
            SelectionAction(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = Icons.Default.FavoriteBorder,
                label = "收藏",
                onClick = onFavorite
            )
            SelectionAction(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = Icons.Default.Tune,
                label = "整理",
                onClick = onOrganize
            )
            SelectionAction(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = Icons.Default.DeleteOutline,
                label = "刪除",
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SelectionAction(
    modifier: Modifier,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SortDialog(
    field: SortField,
    descending: Boolean,
    onField: (SortField) -> Unit,
    onDirection: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序") },
        text = {
            Column {
                SortField.entries.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = item == field,
                            onClick = { onField(item) }
                        )
                        Text(
                            item.label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                val descendingLabel = when (field) {
                    SortField.FILE_NAME -> "Z → A"
                    SortField.CAPTURE_TIME,
                    SortField.MODIFIED_TIME,
                    SortField.ADDED_TIME -> "新 → 舊"
                    SortField.FILE_SIZE,
                    SortField.RESOLUTION -> "大 → 小"
                }
                val ascendingLabel = when (field) {
                    SortField.FILE_NAME -> "A → Z"
                    SortField.CAPTURE_TIME,
                    SortField.MODIFIED_TIME,
                    SortField.ADDED_TIME -> "舊 → 新"
                    SortField.FILE_SIZE,
                    SortField.RESOLUTION -> "小 → 大"
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = descending,
                        onClick = { onDirection(true) }
                    )
                    Text(descendingLabel)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !descending,
                        onClick = { onDirection(false) }
                    )
                    Text(ascendingLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
private fun DensityDialog(
    current: Int?,
    onChoose: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val choices = listOf<Int?>(null, 2, 3, 4, 6, 8, 12, 16, 24, 40)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("縮圖密度") },
        text = {
            Column {
                choices.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = { onChoose(value) }
                        )
                        Text(value?.let { it.toString() + " 欄" } ?: "自動")
                    }
                }
                Text(
                    "40 欄是超密集總覽，主要用來快速定位時間區段。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun YearPickerDialog(
    years: List<Int>,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳到年份") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(years) { year ->
                    TextButton(
                        onClick = { onChoose(year) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            year.toString() + "年",
                            fontSize = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun YearOverview(
    groups: List<Pair<Int, List<MonthBucket>>>,
    state: LazyGridState,
    selectionMode: Boolean,
    selected: Set<String>,
    targetYear: Int?,
    onTargetConsumed: () -> Unit,
    onMonthClick: (MonthBucket) -> Unit
) {
    LaunchedEffect(targetYear, groups) {
        val year = targetYear ?: return@LaunchedEffect
        var index = 0
        for ((groupYear, months) in groups) {
            if (groupYear == year) {
                state.scrollToItem(index)
                onTargetConsumed()
                break
            }
            index += 1 + months.size
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        groups.forEach { (year, months) ->
            item(
                key = "year-header-" + year.toString(),
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Text(
                    year.toString() + "年",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 20.dp,
                        bottom = 10.dp
                    )
                )
            }

            gridItems(
                items = months,
                key = { "month-card-" + it.key }
            ) { bucket ->
                val keys = bucket.items.map { it.key }
                val count = keys.count { selected.contains(it) }
                MonthCard(
                    bucket = bucket,
                    selectedCount = count,
                    selectionMode = selectionMode,
                    onClick = { onMonthClick(bucket) }
                )
            }
        }
    }
}

@Composable
private fun MonthCard(
    bucket: MonthBucket,
    selectedCount: Int,
    selectionMode: Boolean,
    onClick: () -> Unit
) {
    val cover = bucket.items.firstOrNull()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(1.5.dp)
            .aspectRatio(0.92f),
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box {
            if (cover != null) {
                AsyncImage(
                    model = cover.uri,
                    contentDescription = bucket.month.toString() + "月",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Column {
                    Text(
                        bucket.month.toString() + "月",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        if (selectionMode && selectedCount > 0) {
                            selectedCount.toString() + "/" + bucket.items.size.toString() + " 項"
                        } else {
                            bucket.items.size.toString() + " 項"
                        },
                        color = Color.White.copy(alpha = 0.84f),
                        fontSize = 11.sp
                    )
                }
            }

            if (selectionMode && selectedCount == bucket.items.size && bucket.items.isNotEmpty()) {
                Box(
                    Modifier
                        .padding(7.dp)
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RegularGrid(
    sections: List<AlbumSection>,
    columns: Int,
    state: LazyGridState,
    selectionMode: Boolean,
    selected: Set<String>,
    targetMonthKey: String?,
    targetMediaKey: String?,
    onTargetConsumed: () -> Unit,
    onMediaTargetConsumed: () -> Unit,
    isFavorite: (String) -> Boolean,
    onClick: (MediaItem) -> Unit,
    onLongPressStart: (String, Boolean) -> Unit,
    onDragSelect: (String, Boolean) -> Unit,
    onSelectSection: (List<MediaItem>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val selectedState by rememberUpdatedState(selected)
    val mediaByKey = remember(sections) {
        sections.flatMap { it.items }.associateBy { it.key }
    }

    LaunchedEffect(targetMonthKey, sections) {
        val key = targetMonthKey ?: return@LaunchedEffect
        var index = 0
        for (section in sections) {
            if (section.key == key) {
                state.scrollToItem(index)
                onTargetConsumed()
                break
            }
            index += 1 + section.items.size
        }
    }

    LaunchedEffect(targetMediaKey, sections) {
        val key = targetMediaKey ?: return@LaunchedEffect
        var index = 0
        var found = false
        for (section in sections) {
            index += 1
            for (item in section.items) {
                if (item.key == key) {
                    state.scrollToItem(index)
                    found = true
                    break
                }
                index += 1
            }
            if (found) break
        }
        if (found) onMediaTargetConsumed()
    }

    val dragModifier = Modifier.pointerInput(sections, columns) {
        var addMode = true
        val visited = mutableSetOf<String>()

        fun mediaKeyAt(offset: Offset): String? {
            val hit = state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                offset.x >= info.offset.x &&
                    offset.x <= info.offset.x + info.size.width &&
                    offset.y >= info.offset.y &&
                    offset.y <= info.offset.y + info.size.height
            } ?: return null
            val raw = hit.key?.toString() ?: return null
            if (!raw.startsWith("media:")) return null
            return raw.removePrefix("media:")
        }

        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                visited.clear()
                val key = mediaKeyAt(offset)
                if (key != null && mediaByKey.containsKey(key)) {
                    addMode = !selectedState.contains(key)
                    visited.add(key)
                    onLongPressStart(key, addMode)
                }
            },
            onDrag = { change, _ ->
                change.consume()
                val key = mediaKeyAt(change.position)
                if (key != null && mediaByKey.containsKey(key) && visited.add(key)) {
                    onDragSelect(key, addMode)
                }

                val edge = 90f
                when {
                    change.position.y < edge -> {
                        scope.launch { state.scrollBy(-70f) }
                    }
                    change.position.y > size.height - edge -> {
                        scope.launch { state.scrollBy(70f) }
                    }
                }
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(2, 40)),
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .then(dragModifier),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        sections.forEach { section ->
            item(
                key = "header:" + section.key,
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(
                            start = 14.dp,
                            end = 10.dp,
                            top = 18.dp,
                            bottom = 9.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        section.title,
                        modifier = Modifier.weight(1f),
                        fontSize = if (columns >= 16) 15.sp else 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (selectionMode) {
                        val keys = section.items.map { it.key }
                        val count = keys.count { selected.contains(it) }
                        TextButton(onClick = { onSelectSection(section.items) }) {
                            val label = when {
                                count == keys.size && keys.isNotEmpty() -> {
                                    keys.size.toString() + " 項 ✓"
                                }
                                count > 0 -> count.toString() + "/" + keys.size.toString() + " 項"
                                else -> "全日 " + keys.size.toString() + " 項"
                            }
                            Text(label, fontSize = 12.sp)
                        }
                    } else if (columns < 16) {
                        Text(
                            section.items.size.toString() + " 項",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            gridItems(
                items = section.items,
                key = { "media:" + it.key }
            ) { item ->
                PhotoCell(
                    item = item,
                    selected = selected.contains(item.key),
                    selectionMode = selectionMode,
                    favorite = isFavorite(item.key),
                    dense = columns >= 12,
                    onClick = { onClick(item) }
                )
            }
        }
    }
}

@Composable
private fun PhotoCell(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    favorite: Boolean,
    dense: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(if (dense) 0.25.dp else 0.75.dp)
            .aspectRatio(1f),
        shape = RoundedCornerShape(if (dense) 1.dp else 3.dp),
        color = Color.Black
    ) {
        Box {
            AsyncImage(
                model = item.uri,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (!dense && favorite && !selectionMode) {
                Box(
                    Modifier
                        .padding(5.dp)
                        .size(23.dp)
                        .background(Color.Black.copy(alpha = 0.42f), CircleShape)
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

            if (!dense && item.kind == MediaKind.VIDEO) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(8.dp)
                        )
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
                    Text(
                        formatDuration(item.duration),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }

            if (selectionMode && !dense) {
                Box(
                    Modifier
                        .padding(6.dp)
                        .size(25.dp)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Black.copy(alpha = 0.35f)
                            },
                            CircleShape
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
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                )
            }
        }
    }
}
