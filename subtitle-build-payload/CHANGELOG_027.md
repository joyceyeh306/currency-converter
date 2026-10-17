# 027 — iPhone HDR 色彩與原始動態流暢度優先修正

- 唯一功能修改範圍：字幕影片輸出的色彩／影格率，不改 Whisper Small Q5_1、Android 快速辨識、雲端辨識、字幕時間、App 圖示。
- HDR → SDR 仍透過 Android MediaCodec 正規 tone mapping，而非將 HDR 資料直接當 SDR。
- 取消以往在 HDR 60fps 影片上強制 60 → 30 的 FrameDropEffect，目標是保留原始影格率。
- 取消 HDR → SDR 後的 HSL 飽和度 +6、亮度 -2 後製，避免額外偏色；HDR 與 SDR 畫面不保證完全相同。
- 編碼器相容模式保留一次 720p 重試，**仍不主動降低影格率**；若 OPPO 裝置仍不支援會明確失敗，而不靜默輸出 30fps。
- 編輯 UI 的輸出預估由「HDR 60 fps 轉 30 fps」改為「目標維持原片約 XX fps」。實際輸出以檔案影格率與裝置測試為準。
- Android versionCode 27、versionName 027；簽章需用 026 同一憑證覆蓋安裝。

## 驗收資料
- 原始 iPhone HLG HDR `28825.mp4`（1920×1080、約 60fps、BT.2020/HLG）
- 026 有字幕成品 `29017.mp4`（1920×1080、約 30fps、BT.709 SDR）
- 在 OPPO 實際 027 檢查色彩、流暢度、輸出時間及是否能 60fps；未實測不得宣稱已修復。
- 掌聲被誤辨為「鼓掌」為另一問題，刻意不在 027 改動。
