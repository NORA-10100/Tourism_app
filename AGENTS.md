# AGENTS.md

## Project

Tourism / 家計簿 (kakeibo) expense-tracking app for 沖縄みらいAI＆IT専門学校.
Stack: Spring Boot 4.1.1 (Java 17) + Spring Data JPA + Thymeleaf + Oracle DB.
UI text and domain terms are Japanese (categories: 食事 / 交通 / 宿泊 / 観光 / その他).

## Commands

The Maven module lives in `kakeibo/` — run all Maven commands from there, not the repo root.

- Run app: `cd kakeibo && ./mvnw spring-boot:run`
- Run tests: `cd kakeibo && ./mvnw test`
- Single test: `cd kakeibo && ./mvnw test -Dtest=KakeiboApplicationTests`
- Package: `cd kakeibo && ./mvnw package`

Maven wrapper included (mvnw / mvnw.cmd; wrapper 3.3.4 -> Maven 3.9.16). Java 17 required.

## Current state

- Single-module Spring Boot app, package `com.example.kakeibo`, entry class `KakeiboApplication`.
- Feature in progress: 支出 (expense) tracker — `Expense` entity, `ExpenseRepository`, `ExpenseController`
  (`@RequestMapping("/expenses")`), Thymeleaf views in `src/main/resources/templates/expenses/{List,Form}.html`.
- No REST controllers yet; no delete endpoint (design doc allows SELECT/INSERT/UPDATE only).

## Gotchas

- `Hello.java` at repo root is a scratch program, not part of the Maven build.
- `kakeibo/bin/` is committed Eclipse build output (stale copies of pom/mvnw/sources); do not edit it.
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

- Only `KakeiboApplicationTests.contextLoads` exists (`@SpringBootTest`).
- Context load runs Hibernate validation against the DB, so the local Oracle XE instance must be running;
  no H2 or testcontainers configured.
