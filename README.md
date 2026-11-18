# PDF2CBZ — Android漫画PDF→CBZ一括変換アプリ

**PDF2CBZ** は、複数の漫画PDFファイルを **1冊ごとに独立したCBZファイル**（`manga01.pdf` → `manga01.cbz`）へ完全オフラインで一括変換する Android アプリです。Mihon をはじめとするコミックビューアでそのまま読み込めるフラット構造の CBZ アーカイブを生成します。

---

## 主な特徴

- **1冊＝1CBZの個別出力**: 50冊のPDFを選択した場合、50個の独立した `.cbz` ファイルを自動生成します。
- **完全オフライン**: `INTERNET` 権限を持たず、外部サーバー通信やトラッキングは行いません。元PDFは初期設定で一切変更・削除しません。
- **高速変換パイプライン**:
  - **ゼロコピー直接抽出**: ページ全体（CropBox）を回転・透過・注釈・テキスト重畳なしで覆う単一 JPEG 画像は、再圧縮や一時ファイルを経由せず生ストリームのまま CBZ へ格納します。
  - **マルチワーカー `PdfRenderer` + `BitmapPool`**: 描画フォールバック時は複数ワーカーのネイティブ `PdfRenderer` とバイト上限付き `BitmapPool` でメモリ再確保と GC を抑制し、並列レンダリングを行います。
  - **ストリーミング `STORED` CBZ 出力**: 画像エントリは無圧縮 `ZipEntry.STORED`（CRC32事前計算）で単一出力ストリームへ順序通り直接書き込み、中間画像ファイルの大量生成と無駄な Deflate CPU 負荷を排除しています。
  - **ハイブリッドメモリ管理**: 大容量PDFでの PDFBox 解析時は `MemoryUsageSetting.setupMixed(16MB)` を使用し、直接抽出無効時や非JPEG出力時は PDFBox 読み込み自体をスキップします。
- **SAF 完全準拠 & WorkManager バックグラウンド実行**:
  - `OpenMultipleDocuments` / `OpenDocumentTree`、WorkManager（`dataSync` フォアグラウンドサービス）、一時停止・再開・キャンセル・エラー再試行・変換履歴に対応。
- **Material Design 3**: Dynamic Color、ライト／ダーク／システム連動、タブレット等の大画面幅にも対応。

---

## 出力されるCBZ構造（Mihon対応）

```text
manga01.cbz
├── 0001.jpg
├── 0002.jpg
├── 0003.jpg
├── ...
└── ComicInfo.xml   # 設定で有効化した場合のみ同梱
```

---

## ビルド要件・環境

| 項目 | バージョン |
| :--- | :--- |
| JDK | 21 (Temurin / OpenJDK 21) |
| Gradle | 9.3.1 (Gradle Wrapper 同梱) |
| Android Gradle Plugin | 9.1.1 |
| Kotlin | 2.4.21 |
| compileSdk / targetSdk | 36.1 (`platforms;android-36.1`) / 36 |
| minSdk | 26 (Android 8.0) |

---

## ローカルビルド手順（Gradle Wrapper）

```bash
# ユニットテスト・Robolectricテスト実行
./gradlew testDebugUnitTest

# デバッグAPKビルド
./gradlew assembleDebug
```

生成されるAPK: `app/build/outputs/apk/debug/app-debug.apk`

---

## GitHub Actions 自動ビルド

リポジトリのルートに配置された `.github/workflows/build-apk.yml` により、`main` / `master` ブランチへのプッシュまたは GitHub Actions 画面の「Run workflow」から自動的に `./gradlew testDebugUnitTest` と `./gradlew assembleDebug` が実行され、Artifacts（**`PDF2CBZ-APK`**）から APK をダウンロードできます。

> **アップロード時の注意**: ブラウザからドラッグ＆ドロップでアップロードする場合、環境によっては `.github/` や `.env.example` などの隠しファイル／フォルダが除外されることがあります。Git（`git add -A`）または隠しファイルを表示した状態で、`.github/workflows/build-apk.yml` ごとアップロードしてください。

---

## 使用ライブラリとライセンス

- AndroidX / Jetpack Compose / Material 3 / WorkManager / DataStore / DocumentFile (Apache-2.0)
- Kotlin Coroutines & Serialization (Apache-2.0)
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0)
