# ㄚ喬自動字幕 055 — HDR→SDR 飽和度 +4（僅兩處程式修改）

以使用者實機確認「紫綠色已消失、但仍偏淡」的 054 為基準。

- **僅調整 HDR 來源在一般 MP4 匯出的飽和度**：沿用 `SdrColor054` 與既有 `HslAdjustment.Builder().adjustSaturation(...)`，將常數 `SATURATION_POINTS` 從 `2f` 改為 `4f`，不是增加 4 在 2 之上。
- **保留 054 已實機確認的色彩路徑**：HDR→SDR MediaCodec tone-map + H.264 SDR，不回到 049～053 正式輸出造成紫綠偏色的 KEEP_HDR。
- **不修改**：亮度、對比、色相、幀率、解析度、音訊、字幕外觀、字幕時間、Whisper辨識、主畫面及設定內 HDR 診斷工具。SDR 來源不套用飽和度。
- Android `versionCode=55`、`versionName=055`。使用與 054 相同的原私人簽章才可直接覆蓋安裝。
- 僅程式回歸測試與 CI 編譯不足以保證 iPhone 膚色已達目標；需要實機對同一原片 `28822.mp4` 匯出檢查。
