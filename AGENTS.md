# AGENTS.md

## プロジェクト概要

沖縄みらいAI＆IT専門学校（チーム Vita）の観光 / 家計簿 (kakeibo) アプリです。
技術スタック: Spring Boot 4.1.2 (Java 25) + Spring Data JPA + Thymeleaf + Spring Security（CSRF とヘッダーのみ）。
DB: デフォルトの `local` プロファイルでは Oracle XE、テストでは H2、
チーム共有用の `prod` プロファイルでは環境変数で指定した Oracle を使います。
UI のテキストとドメイン用語は日本語です（カテゴリ: 食事 / 交通 / 宿泊 / 観光 / その他）。

## コマンド

Maven モジュールは `kakeibo/` にあります。Maven コマンドはリポジトリのルートではなく、すべてここで実行してください。

- アプリ起動（ローカル Oracle）: `cd kakeibo && ./mvnw spring-boot:run`
- アプリ起動（チーム共有、`prod` プロファイル）: 接続情報を環境変数で設定して起動します。手順は README の「チーム共有環境での起動」を参照してください
- テスト実行: `cd kakeibo && ./mvnw test`
- テストを 1 つだけ実行: `cd kakeibo && ./mvnw test -Dtest=KakeiboApplicationTests`
- パッケージ作成: `cd kakeibo && ./mvnw package`

Maven Wrapper 同梱（mvnw / mvnw.cmd、wrapper 3.3.4 -> Maven 3.9.16）。Java 25 が必要です。

## ワークフロー（Git / PR）

- コミットメッセージは日本語で、何を変更したかが分かるように書きます（メッセージがそのまま PR の概要になります）。
- `main` へ直接コミット・プッシュしないでください。ブランチを `main` へマージするのも禁止です。
  マージの判断はすべてリポジトリの所有者が GitHub 上で行います。
- 作業は `feature/*` ブランチで行い、コミットしてそのブランチをプッシュしたら、そこで止めてください。
  PR が必要な場合はタイトルと本文を日本語で下書きし、作成・レビュー・マージは所有者に任せます。
- この環境には `gh` CLI が入っていないため、`gh pr create` は使えません。

## 現在の状態

- 単一モジュールの Spring Boot アプリです。パッケージは `com.example.kakeibo`、起動クラスは `KakeiboApplication` です。
- 認証: `controller/AuthController` によるセッション方式の独自ログイン（スプラッシュ / 言語選択 / 新規登録 /
  ログイン / ログアウト / ホーム）で、実処理は `service/UserService` + `repository/UserRepository`
  （`users` テーブル、パスワードは BCrypt ハッシュ）を使います。ログイン時にセッションへ `loggedIn`、
  `expenseUserId = "user-<users.id>"`、`username`、`userEmail` を保存します。
  Spring Security のフォームログインは無効化していて、CSRF 保護とセキュリティヘッダーだけを提供します。
  `WebSecurityConfig` のインターセプタが `/expenses/**` をログイン済み利用者だけに制限します。
- 支出（家計簿）: `Expense` エンティティ、`ExpenseRepository`、`ExpenseController`
  （`@RequestMapping("/expenses")`）。このコントローラーだけは `controller/` ではなくルートパッケージにある点に注意してください。
  画面は `src/main/resources/templates/expenses/{List,Form,Graph,Capture}.html` です。
  レシート OCR は `ReceiptController` / `ReceiptOcrService` / `TextractService` が担当し、
  Amazon Textract の認証情報が必要です。読み取り結果を確認・修正してから支出登録します。
  OCR API は `/api/receipts/scan`、画面は `/receipts/scan` です。画面はカメラ撮影と画像選択の
  両方に対応し、送信前に長辺 2400px 以下へ縮小して JPEG 化します（上限 20MB、HEIC は非対応）。
  品目名はレシート上部に印字された会社名・店舗名を使います。店名が「領収書」より上にある場合は
  その上の文字を、見出しが一番上にある場合はその下の店名を読み取ります（見出し自体は除外）。
  合計金額は「合計」「税込合計」などの明示的な合計を最優先し、次に「合」、最後に「計」を使います。
  「小計」は合計として採用しません。OCR で「合」が欠けた「言十」も「計」として扱います。
  金額と税率が「¥5188%」のように連結された場合も、税率を分離して金額を復元します。
  カテゴリ（宿泊 / 交通 / 食事 / 観光 / その他）は店舗名・商品名・レシート全体から自動判定します。
  合計ラベルを特定できない場合は、小計・税・お預かり等を除いた最大金額を使います。
  編集画面には削除ボタンがあり、誤って重複登録した支出を削除できます（確認ダイアログ付き）。
  削除は `POST /expenses/{id}/delete` で、所有者以外が実行すると 404 になります。
  他人の支出を開くと 404 になります。
