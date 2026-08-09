# Fire Tablet Remote

アーム等に固定した Fire Tablet を、Android スマートフォンから同一 LAN 内で操作するためのクライアント・サーバー型アプリです。主な想定用途は、手元から離れた Fire HD 10 上の YouTube 等を操作することです。

## 想定環境

- Server: Fire HD 10（第13世代）/ Fire OS 8 系を主対象
- Controller: Motorola Edge 50 Pro / Android 16 を主対象
- 接続: 信頼できる同一 LAN（Protocol v1 は認証・TLS なし）
- 画面: Fire Tablet と Controller の両方を Landscape 固定で使用

## ディレクトリ

```text
FireRemoteServer/       Android Studio / Kotlin の WebSocket Server
FireRemoteController/   Android 向け .NET MAUI WebSocket Client
protocol/               実装非依存の JSON Protocol v1
docs/                   責務分離などの設計メモ
tools/MockWebSocketServer/ Controller 単体確認用 .NET mock
```

## FireRemoteServer

Android Studio で `FireRemoteServer/` を開き、Gradle Sync 後に実機へ実行します。画面の **Start server** を押すと `ws://<tablet-ip>:8080/ws` で待ち受けます。受信内容は Logcat の `FireRemoteWebSocket` と `FireRemoteCommand` タグで確認できます。

`ping` は `pong` を返します。`back` / `home` / `recents` は接続済みの AccessibilityService を通じてAndroidのGlobal Actionを実行します。`tap` / `longPress` / `swipe` は `dispatchGesture()` で実行します。

| Command | 現在の状態 |
|---|---|
| `ping` | 実装済み |
| `back` | 実装済み |
| `home` | 実装済み |
| `recents` | 実装済み |
| `tap` | 実装済み |
| `longPress` | 実装済み |
| `swipe` | 実装済み |
| `preview` | 実装済み（約1fps） |

コマンド解析テスト:

```powershell
cd FireRemoteServer
./gradlew.bat testDebugUnitTest
```

## FireRemoteController

Android 専用の .NET MAUI プロジェクトです。通常画面は約1秒間隔の静止画Previewを中心とし、右側の `◀`（Back）/ `●`（Home）/ `■`（Recents）からCommandを送信します。Server IP、Port、Connect / Disconnect、詳細Status、**Send test ping** は右上の設定ボタンから開く接続設定内にあります。Preview上のタップ・長押し・スワイプは、AspectFitの余白を考慮してFire実画面ピクセルへ変換して送信します。

```powershell
dotnet build FireRemoteController/FireRemoteController.csproj
```

Visual Studio または `dotnet build -t:Run` から Android 実機へ配置してください。Controller の Landscape 固定は `Platforms/Android/MainActivity.cs` の `ScreenOrientation.SensorLandscape` で設定しています。Server 側も Manifest で `sensorLandscape` に設定しています。

## 片側だけで確認する

### Server 単体

一般的な WebSocket Client（Postman、websocat 等）で Server に接続し、次を送信します。

```json
{"version":1,"type":"ping","requestId":"manual-1"}
```

`{"version":1,"type":"result",..."success":true,"message":"pong"}` が返り、Logcat に接続・受信・解析結果が出れば Server 側の最小経路を確認できています。

### Fire HD 10でbackを確認する

1. FireRemoteServerのDebug APKをFire HD 10へインストールして起動します。
2. **Open Accessibility settings** を押します。
3. Accessibility設定で **Fire Remote Accessibility** を有効にします。
4. アプリへ戻り、`Accessibility: Connected` と表示されることを確認します。
5. **Start server** を押します。
6. 同一LAN上のWebSocket Clientから `ws://<tablet-ip>:8080/ws` へ接続します。
7. 次のCommandを送信し、Tablet上で実際に「戻る」が発生することと、`success: true` のresultを確認します。

```json
{"version":1,"type":"back","requestId":"back-test-1"}
```

Fire OSのバージョンによってAccessibility設定の名称や階層が異なる場合があります。その場合は設定画面内の「ユーザー補助」またはインストール済みサービスに相当する項目から **Fire Remote Accessibility** を有効にしてください。未接続時やAndroid APIが操作を受理しなかった場合は、Serverはクラッシュせず `success: false` と理由を返します。

### Controller 単体

PC 上で依存パッケージ不要の Mock Server を起動します。

```powershell
dotnet run --project tools/MockWebSocketServer --urls http://0.0.0.0:8080
```

Controller から PC の LAN IP（Android Emulator なら通常 `10.0.2.2`）、Port `8080` へ接続します。ping 送信後に `mock received ping` が表示されれば Client の送受信を確認できます。Windows Firewall で受信許可が必要な場合があります。

## 現在の開発段階

初期基盤です。Protocol、WebSocket送受信、Command解析、Foreground Service、AccessibilityService経由のback/home/recents/tap/longPress/swipe操作を実装しています。Controllerでは低解像度静止画Previewを表示し、AspectFit座標変換後のFire実座標へタップ・長押し・スワイプできます。認証、TLS、自動探索はまだ実装していません。詳細は [Protocol](protocol/README.md) と [Architecture](docs/architecture.md) を参照してください。
