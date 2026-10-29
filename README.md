アプリ開発# ---
沖縄みらいAI＆IT専門学校が観光産業の課題解決に繋がるユーザアプリを制作するためのリポジトリ

## ホーム・ログイン・家計簿の統合アプリ

実行する Maven プロジェクトは `kakeibo` です。ZIP 内のホーム・ログイン・新規登録・言語選択の画面をこのアプリへ統合しています。
添付に含まれていた別の教材用 `shop` プロジェクト、ビルド済みファイル、PC 固有の DB 設定は取り込んでいません。

### Eclipse / VS Code

JDK 17 以上を使用してください。Eclipse では「既存 Maven プロジェクトのインポート」で `kakeibo/pom.xml` を選択します。
VS Code では `kakeibo` を開き、Java 拡張機能を使用します。
起動クラスは `com.example.kakeibo.KakeiboApplication` です。

ローカル開発では起動時の環境変数 `SPRING_PROFILES_ACTIVE=cloud` を設定します。
H2 のデータは `kakeibo` を作業ディレクトリにした場合 `kakeibo/data/` に保存されます。認証情報は Git に登録しないでください。

```sh
cd kakeibo
# Linux / macOS
SPRING_PROFILES_ACTIVE=cloud sh mvnw spring-boot:run
# Windows PowerShell
# $env:SPRING_PROFILES_ACTIVE="cloud"
# .\mvnw.cmd spring-boot:run
```

ブラウザで `http://localhost:8080/` を開きます。起動画面 → 言語選択 → 新規登録 → ログイン → ホームと進めます。
ホームの「レシート家計簿」は支出一覧へ移動します。スポット・多言語・リアルタイム・設定は元の仮画面です。
言語選択は保存のみで、画面全体の翻訳は未実装です。

### DB とログイン

新規登録は既存設計の `users` テーブル（`username`, `email`, `password_hash`）を使います。パスワードは BCrypt で保存します。
ログイン後の支出識別子は `user-<users.id>` です。以前の `dev-device-001` や `demo-device-001` の支出は自動移行しません。
他人の支出の表示・変更を防ぎ、フォーム送信には CSRF 検証を行います。

Oracle を使用する場合は `cloud` プロファイルを指定せず、既存の `db/create_tables.sql` を適用した DB を用意します。
`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` を安全な環境変数等で設定してください。
ZIP 側の `app_users` テーブルは不要です。Oracle 接続はこの統合作業では未検証です。

### テスト

```sh
cd kakeibo
sh mvnw verify
# Windows: .\mvnw.cmd verify
```

テストは専用のインメモリ H2 を使用します。ログイン・登録失敗、CSRF、ホームから家計簿への遷移、支出のユーザー分離、ログアウトを検証します。
