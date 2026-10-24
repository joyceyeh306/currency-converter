# ㄚ喬自動字幕 058｜K 版 +200% 飽和度目標

## 基準與範圍
- 以 OPPO Find X9 Pro 使用者已實機確認無破圖、無抖動的 057 為母版。
- K 樣本使用 FFmpeg `eq=saturation=3.0`（相對 054 的 SDR 成品飽和度增加 200%）。
- Android Media3 `RgbMatrix` 使用線性 RGB 而非 FFmpeg 色彩域；058 把 057 係數 2.5 改為 3.0 作為 K 視覺目標的近似，並非逐像素完全相等。

## 僅修改兩個既有程式檔案
- `app/build.gradle`：版本 58 / 058。
- `app/src/main/java/tw/ajo/autosubtitles/SdrSaturation056.java`：保留 H 歷史常數，新增 K 3.0 常數並讓實際矩陣使用它。
- **其他既有程式碼不變**，特別是 057 `experimentalSetMaxFramesInEncoder(4)` 和 054 MediaCodec HDR→SDR 處理。
- 完整保留 Whisper、字幕、時間軸、介面、FPS、音訊、原 SDR 處理、HDR 診斷與 fallback。

## 驗收與安全
- 058 的 25 項 Java 主要回歸檢查包含實際係數、灰階、亮度、飽和度及 057 穩定性設定。
- APK 由 GitHub Actions 編譯臨時檔後，再以使用者原私人簽章在本地重簽。私鑰不可上傳 GitHub。
- 實機仍需再次用原 HDR 測試影片確認 K 色彩與 057 的無破圖穩定性。