- 旅行機能: `controller/FeatureController` がスポット一覧・詳細
  （`/features/spots`、`/features/spots/{id}`。現在はハードコードされた見本 2 件）、マイページ
  （`/mypage`）、ホームの機能カードのリダイレクト（receipts -> `/receipts/scan`、
  realtime -> `/currency`、languages/settings -> `/mypage`）を担当します。
- 為替: `controller/CurrencyController`（`/currency`、`/currency/convert`）が
  USD/EUR/KRW/TWD を固定の見本レートで円換算します。外部 API にはまだ接続していません。
- 共通レイアウト: `templates/fragments/common.html` + `static/css/app.css`。
  言語選択は選択をセッションに保存するだけで、画面全体の翻訳は未実装です。

## 注意事項

- `Hello.java`（リポジトリ直下）と `kakeibo/bin/`（Eclipse のビルド出力）は fd0922f で削除済みです。
  ビルド出力をリポジトリへ再追加しないでください。
- DB: ローカルの Oracle XE は `//localhost:1521/XEPDB1`、ユーザーは `vita_app_user` です。パスワードは
  `application-local.properties` にだけ書き、Git 管理外にしてください。絶対にコミットしないでください。
- `spring.jpa.hibernate.ddl-auto` は `application.properties` と `application-prod.properties` で `validate`、
  テストでは `create-drop` です。ローカルでテーブル変更が必要な場合の手順は README を確認します。
- レシート OCR は AWS Textract の `AnalyzeDocument` API を呼び出すため、AWS 認証情報と `textract:AnalyzeDocument` 権限が必要です。
  AWS 認証情報をソースやGitに保存しないでください。
- スマートフォンや VS Code のポート転送経由で開いても `http://127.0.0.1:8080/...` へリダイレクトしないよう、
  `application.properties` で `server.tomcat.use-relative-redirects=true` と
  `server.forward-headers-strategy=framework` を設定しています。変更後はアプリの再起動が必要です。
- SQL スクリプトは `db/` にあります（Oracle 方言）: `create_tables.sql`、`insert_data.sql`、`insert_expenses.sql`、
  `queries_expenses.sql`。支出は `user-<users.id>` で識別します。旧 `dev-device-001` /
  `demo-device-001` のデータは自動移行されません。
- エンティティに反映された設計判断のうち、素の JPA デフォルトと異なる点: `id` は `Integer`
  （設計書が INTEGER。`Long` にすると `ddl-auto=validate` で失敗する）、`originalCurrency` のデフォルトは `"JPY"`、
  `createdAt` は DB 管理（`insertable = false, updatable = false`）です。

## テスト

- テストクラスは `kakeibo/src/test/java/com/example/kakeibo/` に 3 本あります。
  `KakeiboApplicationTests.contextLoads`（`@SpringBootTest`）、
  `LoginExpenseIntegrationTests`（MockMvc による通し確認: 新規登録、ログイン失敗、CSRF、
  ホームからの画面遷移、レシート画像アップロード、為替換算、スポット / マイページ表示、
  支出のユーザー分離、削除不可、ログアウト）、
  `ReceiptOcrServiceTests`（レシート OCR の合計金額・日付・品目・カテゴリ解析）です。
- テストは専用のインメモリ H2 で実行されます（`src/test/resources/application.properties`、
  `ddl-auto=create-drop`）。実行に Oracle は不要です。
- すべて実行する場合: `cd kakeibo && ./mvnw verify`
