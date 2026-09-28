# ㄚ喬的相簿：接續開發紀錄

## 唯一穩定母版

- 版本：2.0.0-alpha11-stable1，versionCode 22。
- GitHub：joyceyeh306/currency-converter，photo-manager-v1 分支。
- 固定提交：8dd319049a5170e61a706a30b60575337a044cea。後續請以提交辨識母版，勿只依賴可移動的分支名稱。
- 成功建置：https://github.com/joyceyeh306/currency-converter/actions/runs/36380087502
- 該次建置日誌確認 checkout 上述固定提交。
- 該次產物與使用者提供的 APK 逐位元組相同。
- APK SHA-256：12ff0569239b87fe66434b48f40359d0e2735b86c566429ff61246b54d7a8581。
- 簽章憑證 SHA-256：a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc。
- 相同簽章為 AOSP 公開 testkey；原建置使用官方 aosp-mirror/platform_build 的 testkey.pk8 與 testkey.x509.pem。

## 此次修改：2.0.0-alpha11-info1 / versionCode 23

只新增左上「⋮ → 版本資訊」與相應對話框。版本號取自 BuildConfig，顯示稳定母版與本次更新內容。此版需使用者實機驗證。

整理流程、資料結構、照片讀寫、拖曳、關鍵字功能均未修改。

## 下一項工作，先討論

「選照片 → 整理」曾有跳出／閃退回報，但不要將 alpha12/13 的問題假定已存在於 stable1。
先核對 stable1 的實際症狀，再檢查對應原始碼。

使用者希望選取照片後底部為「分享｜收藏｜相簿｜整理｜刪除」。
相簿為一級操作；整理只放關鍵字、拍攝時間、GPS、檔名、完整資料、修改紀錄。
不要自動把這項改版混入版本資訊版。

## 原則

禁止以 alpha12/13 系列當母版。
任何照片修改不得重新編碼、重新壓縮或更改影像像素；無法保證無損的格式不得寫回原檔。
核心本機相簿與資料整理功能須可離線運作。
每次交付 APK 同時保留對應固定 Git 提交、原始碼 ZIP、建置流程及簽章辨識資料。

## 建置

Java 17、Gradle 8.9、Android SDK / Build Tools 35。
先把 ../photo_manager_build/icon.b64 解碼為 app/src/main/res/drawable/app_icon.png，再執行 gradle :app:assembleRelease。
完整可執行步驟在 ../.github/workflows/build-ajo-album-alpha11-info1.yml。
GitHub 的工作流程產出 APK 與對應完整原始碼 ZIP。
