# アプリ開発

沖縄みらいAI＆IT専門学校が観光産業の課題解決に繋がるユーザアプリを制作するためのリポジトリ

## Maven モジュール構成

`Tourism_app` 直下の `spot/`、`currency/`、`language/`、`receipt/` が4つの機能モジュールです。`kakeibo/` は認証・支出管理を含む起動アプリで、各機能モジュールを読み込んで一つのWebアプリとして動作します。4機能はビルド単位で分離されていますが、ログイン・DB・画面遷移は共有します。

```text
Tourism_app/
├── spot/       # 観光スポット・旅行プラン
├── currency/   # 為替レート・通貨換算
├── language/   # 言語設定・翻訳・共通セッション情報
├── receipt/    # レシートOCR（Amazon Textract）
└── kakeibo/    # 起動アプリ、認証、家計簿、共通画面
```

## ホーム・ログイン・家計簿の統合アプリ

実行する Maven プロジェクトは `kakeibo` です。ZIP 内のホーム・ログイン・新規登録・言語選択の画面をこのアプリへ統合しています。
添付に含まれていた別の教材用 `shop` プロジェクト、ビルド済みファイル、PC 固有の DB 設定は取り込んでいません。

### Eclipse / VS Code

JDK 25 以上を使用してください。Eclipse では「既存 Maven プロジェクトのインポート」でリポジトリ直下の `pom.xml` を選択します。
VS Code ではリポジトリルートを開き、Java 拡張機能を使用します。
起動クラスは `com.example.kakeibo.KakeiboApplication` です。

ローカル開発では既定の `local` プロファイルで Oracle XE に接続します。Oracle のパスワードは
Git 管理外の `application-local.properties` に設定してください。テストは別の H2 データベースを使用します。

```sh
# Linux / macOS
sh kakeibo/mvnw -f pom.xml -pl kakeibo -am -DskipTests install
sh kakeibo/mvnw -f pom.xml -pl kakeibo spring-boot:run
# Windows PowerShell
# .\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo -am -DskipTests install
# .\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo spring-boot:run
```

ブラウザで `http://localhost:8080/` を開きます。起動画面 → 言語選択 → 新規登録 → ログイン → ホームと進めます。
ホームからは次の画面へ進めます。

- レシート家計簿：`/receipts/scan` で画像を Amazon Textract に送り、品目・金額・日付・カテゴリの候補を読み取ります。内容を確認・修正して支出登録できます。AWS 認証情報と `textract:AnalyzeDocument` 権限、`AWS_REGION`（既定 `ap-northeast-1`）が必要です。画像はAWSへ送信され、利用料金が発生する場合があります。AWS認証情報はGitに登録しないでください。ローカルではAWS CLIの名前付きプロファイル、またはIAMロールを使ってください。
- スポット：スポット一覧・詳細（見本データ 2 件）
- リアルタイム：為替レート・円換算画面（`/currency`）。レートは外部 API・キャッシュ・フォールバックから取得します
- 言語選択：選択をセッションに保存し、翻訳メッセージを切り替えます
- マイページ：共通の下部ナビから開けます（ログイン中の利用者情報を表示）

### DB とログイン

新規登録は既存設計の `users` テーブル（`username`, `email`, `password_hash`）を使います。パスワードは BCrypt で保存します。
ログイン後の支出識別子は `user-<users.id>` です。以前の `dev-device-001` や `demo-device-001` の支出は自動移行しません。
他人の支出の表示・変更を防ぎ、フォーム送信には CSRF 検証を行います。

Oracle を使用する場合は既定の `local` プロファイルを使い、既存の `db/create_tables.sql` を適用した DB を用意します。
`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` を安全な環境変数等で設定してください。
ZIP 側の `app_users` テーブルは不要です。このPCのOracle XEには `prod` プロファイルで接続し、スキーマの検証まで確認済みです。別サーバーの共有DBを使う場合は、そのDBのホスト名をURLに設定します。

### チーム共有環境での起動

チーム共有用の `prod` プロファイルはOracleへ接続し、スキーマを `validate` します。H2のテスト・開発データは使いません。
アプリを起動するPCまたはサーバーに、共有Oracle DBの接続先とアカウントを環境変数として設定します。パスワードはソースやGitに書かないでください。

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:SPRING_DATASOURCE_URL = "jdbc:oracle:thin:@//<共有DBのホスト>:1521/XEPDB1"
$env:SPRING_DATASOURCE_USERNAME = "vita_app_user"
$env:SPRING_DATASOURCE_PASSWORD = "<共有DBのパスワード>"
$env:MAVEN_USER_HOME = "$env:USERPROFILE\.m2"
.\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo -am -DskipTests install
.\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo spring-boot:run
```

起動後はアプリを動かしているPCのIPアドレスを使い、同じネットワーク内から `http://<アプリPCのIP>:8080/` を開きます。PCとネットワークのファイアウォールで、TCP 8080への受信をLAN内に限って許可する必要があります。
校外から利用する場合は、インターネットに公開できるサーバー、到達可能なOracle DB、HTTPSのURLが別途必要です。校内PCとローカルOracle XEのままでは校外から接続できません。

### テスト

```sh
# Run from the repository root
sh kakeibo/mvnw -f pom.xml -pl kakeibo -am verify
# Windows: .\kakeibo\mvnw.cmd -f pom.xml -pl kakeibo -am verify
```

テストは専用のインメモリ H2 を使用します。ログイン・登録失敗、CSRF、ホームから家計簿への遷移、支出のユーザー分離、ログアウトを検証します。

