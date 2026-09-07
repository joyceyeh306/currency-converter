from pathlib import Path

path = Path("video-compressor/app/src/main/java/com/joyce/videocompressor/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Use Media3's in-app MP4 muxer so source MP4 metadata (XMP/mdta etc.) can
# survive transcoding. The old v1.4 post-processing remux only copied tracks
# plus GPS and therefore stripped camera / lens metadata.
if "import androidx.media3.container.Mp4LocationData" not in s:
    s = s.replace(
        "import androidx.media3.effect.Presentation\n",
        "import androidx.media3.container.Mp4LocationData\nimport androidx.media3.effect.Presentation\n",
        1,
    )
if "import androidx.media3.transformer.InAppMp4Muxer" not in s:
    s = s.replace(
        "import androidx.media3.transformer.Effects\n",
        "import androidx.media3.transformer.Effects\nimport androidx.media3.transformer.InAppMp4Muxer\n",
        1,
    )

old_transformer = '''        transformer = Transformer.Builder(this)\n            .setEncoderFactory(encoderFactory)\n            .setVideoMimeType(MimeTypes.VIDEO_H264)\n            .setAudioMimeType(MimeTypes.AUDIO_AAC)\n'''
new_transformer = '''        val muxerFactory = InAppMp4Muxer.Factory { metadataEntries ->\n            // Keep every metadata entry Transformer extracted from the source file.\n            // Add GPS only when the source metadata set does not already contain it.\n            val coords = parseLocation(sourceMeta.locationText)\n            if (coords != null && metadataEntries.none { it is Mp4LocationData }) {\n                metadataEntries.add(Mp4LocationData(coords.first, coords.second))\n            }\n        }\n\n        transformer = Transformer.Builder(this)\n            .setMuxerFactory(muxerFactory)\n            .setEncoderFactory(encoderFactory)\n            .setVideoMimeType(MimeTypes.VIDEO_H264)\n            .setAudioMimeType(MimeTypes.AUDIO_AAC)\n'''
if "val muxerFactory = InAppMp4Muxer.Factory" not in s:
    if old_transformer not in s:
        raise SystemExit("Could not find Transformer builder block")
    s = s.replace(old_transformer, new_transformer, 1)

old_remux = '''                        val locationCopy = File(cacheDir, "metadata_$date.mp4").also { if (it.exists()) it.delete() }\n                        val fileToSave = if (parseLocation(sourceMeta.locationText) != null) {\n                            remuxWithLocation(exported, locationCopy, sourceMeta.locationText)\n                            exported.delete()\n                            locationCopy\n                        } else exported\n\n'''
new_remux = '''                        // Do not remux a second time here. Media3's InAppMp4Muxer keeps\n                        // source metadata and writes GPS directly into the transformed MP4.\n                        val fileToSave = exported\n\n'''
if old_remux in s:
    s = s.replace(old_remux, new_remux, 1)
elif "val fileToSave = exported" not in s:
    raise SystemExit("Could not find v1.4 metadata remux block")

path.write_text(s, encoding="utf-8")
