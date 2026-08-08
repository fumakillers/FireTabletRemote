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

`ping` は `pong` を返します。`tap` / `back` / `longPress` は JSON の解析とログ出力まで実装済みですが、AccessibilityService を使った実操作は未実装です。Accessibility 設定への導線と Service の骨格のみ用意しています。

コマンド解析テスト:

```powershell
cd FireRemoteServer
./gradlew.bat testDebugUnitTest
```

## FireRemoteController

Android 専用の .NET MAUI 初期プロジェクトです。接続先 IP と Port を入力し、Connect 後に **Send test ping** で JSON を送信します。右側は将来の横向きプレビュー領域として空けてあります。

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

### Controller 単体

PC 上で依存パッケージ不要の Mock Server を起動します。

```powershell
dotnet run --project tools/MockWebSocketServer --urls http://0.0.0.0:8080
```

Controller から PC の LAN IP（Android Emulator なら通常 `10.0.2.2`）、Port `8080` へ接続します。ping 送信後に `mock received ping` が表示されれば Client の送受信を確認できます。Windows Firewall で受信許可が必要な場合があります。

## 現在の開発段階

初期基盤です。Protocol、WebSocket 送受信、Command 解析、Foreground Service、AccessibilityService の責務境界を用意しています。画面プレビュー、座標変換、Android gesture/global action、認証、TLS、自動探索はまだ実装していません。詳細は [Protocol](protocol/README.md) と [Architecture](docs/architecture.md) を参照してください。
