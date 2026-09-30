@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package tw.ajo.photomanager

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.format.DateTimeFormatter

@Composable
fun PhotoOrganizerScreen(
    selectedItems: List<MediaItem>,
    repository: AlbumRepository,
    onBack: () -> Unit,
    onOpenAlbumOrganize: (Set<String>) -> Unit,
    onSearchKeyword: (String) -> Unit,
    onKeywordsChanged: () -> Unit,
    onKeywordEditingFinished: () -> Unit,
    onFilesRenamed: () -> Unit,
    onPhotoTimesChanged: () -> Unit
) {
    BackHandler { onBack() }

    val haptic = LocalHapticFeedback.current
    val selectedKeys = remember(selectedItems) { selectedItems.map { it.key }.toSet() }
    var localVersion by remember { mutableIntStateOf(0) }
    var keywordDialog by remember { mutableStateOf(false) }
    var filenameDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
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
                        subtitle = "查看・批次修改時間",
                        enabled = selectedItems.isNotEmpty(),
                        onClick = { timeDialog = true }
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
                        subtitle = "單張・批次重新命名",
                        enabled = selectedItems.isNotEmpty(),
                        onClick = { filenameDialog = true }
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
            },
            onFinished = { changed ->
                keywordDialog = false
                if (changed) onKeywordEditingFinished()
            }
        )
    }

    if (filenameDialog) {
        FilenameRenameDialog(
            selectedItems = selectedItems,
            repository = repository,
            onDismiss = { filenameDialog = false },
            onRenamed = onFilesRenamed
        )
    }

    if (timeDialog) {
        PhotoTimeEditDialog(
            selectedItems = selectedItems,
            repository = repository,
            onDismiss = { timeDialog = false },
            onChanged = onPhotoTimesChanged
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
    onChanged: () -> Unit,
    onFinished: (Boolean) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var changed by remember { mutableStateOf(false) }
    val selectedCounts = remember(selectedKeys, version) { repository.allKeywordCounts(selectedKeys) }
    val suggestions = remember(version) { repository.allKeywordCounts() }
    val selectedMap = selectedCounts.associate { it.first.lowercase() to it.second }

    AlertDialog(
        onDismissRequest = { onFinished(changed) },
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
                                    changed = true
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
                                        changed = true
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
                                            changed = true
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
            TextButton(onClick = { onFinished(changed) }) { Text("完成") }
        }
    )
}



private enum class PhotoTimeBatchMode(
    val label: String,
    val description: String
) {
    SHIFT_ALL(
        "整批平移・保留時間間隔",
        "指定第一張的正確時間，其餘照片跟著平移相同差值"
    ),
    SAME_TIME(
        "全部設成同一時間",
        "所有可修改的照片都使用同一個拍攝時間"
    ),
    GPS_TIME(
        "由 GPS 時間讀入",
        "直接讀取照片 EXIF 裡的 GPS 日期與時間，不做時區換算"
    )
}

