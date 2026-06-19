# ChangeGpsAnyway

Android 定位模擬 App（Kotlin + Jetpack Compose + osmdroid，非 root）。三種模式：

1. **傳送/飛人** — 點地圖或輸入經緯度，立即把定位改到該點。
2. **路線/種花** — 點選多個路徑點、設定時速，沿線自動移動定位。
3. **搖桿** — 漂浮在其他 App（如 Pikmin Bloom）之上的搖桿，即時移動定位。

## ⚠️ 重要風險告知

- Android mock location 是合法的開發者功能，但**用於 Pikmin Bloom 等 Niantic 遊戲違反其服務條款，可能導致帳號被封**。
- 非 root 的標準 mock 會在定位上帶 `isFromMockProvider = true`，**反作弊可能偵測到**。本專案做技術上正確的實作，但無法保證繞過偵測。
- 請先用**測試/小號**驗證，傳送後遵守 App 內顯示的冷卻時間。

## 建置

需要 Android Studio（Koala 以上）。本機未含 Gradle wrapper jar 與 Android SDK：

1. 用 Android Studio 開啟此資料夾，IDE 會自動下載 SDK 並產生 Gradle wrapper。
2. 或安裝 Gradle 8.9+ 後在專案根目錄執行 `gradle wrapper` 產生 wrapper，再 `./gradlew assembleDebug`。

- minSdk 31 / targetSdk 34 / compileSdk 34。

## 裝置設定（首次）

App 內「設定步驟」卡片會引導：

1. 授予**精確定位**權限（`ACCESS_FINE_LOCATION`）。
2. 開發者選項 → **選擇模擬位置應用程式** → 選本 App。（無法由程式偵測，只提供捷徑）
3. **懸浮視窗**權限（搖桿模式）。
4. **電池最佳化豁免**（長時間移動穩定）。

## 驗證流程（務必照順序）

1. 安裝到 Android 12+ 實機，完成上述設定。
2. **先用 Google Maps 或「GPS Test」類 App** 確認定位確實被改變：
   - 傳送：點地圖 / 輸入座標 → 看藍點是否跳到該處。
   - 路線：加 ≥2 點、設時速、開始 → 看藍點沿線移動。
   - 搖桿：點地圖設起點 → 開啟搖桿懸浮窗 → 在其他 App 上拖動搖桿。
3. 基本功能穩定後，再用**測試小號**在 Pikmin Bloom 驗證飛人/種花。
4. 種花時讓手機保持輕微晃動以產生硬體步數（非 root 無法 mock 感測器）。

## 模組

| 檔案 | 職責 |
|---|---|
| `mock/MockLocationEngine.kt` | 註冊/推送/清除 GPS+NETWORK+FUSED test provider；高斯抖動、穩定海拔、即時時間戳 |
| `mock/MockLocationService.kt` | 前景服務、WakeLock、協程 tick loop、通知停止鍵 |
| `mock/LocationState.kt` | `MockController` 單一狀態來源（`update{}` 原子更新）、冷卻估算 |
| `mock/RouteSimulator.kt` | 沿 waypoints 依時速插值（單次/循環/來回） |
| `overlay/OverlayService.kt` + `JoystickView.kt` | 懸浮搖桿（NOT_FOCUSABLE / NOT_TOUCH_MODAL / 旋轉重夾） |
| `ui/*` | Compose 介面、osmdroid 地圖、權限引導、冷卻提示 |
| `util/GeoUtils.kt` | haversine、bearing、destination point |
