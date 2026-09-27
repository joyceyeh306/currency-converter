@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package tw.ajo.photomanager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class HomeScreen { GALLERY, COLLECTIONS, MAP }

data class CollectionCardModel(
    val id: String,
    val title: String,
    val count: Int,
    val cover: MediaItem?,
    val mediaKeys: Set<String>,
    val isMap: Boolean = false,
    val isCustom: Boolean = false,
    val isAllPhotos: Boolean = false,
    val isArchived: Boolean = false
)

@Composable
fun CollectionHomeScreen(
    media: List<MediaItem>,
    repository: AlbumRepository,
    refreshVersion: Int,
    onBack: () -> Unit,
    onOpenCollection: (String, String, Set<String>) -> Unit,
    onOpenMap: () -> Unit
) {
    BackHandler { onBack() }

    var createDialog by remember { mutableStateOf(false) }
    var localVersion by remember(refreshVersion) { mutableIntStateOf(refreshVersion) }

    LaunchedEffect(media) {
        repository.updateImportedDeviceIndex(media)
        localVersion += 1
    }

    val cards = remember(media, localVersion, refreshVersion) {
        buildCollectionCards(media, repository)
    }
    val orderedIds = remember(cards, localVersion, refreshVersion) {
        repository.collectionOrder(cards.map { it.id })
    }
    val orderedCards = remember(cards, orderedIds) {
        orderedIds.mapNotNull { id -> cards.firstOrNull { it.id == id } }
    }

    // Keep one stable list instance. Dynamic cards (first favorite, a newly-created
    // album, etc.) can now appear without replacing the drag state mid-session.
    val current = remember { mutableStateListOf<CollectionCardModel>() }
    LaunchedEffect(orderedCards) {
        current.clear()
        current.addAll(orderedCards)
    }

    if (createDialog) {
        CreateAlbumDialog(
            onDismiss = { createDialog = false },
            onCreate = {
                repository.createCustomAlbum(it)
                createDialog = false
                localVersion += 1
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("圖集", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "智慧分類・我的相簿",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回圖庫")
                    }
                },
                actions = {
                    IconButton(onClick = { createDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增相簿")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "長按卡片即可拖曳排序",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 8.dp)
            )

            ReorderableCollectionGrid(
                cards = current,
                repository = repository,
                onClick = { card ->
                    if (card.isMap) onOpenMap()
                    else onOpenCollection(card.id, card.title, card.mediaKeys)
                },
                modifier = Modifier.weight(1f)
            )

            Surface(
                onClick = { createDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text("新增自建相簿", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun buildCollectionCards(
    media: List<MediaItem>,
    repository: AlbumRepository
): List<CollectionCardModel> {
    val result = mutableListOf<CollectionCardModel>()

    fun addSmart(id: String, title: String, items: List<MediaItem>) {
        if (items.isNotEmpty()) {
            result.add(
                CollectionCardModel(
                    id = id,
                    title = title,
                    count = items.size,
                    cover = items.firstOrNull(),
                    mediaKeys = items.map { it.key }.toSet()
                )
            )
        }
    }

    addSmart("smart:favorites", "最愛", media.filter { repository.isFavorite(it.key) })
    addSmart("smart:videos", "影片", media.filter { it.kind == MediaKind.VIDEO })
    addSmart("smart:screenshot", "螢幕截圖", media.filter { repository.smartSourceTag(it) == "screenshot" })
    addSmart("smart:line", "LINE 圖片", media.filter { repository.smartSourceTag(it) == "line" })
    addSmart("smart:instagram", "Instagram", media.filter { repository.smartSourceTag(it) == "instagram" })
    addSmart("smart:downloads", "下載項目", media.filter { repository.smartSourceTag(it) == "downloads" })
    addSmart("smart:camera", "相機", media.filter { repository.smartSourceTag(it) == "camera" })

    val otherDeviceKeys = repository.importedFromOtherDeviceKeys(media)
    addSmart(
        "smart:other_devices",
        "其他裝置匯入",
        media.filter { otherDeviceKeys.contains(it.key) }
    )

    result.add(
        CollectionCardModel(
            id = "smart:map",
            title = "照片地圖",
            count = 0,
            cover = media.firstOrNull(),
            mediaKeys = emptySet(),
            isMap = true
        )
    )

    repository.customAlbums().forEach { album ->
        val albumItems = media.filter { album.mediaKeys.contains(it.key) }
        result.add(
            CollectionCardModel(
                id = "custom:" + album.id,
                title = album.name,
                count = albumItems.size,
                cover = albumItems.firstOrNull(),
                mediaKeys = album.mediaKeys,
                isCustom = true
            )
        )
    }

    val archived = repository.archivedKeys()
    val archivedItems = media.filter { archived.contains(it.key) }

    // These are system collections, not a second main navigation layer. Existing
    // users' saved card order remains intact; new cards are appended and can be
    // dragged lower if they are only occasionally needed.
    result.add(
        CollectionCardModel(
            id = "system:archived",
            title = "已收納",
            count = archivedItems.size,
            cover = archivedItems.firstOrNull(),
            mediaKeys = archivedItems.map { it.key }.toSet(),
            isArchived = true
        )
    )
    result.add(
        CollectionCardModel(
            id = "system:all",
            title = "全部照片",
            count = media.size,
            cover = media.firstOrNull(),
            mediaKeys = media.map { it.key }.toSet(),
            isAllPhotos = true
        )
    )

    return result
}

@Composable
private fun ReorderableCollectionGrid(
    cards: MutableList<CollectionCardModel>,
    repository: AlbumRepository,
    onClick: (CollectionCardModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var suppressClickId by remember { mutableStateOf<String?>(null) }

    fun itemAt(position: Offset): androidx.compose.foundation.lazy.grid.LazyGridItemInfo? {
        return gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            position.x >= info.offset.x &&
                position.x <= info.offset.x + info.size.width &&
                position.y >= info.offset.y &&
                position.y <= info.offset.y + info.size.height
        }
    }

    fun nearestCard(position: Offset): androidx.compose.foundation.lazy.grid.LazyGridItemInfo? {
        return gridState.layoutInfo.visibleItemsInfo
            .filter { info -> cards.any { it.id == info.key?.toString() } }
            .minByOrNull { info ->
                val cx = info.offset.x + info.size.width / 2f
                val cy = info.offset.y + info.size.height / 2f
                val dx = position.x - cx
                val dy = position.y - cy
                dx * dx + dy * dy
            }
    }

    val gestureModifier = Modifier.pointerInput(cards.map { it.id }) {
        detectDragGesturesAfterLongPress(
            onDragStart = { start ->
                val info = itemAt(start)
                val id = info?.key?.toString()
                if (id != null && cards.any { it.id == id } && info != null) {
                    draggedId = id
                    dragOffset = Offset.Zero
                    grabOffset = start - Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
                    suppressClickId = id
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { change, amount ->
                val id = draggedId ?: return@detectDragGesturesAfterLongPress
                change.consume()
                dragOffset += amount

                val fromInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                    it.key?.toString() == id
                }
                val targetInfo = nearestCard(change.position)
                val targetId = targetInfo?.key?.toString()

                if (targetInfo != null && targetId != null && targetId != id) {
                    val from = cards.indexOfFirst { it.id == id }
                    val to = cards.indexOfFirst { it.id == targetId }
                    if (from >= 0 && to >= 0 && from != to) {
                        val moved = cards.removeAt(from)
                        cards.add(to, moved)

                        // Base the floating card directly on the destination cell.
                        // This lets one gesture jump from the first row to the third
                        // (or farther) instead of forcing adjacent-row exchanges.
                        dragOffset = change.position -
                            Offset(targetInfo.offset.x.toFloat(), targetInfo.offset.y.toFloat()) -
                            grabOffset
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                } else if (fromInfo != null) {
                    dragOffset = change.position -
                        Offset(fromInfo.offset.x.toFloat(), fromInfo.offset.y.toFloat()) -
                        grabOffset
                }

                val edge = 92f
                when {
                    change.position.y < edge -> scope.launch { gridState.scrollBy(-48f) }
                    change.position.y > size.height - edge -> scope.launch { gridState.scrollBy(48f) }
                }
            },
            onDragEnd = {
                val id = draggedId
                draggedId = null
                dragOffset = Offset.Zero
                repository.saveCollectionOrder(cards.map { it.id })
                if (id != null) {
                    scope.launch {
                        delay(220)
                        if (suppressClickId == id) suppressClickId = null
                    }
                }
            },
            onDragCancel = {
                val id = draggedId
                draggedId = null
                dragOffset = Offset.Zero
                if (id != null) {
                    scope.launch {
                        delay(180)
                        if (suppressClickId == id) suppressClickId = null
                    }
                }
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = modifier
            .fillMaxWidth()
            .then(gestureModifier),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(cards, key = { it.id }) { card ->
            val isDragging = draggedId == card.id
            val scale by animateFloatAsState(
                targetValue = if (isDragging) 1.035f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "collection-scale"
            )
            val infinite = rememberInfiniteTransition(label = "collection-wiggle")
            val wiggle by infinite.animateFloat(
                initialValue = -0.32f,
                targetValue = 0.32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(180),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "collection-wiggle-value"
            )

            val placement = if (isDragging) {
                Modifier
            } else {
                Modifier.animateItemPlacement(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }

            CollectionCard(
                card = card,
                onClick = {
                    if (draggedId == null && suppressClickId != card.id) onClick(card)
                },
                modifier = placement
                    .zIndex(if (isDragging) 4f else 0f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = if (isDragging) wiggle else 0f
                        translationX = if (isDragging) dragOffset.x else 0f
                        translationY = if (isDragging) dragOffset.y else 0f
                        shadowElevation = if (isDragging) 15f else 0f
                    }
            )
        }
    }
}

@Composable
private fun CollectionCard(
    card: CollectionCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(0.86f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Box {
            if (card.cover != null) {
                AsyncImage(
                    model = card.cover.uri,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            card.isMap -> Icons.Default.Map
                            card.isAllPhotos -> Icons.Default.PhotoLibrary
                            card.isArchived -> Icons.Default.Inventory2
                            else -> Icons.Default.PhotoAlbum
                        },
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Column {
                    Text(
                        card.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        if (card.isMap) "地點瀏覽" else card.count.toString() + " 項",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                card.isMap -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.padding(5.dp).size(15.dp)
                        )
                    }
                }
                card.isArchived -> {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(18.dp)
                    )
                }
                card.isAllPhotos -> {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(18.dp)
                    )
                }
                card.mediaKeys.any { it.startsWith("VIDEO:") } -> {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增相簿") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("相簿名稱") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotBlank(),
                onClick = { onCreate(name) }
            ) { Text("建立") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun AddToAlbumDialog(
    repository: AlbumRepository,
    selectedKeys: Set<String>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    var createMode by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    var archiveAfterAdd by remember(selectedKeys) {
        mutableStateOf(
            selectedKeys.isNotEmpty() && selectedKeys.all { repository.isArchived(it) }
        )
    }
    val albums = remember(version) { repository.customAlbums() }
    val allAlreadyArchived = selectedKeys.isNotEmpty() &&
        selectedKeys.all { repository.isArchived(it) }

    fun finishAdd(albumId: String) {
        repository.addToCustomAlbum(albumId, selectedKeys)
        // The checkbox is the explicit decision for whether these selected items
        // should remain visible on the normal photo wall.
        repository.setArchived(selectedKeys, archiveAfterAdd)
        onChanged()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (createMode) "建立相簿" else "加入相簿") },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = archiveAfterAdd,
                            onCheckedChange = { archiveAfterAdd = it }
                        )
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(
                                "收納這些照片",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                if (archiveAfterAdd) {
                                    "加入相簿後，不顯示在平常的照片牆"
                                } else {
                                    "相簿與平常照片牆都會看得到"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (createMode) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("相簿名稱") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    if (albums.isEmpty()) {
                        Text(
                            "目前還沒有自建相簿。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    albums.forEach { album ->
                        Surface(
                            onClick = { finishAdd(album.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text(album.name, fontWeight = FontWeight.Medium)
                                Text(
                                    album.mediaKeys.size.toString() + " 項",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    TextButton(onClick = { createMode = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("建立新相簿")
                    }

                    if (allAlreadyArchived) {
                        TextButton(
                            onClick = {
                                repository.setArchived(selectedKeys, false)
                                onChanged()
                                onDismiss()
                            }
                        ) {
                            Text("顯示回照片牆")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (createMode) {
                TextButton(
                    enabled = newName.trim().isNotBlank(),
                    onClick = {
                        val album = repository.createCustomAlbum(newName)
                        version += 1
                        finishAdd(album.id)
                    }
                ) { Text("建立並加入") }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        dismissButton = {
            if (createMode) {
                TextButton(onClick = { createMode = false }) { Text("返回") }
            }
        }
    )
}
