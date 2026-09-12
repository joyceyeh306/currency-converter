from pathlib import Path

path = Path("video-compressor/app/src/main/java/com/joyce/videocompressor/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Android 15+ edge-to-edge: keep content clear of status/navigation bars.
if "import androidx.core.view.ViewCompat\n" not in s:
    s = s.replace(
        "import androidx.core.content.ContextCompat\n",
        "import androidx.core.content.ContextCompat\nimport androidx.core.view.ViewCompat\nimport androidx.core.view.WindowInsetsCompat\n",
        1,
    )

# Reset all source-specific state as soon as a different video is selected.
picker_marker = '''        if (uri != null) {
            selectedUri = uri
            compressButton.isEnabled = false
'''
picker_new = '''        if (uri != null) {
            prepareForNewSelection(uri)
            compressButton.isEnabled = false
'''
if "prepareForNewSelection(uri)" not in s:
    if picker_marker not in s:
        raise SystemExit("Could not find picker selection block")
    s = s.replace(picker_marker, picker_new, 1)

# Apply real system-bar insets to the ScrollView instead of using fixed top spacing.
content_marker = '''        setContentView(R.layout.activity_main)

        selectButton = findViewById(R.id.selectButton)
'''
content_new = '''        setContentView(R.layout.activity_main)

        val rootScroll = findViewById<View>(R.id.rootScroll)
        ViewCompat.setOnApplyWindowInsetsListener(rootScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(rootScroll)

        selectButton = findViewById(R.id.selectButton)
'''
if "val rootScroll = findViewById<View>(R.id.rootScroll)" not in s:
    if content_marker not in s:
        raise SystemExit("Could not find setContentView block")
    s = s.replace(content_marker, content_new, 1)

# Extra safety: never let a failed metadata read reuse the previous video's duration.
load_marker = '''    private fun loadVideoInfo(uri: Uri) {
        sourceMeta = readSourceMeta(uri)
'''
load_new = '''    private fun loadVideoInfo(uri: Uri) {
        sourceDurationMs = 0L
        sourceSizeBytes = 0L
        sourceMeta = readSourceMeta(uri)
'''
if "private fun loadVideoInfo(uri: Uri) {\n        sourceDurationMs = 0L" not in s:
    if load_marker not in s:
        raise SystemExit("Could not find loadVideoInfo start")
    s = s.replace(load_marker, load_new, 1)

# Clear source cards/state after a successful output save, but keep the user's
# chosen quality and delete-original checkbox for convenient batch processing.
function_marker = "    private fun isMp3Mode(): Boolean = qualityGroup.checkedRadioButtonId == R.id.qMp3\n"
if "private fun prepareForNewSelection" not in s:
    helpers = '''    private fun prepareForNewSelection(uri: Uri) {
        selectedUri = uri
        sourceDisplayName = "影片"
        sourceSizeBytes = 0L
        sourceDurationMs = 0L
        sourceMeta = SourceMeta()
        tempOutput = null
        fileInfo.text = "正在讀取影片資訊…"
        estimateText.text = "正在讀取影片資訊…"
        statusText.text = ""
    }

    private fun clearSourceSelection() {
        selectedUri = null
        sourceDisplayName = "影片"
        sourceSizeBytes = 0L
        sourceDurationMs = 0L
        sourceMeta = SourceMeta()
        tempOutput = null
        fileInfo.text = "尚未選擇影片"
        estimateText.text = "選擇影片後會顯示預估大小與輸出檔名"
        compressButton.isEnabled = false
    }

'''
    if function_marker not in s:
        raise SystemExit("Could not find isMp3Mode marker")
    s = s.replace(function_marker, helpers + function_marker, 1)

# v1.6 success blocks request deletion after a confirmed output save.
video_old = '''                        if (savedUri != null) {
                            fileToSave.delete()
                            if (deleteOriginalCheck.isChecked) requestOriginalDeletion(uri)
                        }
'''
video_new = '''                        if (savedUri != null) {
                            fileToSave.delete()
                            if (deleteOriginalCheck.isChecked) requestOriginalDeletion(uri)
                            clearSourceSelection()
                        }
'''
if "clearSourceSelection()\n                        }" not in s:
    if video_old not in s:
        raise SystemExit("Could not find video success block")
    s = s.replace(video_old, video_new, 1)

mp3_old = '''                    if (savedUri != null && deleteOriginalCheck.isChecked) requestOriginalDeletion(uri)
                    finishUi()
'''
mp3_new = '''                    if (savedUri != null) {
                        if (deleteOriginalCheck.isChecked) requestOriginalDeletion(uri)
                        clearSourceSelection()
                    }
                    finishUi()
'''
if "if (savedUri != null) {\n                        if (deleteOriginalCheck.isChecked) requestOriginalDeletion(uri)" not in s:
    if mp3_old not in s:
        raise SystemExit("Could not find MP3 success block")
    s = s.replace(mp3_old, mp3_new, 1)

path.write_text(s, encoding="utf-8")
