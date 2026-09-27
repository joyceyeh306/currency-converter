@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tw.ajo.photomanager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

enum class HomeScreen { GALLERY, COLLECTIONS, MAP }

data class CollectionCardModel(
    val id: String,
    val title: String,
    val count: Int,
    val cover: MediaItem?,
    val mediaKeys: Set<String>,
    val isMap: Boolean = false,
    val isCustom: Boolean = false
)

@Composable
fun CollectionHomeScreen(
    media: List<MediaItem>,
    repository: AlbumRepository,
    refreshVersion: Int,
    onBack: () -> Unit,
    onOpenCollection: (String, Set<String>) -> Unit,
    onOpenMap: () -> Unit
) {
    BackHandler { onBack() }

    var createDialog by remember { mutableStateOf(false) }
    var localVersion by remember(refreshVersion) { mutableIntStateOf(refreshVersion) }

    val cards = remember(media, localVersion, refreshVersion) {
        buildCollectionCards(media, repository)
    }
    val orderedIds = remember(cards, localVersion, refreshVersion) {
        repository.collectionOrder(cards.map { it.id })
    }
    val orderedCards = remember(cards, orderedIds) {
        orderedIds.mapNotNull { id -> cards.firstOrNull { it.id == id } }
    }
    val current = remember(orderedCards) {
        mutableStateListOf<CollectionCardModel>().apply { addAll(orderedCards) }
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
                    else onOpenCollection(card.title, card.mediaKeys)
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
    return result
}

@Composable
private fun ReorderableCollectionGrid(
    cards: MutableList<CollectionCardModel>,
    repository: AlbumRepository,
    onClick: (CollectionCardModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thresholdX = with(density) { 86.dp.toPx() }
    val thresholdY = with(density) { 98.dp.toPx() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(cards, key = { it.id }) { card ->
            var dragging by remember(card.id) { mutableStateOf(false) }
            var dx by remember(card.id) { mutableFloatStateOf(0f) }
            var dy by remember(card.id) { mutableFloatStateOf(0f) }

            val scale by animateFloatAsState(
                targetValue = if (dragging) 1.055f else 1f,
                animationSpec = tween(110),
                label = "collection-scale"
            )
            val infinite = rememberInfiniteTransition(label = "collection-wiggle")
            val wiggle by infinite.animateFloat(
                initialValue = -0.7f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(135),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "collection-wiggle-value"
            )

            CollectionCard(
                card = card,
                onClick = { if (!dragging) onClick(card) },
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = if (dragging) wiggle else 0f
                        translationX = if (dragging) dx * 0.2f else 0f
                        translationY = if (dragging) dy * 0.2f else 0f
                        shadowElevation = if (dragging) 20f else 0f
                    }
                    .pointerInput(card.id, cards.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = true
                                dx = 0f
                                dy = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                dragging = false
                                dx = 0f
                                dy = 0f
                                repository.saveCollectionOrder(cards.map { it.id })
                            },
                            onDragCancel = {
                                dragging = false
                                dx = 0f
                                dy = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dx += amount.x
                                dy += amount.y
                                var deltaIndex = 0
                                if (dx > thresholdX) {
                                    deltaIndex = 1
                                    dx = 0f
                                } else if (dx < -thresholdX) {
                                    deltaIndex = -1
                                    dx = 0f
                                }
                                if (dy > thresholdY) {
                                    deltaIndex = 3
                                    dy = 0f
                                } else if (dy < -thresholdY) {
                                    deltaIndex = -3
                                    dy = 0f
                                }

                                if (deltaIndex != 0) {
                                    val from = cards.indexOfFirst { it.id == card.id }
                                    val to = (from + deltaIndex).coerceIn(0, cards.lastIndex)
                                    if (from >= 0 && to != from) {
                                        val moved = cards.removeAt(from)
                                        cards.add(to, moved)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        )
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
                        if (card.isMap) Icons.Default.Map else Icons.Default.PhotoAlbum,
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

            if (card.isMap) {
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
            } else if (card.mediaKeys.any { it.startsWith("VIDEO:") }) {
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
    val albums = remember(version) { repository.customAlbums() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (createMode) "建立相簿" else "加入相簿") },
        text = {
            Column {
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
                            onClick = {
                                repository.addToCustomAlbum(album.id, selectedKeys)
                                onChanged()
                                onDismiss()
                            },
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
                }
            }
        },
        confirmButton = {
            if (createMode) {
                TextButton(
                    enabled = newName.trim().isNotBlank(),
                    onClick = {
                        val album = repository.createCustomAlbum(newName)
                        repository.addToCustomAlbum(album.id, selectedKeys)
                        version += 1
                        onChanged()
                        onDismiss()
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
