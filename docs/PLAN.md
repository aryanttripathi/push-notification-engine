# Phase Plan

Roles (chief-of-staff pattern):
- **Chief**: owns the design docs and orchestration, runs the final clone-and-run check, pushes.
- **Coder**: implements the phases, following [CODING_GUIDELINES.md](CODING_GUIDELINES.md).
- **Reviewer**: read-only. Checks correctness, security and the guidelines, and reports to the chief.
- **Tester**: end-to-end. Boots the jar on its own port and its own DB and drives every endpoint.

Acceptance bar on a fresh clone with no DB installed: `mvn verify` passes, and `mvn spring-boot:run` serves `/dashboard` and `/demo` on `:8080` and writes `logs/pronto.log`.

| Phase | Scope | Gate |
|---|---|---|
| **0 Cleanup** | Delete the `SampleRecord` entity, repo and test. Strip the hardcoded MySQL password. Default profile = H2 file `./data/pronto;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE`. `mysql` profile reads env vars only. Add Flyway (`flyway-core`, `flyway-mysql`), H2, `spring-boot-starter-validation`, Checkstyle `MethodLength` 60. `ddl-auto: validate`. Log file config. `.gitignore`: `data/`, `logs/` | `mvn verify` green with no MySQL |
| **1 Schema** | V1/V2/V3 scripts, entities, repositories per DB_SCHEMA.md; `ClockConfig`; `RequestIdFilter` | boots; Flyway applies; Hibernate validate passes |
| **2 Engine + decide API** | `ExpressionCompiler`, `NotificationFacts`, `RuleEngine`, `TemplateRenderer`, `DecisionService`, `NotificationController`, `GlobalExceptionHandler` | tests below green |
| **3 Rules/catalog API + dashboard** | `RuleService`, `RuleController`, `CatalogService/Controller`, `/dashboard`, `/dashboard/new`, `/dashboard/templates` | tests below green; UI create/edit/toggle/delete works |
| **4 Demo + README** | `DemoService`, `DemoController`, `/demo`; README (run, API curl examples, rule cookbook, template variables, prod MySQL, "add when" list) | every `/demo` step has expected == actual; re-runnable |
| **5 Verify + ship** | reviewer pass → coder fixes → tester E2E (H2 + MySQL profile against local MySQL) → chief clone-and-run → push public repo `aryanttripathi/<name>` | all green |

## Required tests

**Templates**
- `Hi {{user_name}}` → `Hi Aryant`; `{{ order_id }}` with spaces works; deep link rendered
- Missing `order_id` → 400 `MISSING_TEMPLATE_VARIABLE` listing `order_id`; no decision or notification rows
- A request variable `user_name` can't override the DB user name
- Rendered output over the column limit → 400

**Engine / decision**
- Quiet hours (Asia/Kolkata): 21:59 allowed, 22:00 blocked, 07:59 blocked, 08:00 allowed
- Cap: 3 sent, then the 4th blocked; blocked attempts don't count
- Cap follows the user's local day: an America/New_York user at local 23:00 and next-day 01:00 (same UTC day) → reset
- Dedupe: same campaign 23h later blocked, 25h later allowed; a different campaign is not blocked
- Transactional at 03:00 with the cap used up → ALLOWED; still ALLOWED after adding a priority-0 BLOCK `true` rule
- An ALLOW rule at higher priority beats quiet hours
- A rule that throws at runtime (`variables['missing'].length() > 3`, inserted via repository to skip validation) → BLOCKED `RULE_ERROR` (fail-closed)
- `variables['cartValue'] != null and variables['cartValue'] < 199` does not match when cartValue is absent
- ALLOWED → one `notification_decisions` row + one `notifications` row with the rendered text; BLOCKED → decision row only
- Idempotency: the same key twice → same `decisionId`, one row in each table
- Concurrency: 10 parallel marketing decides for one user (no campaign) → exactly 3 ALLOWED, 3 `notifications` rows

**Rule API**
- `T(java.lang.Runtime).getRuntime().exec('id')`, `new java.io.File('x')`, `''.getClass()` → rejected 400
- Non-boolean (`localHour + 1`) → 400; a syntax error → 400 with a message; an unguarded `variables['x'].length() > 1` → 400
- DELETE on a system rule → 409; editing a system rule (`< 24` → `< 48`) takes effect on the next decision
- A disabled rule is skipped
