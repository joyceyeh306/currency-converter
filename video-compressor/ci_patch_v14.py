from pathlib import Path

path = Path("video-compressor/app/src/main/java/com/joyce/videocompressor/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# ACTION_OPEN_DOCUMENT gives a DocumentsProvider URI that Android can map back
# to the corresponding MediaStore item while preserving the user's narrow grant.
s = s.replace("ActivityResultContracts.GetContent()", "ActivityResultContracts.OpenDocument()")
s = s.replace('picker.launch("video/*")', 'picker.launch(arrayOf("video/*"))')

marker = "        return SourceMeta(name, size, relativePath, dateTaken, dateModified)\n"
resolver = '''        // OPPO / system file pickers may return a DocumentsProvider URI whose\n        // direct query does not expose RELATIVE_PATH. Convert it back to the\n        // equivalent MediaStore URI and query the real media row.\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relativePath.isNullOrBlank()) {\n            try {\n                val mediaUri = MediaStore.getMediaUri(this, uri)\n                if (mediaUri != null) {\n                    val projection = arrayOf(\n                        MediaStore.MediaColumns.RELATIVE_PATH,\n                        MediaStore.MediaColumns.DATE_TAKEN,\n                        MediaStore.MediaColumns.DATE_MODIFIED\n                    )\n                    contentResolver.query(mediaUri, projection, null, null, null)?.use { c ->\n                        if (c.moveToFirst()) {\n                            val p = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)\n                            val t = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)\n                            val m = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)\n                            if (p >= 0 && !c.isNull(p)) relativePath = c.getString(p)\n                            if (dateTaken <= 0 && t >= 0 && !c.isNull(t)) dateTaken = c.getLong(t)\n                            if (dateModified <= 0 && m >= 0 && !c.isNull(m)) dateModified = c.getLong(m)\n                        }\n                    }\n                }\n            } catch (_: Exception) {}\n        }\n\n        return SourceMeta(name, size, relativePath, dateTaken, dateModified)\n'''

if "MediaStore.getMediaUri(this, uri)" not in s:
    if marker not in s:
        raise SystemExit("Could not find SourceMeta return marker")
    s = s.replace(marker, resolver, 1)

path.write_text(s, encoding="utf-8")
