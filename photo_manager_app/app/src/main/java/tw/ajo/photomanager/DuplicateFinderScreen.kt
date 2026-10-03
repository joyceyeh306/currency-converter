@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tw.ajo.photomanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class MatchTab {
    EXACT,
    SIMILAR
}

private val matchDateFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.TAIWAN)

@Composable
fun DuplicateFinderScreen(
    media: List<MediaItem>,
    repository: AlbumRepository,
    refreshVersion: Int,
    onBack: () -> Unit,
    onDeleteItems: (List<MediaItem>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(MatchTab.EXACT) }
    var result by remember {
        mutableStateOf(PhotoMatchScanResult(emptyList(), emptyList()))
    }
    var scanning by remember { mutableStateOf(true) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(1) }
    var progressLabel by remember { mutableStateOf("準備掃描照片") }
    var scanNonce by remember { mutableIntStateOf(0) }

    var activeGroup by remember { mutableStateOf<PhotoMatchGroup?>(null) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(media, refreshVersion, scanNonce) {
        scanning = true
        progressDone = 0
        progressTotal = 1
        progressLabel = "準備掃描照片"

        result = repository.scanPhotoMatches(media) { done, total, label ->
            withContext(Dispatchers.Main) {
                progressDone = done
                progressTotal = total.coerceAtLeast(1)
                progressLabel = label
            }
        }

        scanning = false
    }

    val group = activeGroup
    if (group != null) {
        MatchGroupDetailScreen(
            group = group,
            selectedKeys = selectedKeys,
            onToggle = { key ->
                selectedKeys =
                    if (selectedKeys.contains(key)) {
                        selectedKeys - key
                    } else {
                        selectedKeys + key
                    }
            },
            onKeepOnly = { keepKey ->
                selectedKeys = group.items
                    .asSequence()
                    .map { it.key }
                    .filter { it != keepKey }
                    .toSet()
            },
            onBack = {
                selectedKeys = emptySet()
                activeGroup = null
            },
            onDelete = {
                val deleting = group.items.filter {
                    selectedKeys.contains(it.key)
                }

                if (deleting.isNotEmpty()) {
                    selectedKeys = emptySet()
                    activeGroup = null
                    onDeleteItems(deleting)
                }
            }
        )
        return
    }

    val groups = when (selectedTab) {
        MatchTab.EXACT -> result.exactGroups
        MatchTab.SIMILAR -> result.similarGroups
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "重複與相似照片",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = !scanning,
                        onClick = { scanNonce += 1 }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重新掃描"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == MatchTab.EXACT,
                    onClick = { selectedTab = MatchTab.EXACT },
                    label = {
                        Text(
                            "完全重複 " + result.exactGroups.size + " 組"
                        )
                    }
                )
                FilterChip(
                    selected = selectedTab == MatchTab.SIMILAR,
                    onClick = { selectedTab = MatchTab.SIMILAR },
                    label = {
                        Text(
                            "相似照片 " + result.similarGroups.size + " 組"
                        )
                    }
                )
            }

            if (scanning) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(progressLabel, fontSize = 12.sp)
                        }

                        LinearProgressIndicator(
                            progress = {
                                progressDone.toFloat() /
                                    progressTotal.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "第一次掃描會比較久；之後會使用本機快取加快速度。",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!scanning && groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (selectedTab == MatchTab.EXACT) {
                            "目前沒有找到完全重複的照片。"
                        } else {
                            "目前沒有找到明顯相似的照片。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = groups,
                        key = { itemGroup ->
                            itemGroup.type.name + ":" +
                                itemGroup.items.joinToString(",") { it.key }
                                    .hashCode()
                                    .toString()
                        }
                    ) { itemGroup ->
                        MatchGroupCard(
                            group = itemGroup,
                            onClick = {
                                selectedKeys = emptySet()
                                activeGroup = itemGroup
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchGroupDetailScreen(
    group: PhotoMatchGroup,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    onKeepOnly: (String) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (group.type == PhotoMatchType.EXACT) {
                                "完全重複"
                            } else {
                                "相似照片"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            group.items.size.toString() + " 張・已選 " +
                                selectedKeys.size + " 張",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedKeys.isEmpty()) {
                        Text(
                            if (group.type == PhotoMatchType.EXACT) {
                                "點照片選取要刪的副本，或直接按「保留這張」。"
                            } else {
                                "點照片選取要刪除的照片。相似照片不會自動替妳決定。"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "準備刪除 " + selectedKeys.size + " 張",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        enabled = selectedKeys.isNotEmpty(),
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (selectedKeys.isEmpty()) {
                                "刪除已選"
                            } else {
                                "刪除已選（" + selectedKeys.size + "）"
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            gridItems(
                items = group.items,
                key = { it.key }
            ) { item ->
                MatchPhotoCard(
                    item = item,
                    selected = selectedKeys.contains(item.key),
                    exactGroup = group.type == PhotoMatchType.EXACT,
                    onToggle = { onToggle(item.key) },
                    onKeep = { onKeepOnly(item.key) }
                )
            }
        }
    }
}

@Composable
private fun MatchPhotoCard(
    item: MediaItem,
    selected: Boolean,
    exactGroup: Boolean,
    onToggle: () -> Unit,
    onKeep: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Crop
                )

                if (selected) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(28.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已選取",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Column(
                Modifier.padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = 7.dp,
                    bottom = 5.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    item.wallTime.format(matchDateFormatter),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    item.width.toString() + " × " + item.height +
                        "・" + formatMatchBytes(item.size),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (exactGroup) {
                    TextButton(
                        onClick = onKeep,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保留這張", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchGroupCard(
    group: PhotoMatchGroup,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                group.items.take(4).forEach { item ->
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                }

                repeat((4 - group.items.take(4).size).coerceAtLeast(0)) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (group.type) {
                            PhotoMatchType.EXACT -> "完全相同"
                            PhotoMatchType.SIMILAR -> "畫面相似"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        buildString {
                            append(group.items.size)
                            append(" 張")
                            if (
                                group.type == PhotoMatchType.EXACT &&
                                group.reclaimableBytes > 0L
                            ) {
                                append("・刪除多餘副本約可釋放 ")
                                append(formatMatchBytes(group.reclaimableBytes))
                            }
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onClick) {
                    Text("整理")
                }
            }
        }
    }
}

private fun formatMatchBytes(bytes: Long): String {
    if (bytes < 1024L) return bytes.toString() + " B"

    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1

    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    return if (value >= 100.0) {
        String.format(Locale.TAIWAN, "%.0f %s", value, units[unitIndex])
    } else {
        String.format(Locale.TAIWAN, "%.1f %s", value, units[unitIndex])
    }
}
