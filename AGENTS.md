# AGENTS.md

## Project

Tourism / 家計簿 (kakeibo) expense-tracking app for 沖縄みらいAI＆IT専門学校.
Stack: Spring Boot 4.1.2 (Java 25) + Spring Data JPA + Thymeleaf + Oracle DB.
UI text and domain terms are Japanese (categories: 食事 / 交通 / 宿泊 / 観光 / その他).

## Commands

The Maven module lives in `kakeibo/` — run all Maven commands from there, not the repo root.

- Run app: `cd kakeibo && ./mvnw spring-boot:run`
- Run tests: `cd kakeibo && ./mvnw test`
- Single test: `cd kakeibo && ./mvnw test -Dtest=KakeiboApplicationTests`
- Package: `cd kakeibo && ./mvnw package`

Maven Wrapper included (mvnw / mvnw.cmd; wrapper 3.3.4 -> Maven 3.9.16). Java 25 required.

## Workflow (Git / PR)

- Commit messages are written in Japanese and describe what changed (the message doubles as the PR summary).
- Never commit or push directly to `main`, and never merge a branch into `main`. The repository owner makes
  every merge decision on GitHub.
- Do the work on a `feature/*` branch, commit and push that branch, then stop. If a PR is needed, draft the
  PR title and body in Japanese and leave creating / reviewing / merging it to the owner.
- `gh` CLI is not installed in this environment, so `gh pr create` is not available.

## Current state

- Single-module Spring Boot app, package `com.example.kakeibo`, entry class `KakeiboApplication`.
- Implemented screens: splash / language / login / register / home, 支出 tracker (`/expenses`:
  list, form, graph, receipt capture), 為替 (`/currency`), spots (`/features/spots`, `/features/spots/{id}`)
  and `/mypage`. Shared Thymeleaf layout lives in `templates/fragments/common.html` + `static/css/app.css`.
- No REST controllers yet; no delete endpoint (design doc allows SELECT/INSERT/UPDATE only).

## Gotchas

- DB: local Oracle XE at `//localhost:1521/XEPDB1`, user `vita_app_user`. Password is only in
  `kakeibo/src/main/resources/application-local.properties`, which is gitignored — never commit it.
- `spring.jpa.hibernate.ddl-auto=validate` in `application.properties`. Per the comment there, switch to
  `update` on your own PC when entity changes require table creation; use `validate` for a shared DB.
- SQL scripts live in `db/` (Oracle-flavored): `create_tables.sql`, `insert_data.sql`, `insert_expenses.sql`,
  `queries_expenses.sql`. Demo device/user id used: `demo-device-001`.
- Design decisions encoded in the entity that differ from plain JPA defaults: entity `id` is `Integer`
  (design doc says INTEGER — `Long` fails `ddl-auto=validate`); `originalCurrency` defaults to `"JPY"`;
  `createdAt` is DB-managed (`insertable = false, updatable = false`).

## Testing

- `KakeiboApplicationTests.contextLoads` (`@SpringBootTest`) loads the app against the local Oracle DB
  configured in `application.properties`, so Oracle XE must be running.
- `LoginExpenseIntegrationTests` runs on in-memory H2 (`src/test/resources/application.properties`,
  `ddl-auto=create-drop`) and covers registration / login, CSRF, expense isolation, receipt upload,
  currency conversion and the profile screens.
