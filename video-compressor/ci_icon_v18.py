from pathlib import Path
import base64

ICON_B64 = """/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAEAAQADASIAAhEBAxEB/8QAHQAAAQQDAQEAAAAAAAAAAAAAAAUGBwgBBAkCA//EAE0QAAEDAwEEBwQFCAgEBQUAAAECAwQABREGBxIhMQgTQVFhcZEUIoGhCRVCgrEjMlJicpLB0RYzQ1ODk6LhJTREoxdUc7LxY2TC0vD/xAAcAQACAgMBAQAAAAAAAAAAAAAABgUHAwQIAgH/xAA9EQABAwICBgkCBAUDBQAAAAABAAIDBBEFIQYSMUFRkRMiYXGBobHB0QcyFBVi8CNCUuHxJDOCNHKSstL/2gAMAwEAAhEDEQA/AOqdFFFCEUUUUIRRRRQhFFFFCEUUUUIRRRRQhFFFFCEUUUUIRRRRQhFFFFCEUUUUIRRRRQhFFFFCEUUUUIRRRRQhFFFFCEUUUUIRRRRQhFFFFCEUUUUIRRRRQhFFFFCEUUUUIf/Z"""

# Write the user-selected icon into Android resources. The image itself is not redrawn.
out = Path("video-compressor/app/src/main/res/drawable-nodpi/icon_art.jpg")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(base64.b64decode(ICON_B64))

manifest = Path("video-compressor/app/src/main/AndroidManifest.xml")
s = manifest.read_text(encoding="utf-8")
s = s.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/icon_art"')
s = s.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/icon_art"')
manifest.write_text(s, encoding="utf-8")
