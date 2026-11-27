# AGENTS.md

## プロジェクト概要

沖縄みらいAI＆IT専門学校（チーム Vita）の観光 / 家計簿 (kakeibo) アプリです。
技術スタック: Spring Boot 4.2.0 (Java 25) + Spring Data JPA + Thymeleaf + Spring Security（CSRF とヘッダーのみ）。
DB: デフォルトの `local` プロファイルでは Oracle XE、テストでは H2、
チーム共有用の `prod` プロファイルでは環境変数で指定した Oracle を使います。
UI のテキストとドメイン用語は日本語です（カテゴリ: 食事 / 交通 / 宿泊 / 観光 / その他）。

## コマンド

リポジトリ直下の `pom.xml` が Maven リアクターです。4機能は `spot/`、`currency/`、`language/`、`receipt/` に分かれ、起動アプリと家計簿は `kakeibo/` にあります。Maven Wrapper は `kakeibo/` のものを使い、リポジトリルートを作業ディレクトリとして実行してください。

- アプリ起動（ローカル Oracle）: `.\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo -am -DskipTests install` の後に `.\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo spring-boot:run`
- アプリ起動（チーム共有、`prod` プロファイル）: 接続情報を環境変数で設定して起動します。手順は README の「チーム共有環境での起動」を参照してください
- テスト実行: `.\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo -am test`
- テストを 1 つだけ実行: `.\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KakeiboApplicationTests test`
- パッケージ作成: `.\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo -am package`

Maven Wrapper 同梱（mvnw / mvnw.cmd、wrapper 3.3.4 -> Maven 3.9.16）。Java 25 が必要です。

## ワークフロー（Git / PR）

- コミットメッセージは日本語で、何を変更したかが分かるように書きます（メッセージがそのまま PR の概要になります）。
- `main` へ直接コミット・プッシュしないでください。ブランチを `main` へマージするのも禁止です。
  マージの判断はすべてリポジトリの所有者が GitHub 上で行います。
- 作業は `feature/*` ブランチで行い、コミットしてそのブランチをプッシュしたら、そこで止めてください。
  PR が必要な場合はタイトルと本文を日本語で下書きし、作成・レビュー・マージは所有者に任せます。
- この環境には `gh` CLI が入っていないため、`gh pr create` は使えません。

## 現在の状態

- 4機能（スポット・為替・言語設定・レシートOCR）を独立したMavenモジュールに分け、`kakeibo/` がそれらを組み合わせて起動するSpring Bootアプリです。Javaパッケージは `com.example.kakeibo`、起動クラスは `KakeiboApplication` です。
- 認証: `kakeibo/controller/AuthController` によるセッション方式の独自ログイン（スプラッシュ / 言語選択 / 新規登録 /
  ログイン / ログアウト / ホーム）で、実処理は `service/UserService` + `repository/UserRepository`
  （`users` テーブル、パスワードは BCrypt ハッシュ）を使います。ログイン時にセッションへ `loggedIn`、
  `expenseUserId = "user-<users.id>"`、`username`、`userEmail` を保存します。
  Spring Security のフォームログインは無効化していて、CSRF 保護とセキュリティヘッダーだけを提供します。
  `WebSecurityConfig` のインターセプタが `/expenses/**` をログイン済み利用者だけに制限します。
- 支出（家計簿）: `Expense` エンティティ、`ExpenseRepository`、`ExpenseController`
  （`@RequestMapping("/expenses")`）。このコントローラーだけは `controller/` ではなくルートパッケージにある点に注意してください。
  画面は `kakeibo/src/main/resources/templates/expenses/{List,Form,Graph,Capture}.html` です。
  レシート登録コントローラー `kakeibo/` とOCR処理 `receipt/` が連携し、
  Amazon Textract の認証情報が必要です。読み取り結果を確認・修正してから支出登録します。
  OCR API は `/api/receipts/scan`、画面は `/receipts/scan` です。
  削除エンドポイントはありません（設計書の権限は SELECT/INSERT/UPDATE のみ）。
  他人の支出を開くと 404 になります。
- 旅行機能: `spot/` モジュールがスポット一覧・詳細
  （`/spots`、`/spots/{id}`）と旅行プランを担当します。`kakeibo/controller/FeatureController` は旧URLからのリダイレクトとマイページ（`/mypage`）を担当します。
- 為替: `currency/` モジュールの `CurrencyController`（`/currency`、`/currency/convert`）が
  通貨換算を担当し、`ExchangeRateService` がレート取得とキャッシュ・フォールバックを行います。
- 言語: `language/` モジュールが言語選択、セッションのロケール解決、翻訳メッセージを担当します。
- 共通レイアウト: `kakeibo/src/main/resources/templates/fragments/common.html` + `kakeibo/src/main/resources/static/css/app.css`。

## 注意事項

- `Hello.java`（リポジトリ直下）と `kakeibo/bin/`（Eclipse のビルド出力）は fd0922f で削除済みです。
  ビルド出力をリポジトリへ再追加しないでください。
- DB: ローカルの Oracle XE は `//localhost:1521/XEPDB1`、ユーザーは `vita_app_user` です。パスワードは
  `application-local.properties` にだけ書き、Git 管理外にしてください。絶対にコミットしないでください。
- `spring.jpa.hibernate.ddl-auto` は `application.properties` と `application-prod.properties` で `validate`、
  テストでは `create-drop` です。ローカルでテーブル変更が必要な場合の手順は README を確認します。
- レシート OCR は AWS Textract の `AnalyzeDocument` API を呼び出すため、AWS 認証情報と `textract:AnalyzeDocument` 権限が必要です。
  AWS 認証情報をソースやGitに保存しないでください。
- SQL スクリプトは `db/` にあります（Oracle 方言）: `create_tables.sql`、`insert_data.sql`、`insert_expenses.sql`、
  `queries_expenses.sql`。支出は `user-<users.id>` で識別します。旧 `dev-device-001` /
  `demo-device-001` のデータは自動移行されません。
- エンティティに反映された設計判断のうち、素の JPA デフォルトと異なる点: `id` は `Integer`
  （設計書が INTEGER。`Long` にすると `ddl-auto=validate` で失敗する）、`originalCurrency` のデフォルトは `"JPY"`、
  `createdAt` は DB 管理（`insertable = false, updatable = false`）です。

## テスト

- テストクラスは `kakeibo/src/test/java/com/example/kakeibo/` に 2 本あります。
  `KakeiboApplicationTests.contextLoads`（`@SpringBootTest`）と
  `LoginExpenseIntegrationTests`（MockMvc による通し確認: 新規登録、ログイン失敗、CSRF、
  ホームからの画面遷移、レシート画像アップロード、為替換算、スポット / マイページ表示、
  支出のユーザー分離、削除不可、ログアウト）です。
- テストは専用のインメモリ H2 で実行されます（`src/test/resources/application.properties`、
  `ddl-auto=create-drop`）。実行に Oracle は不要です。
- すべて実行する場合: `.\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo -am verify`
