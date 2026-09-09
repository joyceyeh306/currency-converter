from pathlib import Path

path = Path("video-compressor/app/src/main/java/com/joyce/videocompressor/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Imports for the delete-original option and Android's system delete confirmation.
if "import android.app.RecoverableSecurityException\n" not in s:
    s = s.replace("import android.Manifest\n", "import android.Manifest\nimport android.app.RecoverableSecurityException\n", 1)
if "import android.widget.CheckBox\n" not in s:
    s = s.replace("import android.widget.Button\n", "import android.widget.Button\nimport android.widget.CheckBox\n", 1)
if "import androidx.activity.result.IntentSenderRequest\n" not in s:
    s = s.replace(
        "import androidx.activity.result.contract.ActivityResultContracts\n",
        "import androidx.activity.result.IntentSenderRequest\nimport androidx.activity.result.contract.ActivityResultContracts\n",
        1,
    )

# View fields.
field_marker = "    private lateinit var progressBar: ProgressBar\n"
if "private lateinit var deleteOriginalCheck" not in s:
    if field_marker not in s:
        raise SystemExit("Could not find progressBar field")
    s = s.replace(
        field_marker,
        field_marker
        + "    private lateinit var deleteOriginalCheck: CheckBox\n"
        + "    private lateinit var originalPolicyText: TextView\n",
        1,
    )

# System confirmation result. The original is only considered deleted after Android
# returns RESULT_OK from its own confirmation UI.
launcher_marker = "    private val locationPermissionLauncher = registerForActivityResult(\n"
if "private val deleteRequestLauncher" not in s:
    if launcher_marker not in s:
        raise SystemExit("Could not find location permission launcher")
    launcher = '''    private val deleteRequestLauncher = registerForActivityResult(\n        ActivityResultContracts.StartIntentSenderForResult()\n    ) { result ->\n        if (result.resultCode == android.app.Activity.RESULT_OK) {\n            statusText.append("\\n原始影片已刪除")\n            Toast.makeText(this, "原始影片已刪除", Toast.LENGTH_LONG).show()\n        } else {\n            statusText.append("\\n已取消刪除，原始影片保留")\n        }\n    }\n\n'''
    s = s.replace(launcher_marker, launcher + launcher_marker, 1)

# Bind the new controls and keep the warning text in sync with the checkbox.
bind_marker = "        progressBar = findViewById(R.id.progressBar)\n"
if "deleteOriginalCheck = findViewById" not in s:
    if bind_marker not in s:
        raise SystemExit("Could not find progressBar binding")
    s = s.replace(
        bind_marker,
        bind_marker
        + "        deleteOriginalCheck = findViewById(R.id.deleteOriginalCheck)\n"
        + "        originalPolicyText = findViewById(R.id.originalPolicyText)\n",
        1,
    )

listener_marker = "        selectButton.setOnClickListener { picker.launch(arrayOf(\"video/*\")) }\n"
if "deleteOriginalCheck.setOnCheckedChangeListener" not in s:
    # v14 has already changed GetContent to OpenDocument by the time this patch runs.
    if listener_marker not in s:
        raise SystemExit("Could not find patched picker listener")
    listener = '''        deleteOriginalCheck.setOnCheckedChangeListener { _, checked ->\n            originalPolicyText.text = if (checked) {\n                "處理成功後將要求刪除原始影片"\n            } else {\n                "原始影片保留"\n            }\n        }\n'''
    s = s.replace(listener_marker, listener_marker + listener, 1)

# Only request deletion after a new output file has definitely been saved.
video_success = '''                        Toast.makeText(this@MainActivity, "影片處理完成", Toast.LENGTH_LONG).show()\n                        if (savedUri != null) fileToSave.delete()\n'''
video_success_new = '''                        Toast.makeText(this@MainActivity, "影片處理完成", Toast.LENGTH_LONG).show()\n                        if (savedUri != null) {\n                            fileToSave.delete()\n                            if (deleteOriginalCheck.isChecked) requestOriginalDeletion(uri)\n                        }\n'''
if "if (deleteOriginalCheck.isChecked) requestOriginalDeletion(uri)" not in s:
    if video_success not in s:
        raise SystemExit("Could not find video success block")
    s = s.replace(video_success, video_success_new, 1)

mp3_success = '''                    Toast.makeText(this, "MP3 匯出完成", Toast.LENGTH_LONG).show()\n                    finishUi()\n'''
mp3_success_new = '''                    Toast.makeText(this, "MP3 匯出完成", Toast.LENGTH_LONG).show()\n                    if (savedUri != null && deleteOriginalCheck.isChecked) requestOriginalDeletion(uri)\n                    finishUi()\n'''
if "savedUri != null && deleteOriginalCheck.isChecked" not in s:
    if mp3_success not in s:
        raise SystemExit("Could not find MP3 success block")
    s = s.replace(mp3_success, mp3_success_new, 1)

# Prevent the checkbox from being changed while a conversion is running.
busy_marker = "        qualityGroup.isEnabled = !busy\n"
if "deleteOriginalCheck.isEnabled = !busy" not in s:
    if busy_marker not in s:
        raise SystemExit("Could not find busy-state marker")
    s = s.replace(busy_marker, busy_marker + "        deleteOriginalCheck.isEnabled = !busy\n", 1)

# Android 11+ always uses the OS confirmation dialog for deleting media the app
# does not own. Android 10 uses RecoverableSecurityException when needed. Older
# devices attempt deletion only through the URI grant they already received.
function_marker = "    private fun decodeAudioToMp3(uri: Uri, outputFile: File) {\n"
if "private fun requestOriginalDeletion" not in s:
    if function_marker not in s:
        raise SystemExit("Could not find decodeAudioToMp3 marker")
    functions = '''    private fun requestOriginalDeletion(sourceUri: Uri) {\n        val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n            try { MediaStore.getMediaUri(this, sourceUri) ?: sourceUri } catch (_: Exception) { sourceUri }\n        } else {\n            sourceUri\n        }\n\n        statusText.append("\\n等待確認刪除原始影片…")\n        try {\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n                val request = MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))\n                deleteRequestLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())\n                return\n            }\n\n            try {\n                val deleted = contentResolver.delete(mediaUri, null, null)\n                if (deleted > 0) {\n                    statusText.append("\\n原始影片已刪除")\n                    Toast.makeText(this, "原始影片已刪除", Toast.LENGTH_LONG).show()\n                } else {\n                    statusText.append("\\n無法刪除原始影片，原檔已保留")\n                }\n            } catch (e: RecoverableSecurityException) {\n                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n                    deleteRequestLauncher.launch(\n                        IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()\n                    )\n                } else {\n                    statusText.append("\\n無法刪除原始影片，原檔已保留")\n                }\n            }\n        } catch (e: Exception) {\n            // A non-MediaStore provider may not support Android's media delete request.\n            // Never treat this as a conversion failure and never touch the saved output.\n            statusText.append("\\n無法刪除原始影片，原檔已保留")\n        }\n    }\n\n'''
    s = s.replace(function_marker, functions + function_marker, 1)

path.write_text(s, encoding="utf-8")
