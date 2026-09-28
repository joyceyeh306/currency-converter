@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package tw.ajo.photomanager

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

@Composable
fun PhotoOrganizerScreen(
    selectedItems: List<MediaItem>,
    repository: AlbumRepository,
    onBack: () -> Unit,
    onOpenAlbumOrganize: (Set<String>) -> Unit,
    onSearchKeyword: (String) -> Unit,
    onKeywordsChanged: () -> Unit
) {
    BackHandler { onBack() }

    val haptic = LocalHapticFeedback.current
    val selectedKeys = remember(selectedItems) { selectedItems.map { it.key }.toSet() }
    var localVersion by remember { mutableIntStateOf(0) }
    var keywordDialog by remember { mutableStateOf(false) }
    var simpleDialog by remember { mutableStateOf<OrganizerDialogKind?>(null) }

    val selectedKeywordCounts = remember(selectedKeys, localVersion) {
        repository.allKeywordCounts(selectedKeys)
    }
    val allKeywordCounts = remember(localVersion) {
        repository.allKeywordCounts()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "照片整理工具",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (selectedItems.isEmpty()) "關鍵字可直接搜尋照片"
                            else "已選 ${selectedItems.size} 項",
                            fontSize = 11.sp,
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
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 8.dp,
                bottom = 30.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "整理",
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OrganizerToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PhotoLibrary,
                        title = "相簿與收納",
                        subtitle = "加入相簿・收納照片",
                        enabled = selectedItems.isNotEmpty(),
                        onClick = { onOpenAlbumOrganize(selectedKeys) }
                    )
                    OrganizerToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Label,
                        title = "關鍵字",
                        subtitle = if (selectedItems.isNotEmpty()) "加入或移除關鍵字" else "管理與搜尋關鍵字",
                        enabled = true,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            keywordDialog = true
                        }
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OrganizerToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccessTime,
                        title = "拍攝時間",
                        subtitle = "查看時間與來源",
                        enabled = selectedItems.isNotEmpty(),
                        onClick = { simpleDialog = OrganizerDialogKind.TIME }
                    )
                    OrganizerToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocationOn,
                        title = "GPS 位置",
                        subtitle = "查看目前定位",
                        enabled = selectedItems.isNotEmpty(),
                        onClick = { simpleDialog = OrganizerDialogKind.GPS }
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OrganizerToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TextFields,
                        title = "檔名整理",
                        subtitle = "檢查檔名・批次準備",
                        enabled = selectedItems.isNotEmpty(),
                        onClick = { simpleDialog = OrganizerDialogKind.FILENAME }
                    )
                    OrganizerToolCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.History,
                        title = "修改紀錄",
                        subtitle = "查看整理操作",
                        enabled = true,
                        onClick = { simpleDialog = OrganizerDialogKind.HISTORY }
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "關鍵字先存於「ㄚ喬的相簿」，不修改原始照片。",
                            modifier = Modifier.padding(start = 10.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (keywordDialog) {
        KeywordEditorDialog(
            selectedKeys = selectedKeys,
            repository = repository,
            version = localVersion,
            onDismiss = { keywordDialog = false },
            onSearch = { word ->
                keywordDialog = false
                onSearchKeyword(word)
            },
            onChanged = {
                localVersion += 1
                onKeywordsChanged()
            }
        )
    }

    simpleDialog?.let { kind ->
        OrganizerInfoDialog(
            kind = kind,
            selectedItems = selectedItems,
            repository = repository,
            onDismiss = { simpleDialog = null }
        )
    }
}

@Composable
private fun KeywordHeroCard(
    selectedCount: Int,
    selectedKeywordCounts: List<Pair<String, Int>>,
    allKeywordCounts: List<Pair<String, Int>>,
    onOpen: () -> Unit,
    onSearch: (String) -> Unit
) {
    val preview = if (selectedCount > 0) selectedKeywordCounts else allKeywordCounts

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
                ) {
                    Icon(
                        Icons.Default.Label,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp).size(22.dp)
                    )
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("關鍵字", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (selectedCount > 0) "替這 ${selectedCount} 項加入或移除關鍵字"
                        else "管理關鍵字，也可以直接用關鍵字搜尋",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(13.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(preview.take(8), key = { it.first }) { (word, count) ->
                        Surface(
                            modifier = Modifier.clickable {
                                if (selectedCount == 0) onSearch(word) else onOpen()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f)
                        ) {
                            Text(
                                if (selectedCount == 0) "$word · $count" else word,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizerToolCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(108.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.46f else 0.24f)
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
            Column {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.95f else 0.5f)
                )
            }
        }
    }
}