@Composable
private fun PhotoTimeEditDialog(
    selectedItems: List<MediaItem>,
    repository: AlbumRepository,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
    val orderedItems = remember(selectedItems) {
        selectedItems.sortedWith(
            compareBy<MediaItem> { it.wallTime }.thenBy { it.name.lowercase() }
        )
    }
    val first = orderedItems.firstOrNull()
    var mode by remember(orderedItems.size) {
        mutableStateOf(
            if (orderedItems.size > 1) PhotoTimeBatchMode.SHIFT_ALL
            else PhotoTimeBatchMode.SAME_TIME
        )
    }
    var targetText by remember(first?.key, first?.wallTime) {
        mutableStateOf(first?.wallTime?.format(formatter).orEmpty())
    }
    var processing by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var pendingApply by remember { mutableStateOf<List<PhotoTimePreview>>(emptyList()) }
    var result by remember { mutableStateOf<PhotoTimeResult?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var gpsPreviews by remember { mutableStateOf<List<PhotoTimePreview>>(emptyList()) }
    var gpsLoading by remember { mutableStateOf(false) }

    val parsedTarget = remember(targetText) {
        try {
            java.time.LocalDateTime.parse(targetText.trim(), formatter)
        } catch (_: Exception) {
            null
        }
    }

    val requests = remember(orderedItems, parsedTarget, mode) {
        if (
            mode == PhotoTimeBatchMode.GPS_TIME ||
            first == null ||
            parsedTarget == null
        ) {
            emptyList()
        } else {
            when (mode) {
                PhotoTimeBatchMode.SHIFT_ALL -> {
                    val delta = Duration.between(first.wallTime, parsedTarget)
                    orderedItems.map { item ->
                        item to item.wallTime.plus(delta)
                    }
                }
                PhotoTimeBatchMode.SAME_TIME -> {
                    orderedItems.map { item -> item to parsedTarget }
                }
                PhotoTimeBatchMode.GPS_TIME -> emptyList()
            }
        }
    }

    val standardPreviews = remember(requests) {
        repository.previewPhotoTimes(requests)
    }

    LaunchedEffect(mode, orderedItems) {
        if (mode == PhotoTimeBatchMode.GPS_TIME) {
            gpsLoading = true
            gpsPreviews = withContext(Dispatchers.IO) {
                repository.previewGpsPhotoTimes(orderedItems)
            }
            gpsLoading = false
        } else {
            gpsPreviews = emptyList()
            gpsLoading = false
        }
    }

    val previews =
        if (mode == PhotoTimeBatchMode.GPS_TIME) gpsPreviews else standardPreviews
    val skippedCount = previews.count { it.error != null }
    val changedCount = previews.count { it.error == null && it.changed }
    val unchangedCount = previews.count { it.error == null && !it.changed }
    val inputReady =
        if (mode == PhotoTimeBatchMode.GPS_TIME) !gpsLoading
        else parsedTarget != null
    val canApply =
        inputReady &&
        changedCount > 0 &&
        !processing &&
        result == null
    val availableTimeModes = remember(orderedItems.size) {
        if (orderedItems.size > 1) {
            PhotoTimeBatchMode.entries
        } else {
            listOf(
                PhotoTimeBatchMode.SAME_TIME,
                PhotoTimeBatchMode.GPS_TIME
            )
        }
    }

    fun applyChanges(list: List<PhotoTimePreview>) {
        processing = true
        notice = null
        scope.launch {
            val applied = withContext(Dispatchers.IO) {
                repository.applyPhotoTimes(list)
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
            notice = "已取消，拍攝時間沒有修改。"
        }
    }

    fun requestApply() {
        val changed = previews.filter { it.error == null && it.changed }
        if (changed.isEmpty()) return
        val request = repository.createPhotoTimeWriteRequest(changed.map { it.item })
        if (request == null) {
            applyChanges(changed)
        } else {
            pendingApply = changed
            writeLauncher.launch(
                IntentSenderRequest.Builder(request.intentSender).build()
            )
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!processing) onDismiss()
        },
        title = {
            Column {
                Text(
                    if (orderedItems.size == 1) "修改拍攝時間"
                    else "批次修改拍攝時間"
                )
                Text(
                    "已選 ${orderedItems.size} 項・修改前先預覽",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 590.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                result?.let { timeResult ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                Modifier.padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("拍攝時間修改完成", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "成功 ${timeResult.succeeded}　失敗 ${timeResult.failed}",
                                    fontSize = 12.sp
                                )
                                timeResult.details.take(20).forEach { line ->
                                    Text(
                                        line,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (timeResult.details.size > 20) {
                                    Text(
                                        "其餘 ${timeResult.details.size - 20} 筆未顯示",
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
                        Text(
                            "修改方式",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(availableTimeModes, key = { it.name }) { option ->
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
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = mode == option,
                                    onClick = { mode = option }
                                )
                                Column(Modifier.padding(start = 3.dp)) {
                                    Text(
                                        option.label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        option.description,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (mode != PhotoTimeBatchMode.GPS_TIME) {
                        item {
                            OutlinedTextField(
                                value = targetText,
                                onValueChange = { targetText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = {
                                    Text(
                                        if (orderedItems.size == 1 ||
                                            mode == PhotoTimeBatchMode.SAME_TIME
                                        ) "新的拍攝時間"
                                        else "第一張的新拍攝時間"
                                    )
                                }
                            )
                        }

                        item {
                            Text(
                                "格式：yyyy-MM-dd HH:mm:ss",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        item {
                            Text(
                                "GPS 日期與時間會直接依照片內的 EXIF 原值讀入（UTC），不做所在地時區換算。",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!inputReady) {
                        item {
                            Text(
                                if (mode == PhotoTimeBatchMode.GPS_TIME)
                                    "正在讀取 GPS 時間…"
                                else
                                    "日期時間格式不正確",
                                fontSize = 11.sp,
                                color = if (mode == PhotoTimeBatchMode.GPS_TIME)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        item {
                            Surface(
                                shape = RoundedCornerShape(15.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        "可修改 $changedCount 項",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (skippedCount > 0) {
                                        Text(
                                            if (mode == PhotoTimeBatchMode.GPS_TIME)
                                                "略過 $skippedCount 項：沒有 GPS 日期時間或不是照片"
                                            else
                                                "略過 $skippedCount 項：暫不支援的圖片／影片格式",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (unchangedCount > 0) {
                                        Text(
                                            "$unchangedCount 項時間原本就相同",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "預覽",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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
                                        Text(
                                            preview.currentTime.format(formatter) +
                                                " → " + preview.newTime.format(formatter),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (!preview.changed) {
                                            Text(
                                                "時間相同，不會修改",
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
                                    "先顯示前 60 項預覽；執行時會處理全部 ${previews.size} 項。",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            Text(
                                if (mode == PhotoTimeBatchMode.GPS_TIME)
                                    "只讀取照片既有 GPS 日期與 GPS 時間，直接寫入 EXIF 拍攝時間；不做時區換算，也不加入任何時區資料庫。"
                                else
                                    "照片會寫入 EXIF 拍攝時間；MP4／MOV 影片會修改容器建立時間並同步 Android 拍攝時間索引。影音內容不重新編碼。",
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
                    enabled = canApply,
                    onClick = { confirmOpen = true }
                ) {
                    Text(
                        if (processing) "處理中…"
                        else "修改 $changedCount 項"
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
            title = { Text("確認修改拍攝時間") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("將修改 $changedCount 項的拍攝時間。")
                    if (mode == PhotoTimeBatchMode.SHIFT_ALL && first != null && parsedTarget != null) {
                        val delta = Duration.between(first.wallTime, parsedTarget)
                        Text(
                            "整批差值：" + formatDurationDelta(delta),
                            fontSize = 12.sp
                        )
                    }
                    if (mode == PhotoTimeBatchMode.GPS_TIME) {
                        Text(
                            "GPS 時間會使用照片內的 UTC 原值，不做時區換算。",
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        "只修改照片 metadata，不重新編碼、壓縮或改變影像畫質。",
                        fontSize = 12.sp
                    )
                }
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
                TextButton(onClick = { confirmOpen = false }) { Text("取消") }
            }
        )
    }
}

private fun formatDurationDelta(delta: Duration): String {
    val seconds = delta.seconds
    val sign = if (seconds >= 0) "+" else "-"
    var remaining = kotlin.math.abs(seconds)
    val days = remaining / 86400
    remaining %= 86400
    val hours = remaining / 3600
    remaining %= 3600
    val minutes = remaining / 60
    val secs = remaining % 60

    val parts = mutableListOf<String>()
    if (days > 0) parts.add("${days}天")
    if (hours > 0) parts.add("${hours}小時")
    if (minutes > 0) parts.add("${minutes}分")
    if (secs > 0 || parts.isEmpty()) parts.add("${secs}秒")
    return sign + parts.joinToString(" ")
}

private enum class FilenameBatchMode(
    val label: String,
    val description: String
) {
    PREFIX_SEQUENCE(
        "前綴＋流水號",
        "依拍攝時間排序，使用自訂前綴與三位數流水號"
    ),
    CAPTURE_TIME(
        "依拍攝日期時間",
        "例如 2026-09-28 11_20_55.JPG；同一秒多張會自動加序號"
    ),
    KEEP_ORIGINAL(
        "原檔名＋前後綴",
        "保留原本主檔名，只在前方或後方加文字"
    )
}

@Composable
private fun FilenameRenameDialog(
    selectedItems: List<MediaItem>,
    repository: AlbumRepository,
    onDismiss: () -> Unit,
    onRenamed: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val orderedItems = remember(selectedItems) {
        selectedItems.sortedWith(
            compareBy<MediaItem> { it.wallTime }.thenBy { it.name.lowercase() }
        )
    }
    val singleItem = orderedItems.singleOrNull()
    var singleBase by remember(singleItem?.key, singleItem?.name) {
        mutableStateOf(singleItem?.let { filenameBase(it.name) }.orEmpty())
    }
    var mode by remember { mutableStateOf(FilenameBatchMode.PREFIX_SEQUENCE) }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var startNumber by remember { mutableStateOf("1") }
    var previews by remember { mutableStateOf<List<FilenameRenamePreview>>(emptyList()) }
    var previewLoading by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var pendingApply by remember { mutableStateOf<List<FilenameRenamePreview>>(emptyList()) }
    var result by remember { mutableStateOf<FilenameRenameResult?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val inputError = when {
        orderedItems.size > 2000 -> "一次最多重新命名 2000 項"
        singleItem != null && singleBase.isBlank() -> "主檔名不能空白"
        singleItem == null && mode == FilenameBatchMode.PREFIX_SEQUENCE &&
            prefix.isBlank() -> "請輸入前綴"
        singleItem == null && mode == FilenameBatchMode.PREFIX_SEQUENCE &&
            (startNumber.toIntOrNull() == null || (startNumber.toIntOrNull() ?: -1) < 0) ->
            "起始編號必須是 0 以上的整數"
        singleItem == null && mode == FilenameBatchMode.KEEP_ORIGINAL &&
            prefix.isBlank() && suffix.isBlank() -> "請輸入前綴或後綴"
        else -> null
    }

    val rawRequests = remember(
        orderedItems,
        singleBase,
        mode,
        prefix,
        suffix,
        startNumber,
        inputError
    ) {
        if (inputError != null) {
            emptyList()
        } else if (singleItem != null) {
            listOf(singleItem to (singleBase + filenameExtensionWithDot(singleItem.name)))
        } else {
            when (mode) {
                FilenameBatchMode.PREFIX_SEQUENCE -> {
                    val start = startNumber.toIntOrNull() ?: 1
                    orderedItems.mapIndexed { index, item ->
                        val base = prefix + (start + index).toString().padStart(3, '0')
                        item to (base + filenameExtensionWithDot(item.name))
                    }
                }
                FilenameBatchMode.CAPTURE_TIME -> {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH_mm_ss")
                    val occurrences = mutableMapOf<String, Int>()
                    orderedItems.map { item ->
                        val stem = item.wallTime.format(formatter)
                        val ext = filenameExtensionWithDot(item.name)
                        val key = stem.lowercase() + "|" + ext.lowercase()
                        val occurrence = occurrences[key] ?: 0
                        occurrences[key] = occurrence + 1
                        val base = if (occurrence == 0) {
                            stem
                        } else {
                            stem + "_" + occurrence.toString().padStart(2, '0')
                        }
                        item to (base + ext)
                    }
                }
                FilenameBatchMode.KEEP_ORIGINAL -> {
                    orderedItems.map { item ->
                        val base = prefix + filenameBase(item.name) + suffix
                        item to (base + filenameExtensionWithDot(item.name))
                    }
                }
            }
        }
    }

    LaunchedEffect(rawRequests) {
        if (result != null) {
            previewLoading = false
            return@LaunchedEffect
        }
        notice = null
        if (rawRequests.isEmpty()) {
            previews = emptyList()
            previewLoading = false
        } else {
            previewLoading = true
            delay(180)
            previews = withContext(Dispatchers.IO) {
                repository.previewFilenameRenames(rawRequests)
            }
            previewLoading = false
        }
    }

    fun applyRenames(list: List<FilenameRenamePreview>) {
        processing = true
        notice = null
        scope.launch {
            val applied = withContext(Dispatchers.IO) {
                repository.applyFilenameRenames(list)
            }
            result = applied
            processing = false
            pendingApply = emptyList()
            if (applied.succeeded > 0) {
                onRenamed()
            }
        }
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val list = pendingApply
        if (activityResult.resultCode == Activity.RESULT_OK && list.isNotEmpty()) {
            applyRenames(list)
        } else {
            pendingApply = emptyList()
            notice = "已取消，檔名沒有修改。"
        }
    }

    fun requestApply() {
        val changed = previews.filter { it.error == null && it.changed }
        if (changed.isEmpty()) return
        val request = repository.createFilenameWriteRequest(changed.map { it.item })
        if (request == null) {
            applyRenames(changed)
        } else {
            pendingApply = changed
            writeLauncher.launch(
                IntentSenderRequest.Builder(request.intentSender).build()
            )
        }
    }

    val errorCount = previews.count { it.error != null }
    val changedCount = previews.count { it.error == null && it.changed }
    val canRename =
        inputError == null &&
        !previewLoading &&
        !processing &&
        previews.isNotEmpty() &&
        errorCount == 0 &&
        changedCount > 0 &&
        result == null

    AlertDialog(
        onDismissRequest = {
            if (!processing) onDismiss()
        },
        title = {
            Column {
                Text(if (singleItem != null) "修改檔名" else "批次修改檔名")
                Text(
                    if (singleItem != null) "副檔名固定保留"
                    else "已選 ${orderedItems.size} 項・修改前先預覽",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 590.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                result?.let { renameResult ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                Modifier.padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("重新命名完成", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "成功 ${renameResult.succeeded}　失敗 ${renameResult.failed}",
                                    fontSize = 12.sp
                                )
                                renameResult.details.take(20).forEach { line ->
                                    Text(
                                        line,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (renameResult.details.size > 20) {
                                    Text(
                                        "其餘 ${renameResult.details.size - 20} 筆未顯示",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (result == null) {
                    if (singleItem != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    singleItem.name,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = singleBase,
                                    onValueChange = { singleBase = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("主檔名") }
                                )
                                val ext = filenameExtensionWithDot(singleItem.name)
                                if (ext.isNotEmpty()) {
                                    Text(
                                        "副檔名固定保留：$ext",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "命名方式",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        items(FilenameBatchMode.entries, key = { it.name }) { option ->
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
                                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = mode == option,
                                        onClick = { mode = option }
                                    )
                                    Column(Modifier.padding(start = 3.dp)) {
                                        Text(
                                            option.label,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            option.description,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        when (mode) {
                            FilenameBatchMode.PREFIX_SEQUENCE -> {
                                item {
                                    OutlinedTextField(
                                        value = prefix,
                                        onValueChange = { prefix = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("前綴") }
                                    )
                                }
                                item {
                                    OutlinedTextField(
                                        value = startNumber,
                                        onValueChange = { value ->
                                            if (value.all { it.isDigit() }) startNumber = value
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("起始編號") }
                                    )
                                }
                            }
                            FilenameBatchMode.CAPTURE_TIME -> Unit
                            FilenameBatchMode.KEEP_ORIGINAL -> {
                                item {
                                    OutlinedTextField(
                                        value = prefix,
                                        onValueChange = { prefix = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("前綴") }
                                    )
                                }
                                item {
                                    OutlinedTextField(
                                        value = suffix,
                                        onValueChange = { suffix = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("後綴") }
                                    )
                                }
                            }
                        }
                    }

                    inputError?.let { message ->
                        item {
                            Text(
                                message,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
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

                    if (inputError == null) {
                        item {
                            Text(
                                when {
                                    previewLoading -> "正在檢查檔名…"
                                    errorCount > 0 -> "預覽・有 $errorCount 項需要修正"
                                    changedCount == 0 -> "預覽・檔名沒有變更"
                                    else -> "預覽・將修改 $changedCount 項"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        items(previews.take(80), key = { it.item.key }) { preview ->
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
                                    Text(
                                        "→ " + preview.newName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    preview.error?.let { error ->
                                        Text(
                                            error,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (!preview.changed && preview.error == null) {
                                        Text(
                                            "檔名相同，不會修改",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (previews.size > 80) {
                            item {
                                Text(
                                    "先顯示前 80 項預覽；執行時會處理全部 ${previews.size} 項。",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            Text(
                                "副檔名一律保留。系統會檢查重名、非法字元與過長檔名。重新命名只修改檔案名稱，不重新編碼或重存影像像素。",
                                fontSize = 10.sp,
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
                    enabled = canRename,
                    onClick = { confirmOpen = true }
                ) {
                    Text(
                        if (processing) "處理中…"
                        else "重新命名 $changedCount 項"
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
            title = { Text("確認重新命名") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("將修改 $changedCount 項檔名。")
                    Text(
                        "只會重新命名檔案，不會重新編碼、壓縮或改變照片畫質。",
                        fontSize = 12.sp
                    )
                    Text(
                        "副檔名保持原樣。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                TextButton(onClick = { confirmOpen = false }) { Text("取消") }
            }
        )
    }
}

private fun filenameBase(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0 && dot < name.length) name.substring(0, dot) else name
}

private fun filenameExtensionWithDot(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0 && dot < name.lastIndex) name.substring(dot) else ""
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
