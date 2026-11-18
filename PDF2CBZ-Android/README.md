# PDF2CBZ — Android漫画PDF→CBZ一括変換アプリ

**PDF2CBZ** は、複数の漫画PDFファイルを **1冊ごとに独立したCBZファイル**（`manga01.pdf` → `manga01.cbz`）へ完全オフラインで一括変換する Android アプリです。Mihon（旧Tachiyomi系リーダー）をはじめとする各種コミックビューアでそのまま読み込めるフラット構造の CBZ アーカイブを生成します。

---

## スマホへのインストール方法（プログラミング不要・3ステップ）

1. **APKファイルをダウンロードする**
   `PDF2CBZ-debug.apk` を Android スマホに保存します。
2. **スマホで開いてインストールする**
   ダウンロードした `PDF2CBZ-debug.apk` をタップします。「不明なアプリのインストールを許可しますか？」と表示されたら許可（オン）にして「インストール」を押します。
3. **保存先とPDFを選んで変換する**
   アプリを開き、「CBZ保存先フォルダ」を選択 → 「PDFを追加」で漫画PDFをまとめて選び → 「変換開始」を押すだけで1冊ずつ `.cbz` が作成されます。

---

## 主な特徴

- **1冊＝1CBZの個別出力**: 50冊のPDFを選択した場合、50個の独立した `.cbz` ファイルを自動生成します（複数PDFを1つのZIPにまとめることはありません）。
- **完全オフライン・プライバシー保護**: `INTERNET` 権限を持たず、広告・アカウント登録・外部サーバー送信は一切行いません。
- **PDF内画像の直接抽出 + PdfRendererフォールバック**:
  - PDFBox-Android を用いて、単一スキャン画像で構成されたページのJPEG等を再圧縮なしで直接抽出し、画質劣化と容量増加を防ぎます。
  - ベクター描画・テキストレイヤー・注釈・マスク・回転を含むページは自動的に Android 標準の `PdfRenderer` へフォールバックし、表示崩れを防止します。
- **大量ファイル（50〜100冊）対応設計**:
  - 1冊・1ページずつのストリーミング処理と決定的な `Bitmap.recycle()` により OOM を防止。
  - `StatFs` によるストレージ空き容量チェック、一時キャッシュの自動削除、WorkManager（`ForegroundInfo` / `dataSync`）による長時間実行耐性。
  - 処理中状態の永続化により、途中で中断・エラーが発生しても残りの冊数へ進み、失敗ファイルのみ再試行可能。
- **Storage Access Framework (SAF) 完全準拠**:
  - `OpenMultipleDocuments` / `OpenDocumentTree` と永続URI権限を使用し、不要なストレージ権限を要求しません。
- **Material Design 3 / Material You**:
  - Dynamic Color、ライト/ダークモード切り替え、控えめで滑らかなアニメーション。

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

- ページ順序は 4桁以上のゼロ埋め連番（`0001.jpg`, `0002.jpg`...、10,000ページ以上は自動で5桁以上に拡張）で保証されます。
- 不要なサブフォルダ階層は作成せず、元のPDF本体もZIP内に含めません。

---

## ビルド要件・環境

| 項目 | バージョン |
| :--- | :--- |
| JDK | 17 (Temurin / OpenJDK 17) |
| Gradle | 8.7 (Gradle Wrapper 同梱) |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 34 (Android 14) |
| minSdk | 26 (Android 8.0) |

---

## ローカルビルド手順（Gradle Wrapper）

```bash
# Android SDK パスを設定（例）
echo "sdk.dir=/opt/android-sdk" > local.properties

# ユニットテスト実行
./gradlew testDebugUnitTest

# デバッグAPKビルド
./gradlew assembleDebug
```

生成されるAPK:
`app/build/outputs/apk/debug/app-debug.apk`

---

## GitHub Actions 自動ビルド

`.github/workflows/android-build.yml` により、`main` / `master` へのプッシュまたは手動トリガーで自動的に `./gradlew testDebugUnitTest` と `./gradlew assembleDebug` が実行され、生成された APK が Artifacts（`PDF2CBZ-debug-apk`）に保存されます。

---

## 使用ライブラリとライセンス

- AndroidX / Jetpack Compose / Material 3 / WorkManager / DataStore / DocumentFile (Apache-2.0)
- Kotlin Coroutines & Serialization (Apache-2.0)
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0) — PDF内画像の直接抽出および暗号化PDF検知