@Composable
private fun KeywordEditorDialog(
    selectedKeys: Set<String>,
    repository: AlbumRepository,
    version: Int,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onChanged: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val selectedCounts = remember(selectedKeys, version) { repository.allKeywordCounts(selectedKeys) }
    val suggestions = remember(version) { repository.allKeywordCounts() }
    val selectedMap = selectedCounts.associate { it.first.lowercase() to it.second }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(if (selectedKeys.isEmpty()) "關鍵字" else "編輯關鍵字")
                Text(
                    if (selectedKeys.isEmpty()) "點關鍵字即可搜尋照片"
                    else "已選 ${selectedKeys.size} 項・資料只存在本 App",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                if (selectedKeys.isNotEmpty()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("輸入關鍵字，例如：水草、釜山") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        trailingIcon = {
                            TextButton(
                                enabled = input.isNotBlank(),
                                onClick = {
                                    val words = input
                                        .split(',', '，', '、', '\n')
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                    repository.addKeywords(selectedKeys, words)
                                    input = ""
                                    onChanged()
                                }
                            ) { Text("加入") }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedCounts.isNotEmpty()) {
                        item {
                            Text(
                                "已套用",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(selectedCounts, key = { "selected_" + it.first }) { (word, count) ->
                            val allSelected = count == selectedKeys.size
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = allSelected,
                                    onClick = {
                                        repository.removeKeyword(selectedKeys, word)
                                        onChanged()
                                    },
                                    label = {
                                        Text(
                                            if (allSelected) word
                                            else "$word · $count/${selectedKeys.size}"
                                        )
                                    }
                                )
                                Text(
                                    "點一下移除",
                                    modifier = Modifier.padding(start = 8.dp),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item { Spacer(Modifier.height(5.dp)) }
                    }

                    if (suggestions.isNotEmpty()) {
                        item {
                            Text(
                                if (selectedKeys.isEmpty()) "所有關鍵字" else "既有關鍵字",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(suggestions.take(80), key = { "suggest_" + it.first }) { (word, count) ->
                            val alreadyAll = selectedKeys.isNotEmpty() &&
                                (selectedMap[word.lowercase()] ?: 0) == selectedKeys.size
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedKeys.isEmpty()) {
                                            onSearch(word)
                                        } else if (!alreadyAll) {
                                            repository.addKeywords(selectedKeys, listOf(word))
                                            onChanged()
                                        }
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(word, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    Text(
                                        if (alreadyAll) "已套用" else "$count 張",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (selectedKeys.isEmpty()) {
                        item {
                            Text(
                                "目前還沒有關鍵字。先回照片牆選取照片，再按「整理」。",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

private enum class OrganizerDialogKind { TIME, GPS, FILENAME, DETAIL, HISTORY }

@Composable
private fun OrganizerInfoDialog(
    kind: OrganizerDialogKind,
    selectedItems: List<MediaItem>,
    repository: AlbumRepository,
    onDismiss: () -> Unit
) {
    val first = selectedItems.firstOrNull()
    var detail by remember(first?.key, kind) { mutableStateOf<DetailInfo?>(null) }

    if ((kind == OrganizerDialogKind.GPS || kind == OrganizerDialogKind.DETAIL) && first != null) {
        LaunchedEffect(first.key, kind) {
            detail = withContext(Dispatchers.IO) { repository.readDetail(first) }
        }
    }

    val title = when (kind) {
        OrganizerDialogKind.TIME -> "拍攝時間"
        OrganizerDialogKind.GPS -> "GPS 位置"
        OrganizerDialogKind.FILENAME -> "檔名整理"
        OrganizerDialogKind.DETAIL -> "完整照片資料"
        OrganizerDialogKind.HISTORY -> "修改紀錄"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                when (kind) {
                    OrganizerDialogKind.TIME -> {
                        if (selectedItems.isEmpty()) {
                            item { Text("請先選取照片。") }
                        } else {
                            item {
                                Text(
                                    if (selectedItems.size == 1) "目前照片" else "已選 ${selectedItems.size} 項",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            items(selectedItems.take(12), key = { it.key }) { item ->
                                Column {
                                    Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        item.wallTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")),
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        item.timeSource,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            item {
                                Text(
                                    "這一版先把時間與判定來源集中到整理工具；安全調整與寫回原始資料會沿用「預覽 → 確認 → 寫入 → 驗證」流程。",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OrganizerDialogKind.GPS -> {
                        if (first == null) {
                            item { Text("請先選取照片。") }
                        } else if (detail == null) {
                            item { Text("讀取定位資料中…") }
                        } else {
                            item { Text(first.name, fontWeight = FontWeight.Medium) }
                            item {
                                val d = detail!!
                                Text(
                                    if (d.hasGps && d.lat != null && d.lon != null)
                                        "${d.lat}, ${d.lon}"
                                    else "這張照片沒有 GPS 定位資料"
                                )
                            }
                            item {
                                Text(
                                    "這一版先提供定位檢查；新增、修改與移除 GPS 會在寫入流程完成後開放。",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OrganizerDialogKind.FILENAME -> {
                        if (selectedItems.isEmpty()) {
                            item { Text("請先選取照片。") }
                        } else {
                            item { Text("已選 ${selectedItems.size} 項", fontWeight = FontWeight.Medium) }
                            items(selectedItems.take(30), key = { it.key }) { item ->
                                Text(item.name, fontSize = 12.sp)
                            }
                            item {
                                Text(
                                    "批次重新命名會先做完整預覽，確認後才修改 MediaStore；這一版先把入口與檔名檢查打開。",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OrganizerDialogKind.DETAIL -> {
                        if (first == null) {
                            item { Text("請先選取一張照片。") }
                        } else if (detail == null) {
                            item { Text("讀取照片資料中…") }
                        } else {
                            item {
                                Column {
                                    Text(first.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${first.width} × ${first.height}　${formatBytes(first.size)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(detail!!.rows, key = { it.label + ":" + it.rawTag }) { row ->
                                Column {
                                    Text(
                                        row.label,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(row.value, fontSize = 13.sp)
                                    Text(
                                        row.rawTag,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    OrganizerDialogKind.HISTORY -> {
                        val history = repository.organizerHistory()
                        if (history.isEmpty()) {
                            item {
                                Text(
                                    "還沒有整理紀錄。加入或移除關鍵字後，紀錄會出現在這裡。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(history) { line ->
                                Text(line, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
