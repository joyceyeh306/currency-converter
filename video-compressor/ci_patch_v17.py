from pathlib import Path

path = Path("video-compressor/app/src/main/java/com/joyce/videocompressor/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Some Android/OPPO document-provider URIs can be opened for transcoding but
# MediaMetadataRetriever still fails to return duration.  That left the estimate
# card stuck at its placeholder because updateEstimate() returned early.
# Add a MediaExtractor fallback that reads track duration through a file descriptor.

fallback_call_marker = '''        compressButton.isEnabled = true\n        updateEstimate()\n'''
fallback_call_new = '''        if (sourceDurationMs <= 0L) {\n            sourceDurationMs = readDurationWithExtractor(uri)\n        }\n        compressButton.isEnabled = true\n        updateEstimate()\n'''
if "sourceDurationMs = readDurationWithExtractor(uri)" not in s:
    if fallback_call_marker not in s:
        raise SystemExit("Could not find loadVideoInfo completion block")
    s = s.replace(fallback_call_marker, fallback_call_new, 1)

function_marker = "    private fun metadataUri(uri: Uri): Uri {\n"
if "private fun readDurationWithExtractor" not in s:
    if function_marker not in s:
        raise SystemExit("Could not find metadataUri marker")
    function = '''    private fun readDurationWithExtractor(uri: Uri): Long {\n        val extractor = MediaExtractor()\n        return try {\n            val opened = contentResolver.openFileDescriptor(uri, "r") ?: return 0L\n            opened.use { pfd -> extractor.setDataSource(pfd.fileDescriptor) }\n            var longestUs = 0L\n            for (i in 0 until extractor.trackCount) {\n                val format = extractor.getTrackFormat(i)\n                if (format.containsKey(MediaFormat.KEY_DURATION)) {\n                    longestUs = maxOf(longestUs, format.getLong(MediaFormat.KEY_DURATION))\n                }\n            }\n            longestUs / 1000L\n        } catch (_: Exception) {\n            0L\n        } finally {\n            try { extractor.release() } catch (_: Exception) {}\n        }\n    }\n\n'''
    s = s.replace(function_marker, function + function_marker, 1)

# Never leave a stale placeholder when a duration truly cannot be obtained.
old_guard = '''        if (selectedUri == null || sourceDurationMs <= 0) return\n'''
new_guard = '''        if (selectedUri == null) return\n        if (sourceDurationMs <= 0) {\n            estimateText.text = "無法讀取影片長度，暫時無法估算輸出大小"\n            return\n        }\n'''
if "暫時無法估算輸出大小" not in s:
    if old_guard not in s:
        raise SystemExit("Could not find updateEstimate guard")
    s = s.replace(old_guard, new_guard, 1)

path.write_text(s, encoding="utf-8")
