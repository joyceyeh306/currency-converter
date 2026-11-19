# ㄚ喬文字搜尋測試 003
以已實機使用的ㄚ喬計步器 003 建置規格重建獨立專案。AGP 8.9.2 / Gradle 8.11.1 / JDK17 / minSdk29 / targetSdk35；新 package tw.ajo.phototext，獨立 release 簽章。僅保留中文＋英文與英文模型，先驗證安裝與核心搜尋。無網路存取權限，原始照片只讀。照片選取、辨識、記錄及搜尋功能沿用 001。日韓模型延後整合。

## 004
保留 003 applicationId、簽章、資料檔 index.json、ML Kit 版本及辨識邏輯；Android 13+ 使用原生 Photo Picker 多選（系統上限），無法啟動時或較舊 Android 使用文件選擇器。根介面處理狀態列、導覽列、顯示切口及鍵盤 insets；批次顯示實際經過時間與每張辨識耗時平均值。更新前不必清除 003 資料。

## 005 啟動修正版
004 啟動閃退：移除 onCreate 早期直接存取 WindowInsetsController 的程式碼，沿用原主題系統列外觀，根 View 附著後才設定 safe-area；保留相片選擇器、耗時及原本 OCR、簽章、索引格式。實機閃退原因仍待驗證；未加大量掃描功能。