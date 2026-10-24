# ㄚ喬自動字幕 057｜保留第一次 056 顏色、限制編碼緩衝

- 基礎：**第一次 056** (GitHub commit `781a3c5924240906fde4d0399417d8f3984fd35d`)，不是後來 2.10 係數的第二次 056。
- 色彩：嚴格保留第一次 056 `SdrSaturation056.getMatrix()` 的 `H_TARGET_MULTIPLIER = 2.5f`；也保留 054 正常 HDR→SDR `Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC`。這仍是 H 色彩的近似，不能保證等於 FFmpeg eq=saturation=2.5。
- 異常：054 沒有出現的綠色條紋，在 055/056 影格內出現，可能涉及硬體解碼/GPU/編碼管線緩衝。057 針對**一般 HDR 影片匯出**，以 Media3 `experimentalSetMaxFramesInEncoder(4)` 限制 encoder 同時處理的影格。此 API 是實驗性設定，目的是降低 in-flight buffer 壓力，可能增加匯出時間。**不是已證實的根因修復**。
- 不變：Whisper、字幕、操作 UI、畫質設定、解析度、音效、音訊、HDR 診斷路線、SDR 輸入與 fallback。
- 版本：57/057；簽署：沿用 055/第一次 056 的私人簽章，金鑰只保存在本地 ZIP，不上傳 GitHub。
- 實機必要測試：OPPO Find X9 Pro，原始 `28822.mp4`，匯出後確認 60fps 與第 6.7/7.4/8.1/8.5/20.3/30.6/38.2 秒的影格，以及全片其他位置；比對第一次 056 色彩。測完才能確定本試驗設定能否解決雜訊。
