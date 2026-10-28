# Pronto: Push Notification Decision Engine

Upstream services ask *"may I send template X to user Y?"*. Pronto runs the rules and answers **ALLOWED** or **BLOCKED**. On ALLOWED it returns the rendered message (`Hi {{user_name}}` becomes `Hi Aryant`) for the caller to deliver, and records it as sent.

- Max 3 marketing pushes per user per local day, no marketing between 22:00 and 08:00 in the user's timezone, and no repeat of a campaign within 24h.
- Transactional messages (order shipped, delivered) always pass. This is enforced in code, so no dashboard edit can break it.
- Rules live in the DB as sandboxed SpEL and are edited at runtime from `/dashboard`, with no deploy.
- Every attempt is audited with its rule trace, and every sent message is stored with its exact rendered text.

Design docs: [HLD](docs/HLD.md), [LLD](docs/LLD.md), [DB schema](docs/DB_SCHEMA.md), [coding guidelines](docs/CODING_GUIDELINES.md), [plan](docs/PLAN.md).

## Quick start

Requires Java 21 and Maven. No database to install: the default profile uses an H2 file DB in MySQL mode, and Flyway creates the schema plus sample data on first boot.

```bash
mvn spring-boot:run
```

| URL | What |
|---|---|
| http://localhost:8080/dashboard | Rules: list, create, edit, enable/disable, delete |
| http://localhost:8080/dashboard/templates | Templates with the variables callers must send, plus a create form |
| http://localhost:8080/demo | **Run scenario**: a 15-step scripted walk-through with expected vs actual for each step. Default profile only (`pronto.demo.enabled`). It assumes only the system rules are active: a custom rule that matches the demo user changes the outcomes |
| http://localhost:8080/h2-console | DB browser. JDBC URL `jdbc:h2:file:./data/pronto;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;LOCK_TIMEOUT=10000`, user `sa`, empty password |
| http://localhost:8080/health | Liveness plus a DB check |

- The H2 console is for local development only. It is enabled only in the default profile and accepts only localhost connections.
- Logs go to the console and to `logs/pronto.log` (rolling, 10MB × 7). Every line carries the request id. Send `X-Request-Id` (`[A-Za-z0-9._-]`, up to 64 chars) to set your own; anything else is replaced with a UUID.
- Data lives in `./data/`. Delete that folder to start fresh.

Sample data includes users `1` (Aryant, Asia/Kolkata) and `2` (Maya, America/New_York), and the templates `ORDER_SHIPPED`, `ORDER_DELIVERED`, `DIWALI_OFFER`, `FLASH_SALE`, `WEEKEND_DEALS` and `NEW_ARRIVALS`.

## API by example

Decide (BLOCKED is still a `200`; `message` is then `null`):

```bash
curl -s localhost:8080/api/v1/notifications/decide -H 'Content-Type: application/json' -d '{
  "userId": 1,
  "templateCode": "ORDER_SHIPPED",
  "idempotencyKey": "order-1001-shipped",
  "variables": {"order_id": "1001", "eta": "6 PM"}
}'
```

```json
{"decisionId":1,"userId":1,"templateCode":"ORDER_SHIPPED","decision":"ALLOWED","reason":"TRANSACTIONAL_BYPASS",
 "ruleId":null,"ruleName":null,"messageType":"TRANSACTIONAL","decidedAt":"2026-10-27T20:20:48.908800Z",
 "notificationId":1,"message":{"title":"Hi Aryant, your order is on the way",
 "body":"Order #1001 will reach you by 6 PM.","deepLink":"app://orders/1001"},"trace":[]}
```

- A missing variable returns `400 {"code":"MISSING_TEMPLATE_VARIABLE","missing":["order_id"]}`, and nothing is stored.
- Retrying with the same `idempotencyKey` returns the stored decision and never uses a second cap slot.

Create a rule (validated by a sandbox dry-run first; an invalid rule returns 400 `INVALID_RULE`):

```bash
curl -s localhost:8080/api/v1/rules -H 'Content-Type: application/json' -d '{
  "name": "SMALL_CART_BLOCK",
  "priority": 25,
  "expression": "variables[\"cartValue\"] != null and variables[\"cartValue\"] < 199",
  "action": "BLOCK",
  "enabled": true
}'
```

Check an expression without saving it:

```bash
curl -s localhost:8080/api/v1/rules/validate -H 'Content-Type: application/json' -d '{"expression": "localHour + 1"}'
# {"valid":false,"error":"expression must return a boolean, got Integer"}
```

Create a template (and a campaign, if it needs one):

```bash
curl -s localhost:8080/api/v1/campaigns -H 'Content-Type: application/json' \
  -d '{"code": "CART_26", "name": "Cart nudges"}'
curl -s localhost:8080/api/v1/templates -H 'Content-Type: application/json' -d '{
  "code": "CART_REMINDER",
  "type": "MARKETING",
  "campaignCode": "CART_26",
  "title": "Hi {{user_name}}, your cart is waiting",
  "body": "{{item_count}} items are still in your cart.",
  "deepLink": "app://cart"
}'
# response includes "requiredVariables": ["item_count"]
```

Other endpoints:

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/notifications/decisions?userId=1&limit=50` | Audit log, newest first |
| GET | `/api/v1/notifications/sent?userId=1&limit=50` | Sent messages, newest first |
| GET / PUT / DELETE | `/api/v1/rules/{id}` | PUT needs the current `version` (a stale one returns 409). System rules: expression, priority and description are editable; name, action and enabled are locked (409); they can't be deleted (409) |
| GET / POST | `/api/v1/users`, `/api/v1/campaigns`, `/api/v1/templates` | Catalog setup. Users need a `city` and an IANA region `timezone` such as `Asia/Kolkata` (`+05:30`, `UTC+5` and `IST` are rejected). Template `deepLink` must start with `app://` or `https://` |
| POST | `/api/v1/demo/run` | Runs the scripted scenario (default profile only; 404 when disabled) |
| GET | `/api/v1/demo/data` | All tables; decisions and notifications are capped at the last 100 |

Errors are always `{code, message, details?, missing?}`.

## Rule cookbook

Rules apply to MARKETING messages only. They are evaluated in ascending `priority`, and the first `true` decides (`BLOCK` or `ALLOW`). If no rule matches, the message is allowed. A rule that throws **blocks** (`RULE_ERROR`, stored in the audit log), so a broken rule can't cause spam. A database error is not a rule error: the request fails with 500 and nothing is stored. Seeded system rules (expression, priority and description are editable; name, action and enabled are locked; they can't be deleted):

| priority | name | expression |
|---|---|---|
| 10 | `QUIET_HOURS` | `localHour >= 22 or localHour < 8` |
| 20 | `DAILY_MARKETING_CAP` | `marketingSentToday >= 3` |
| 30 | `DUPLICATE_CAMPAIGN_24H` | `message.campaignCode != null and hoursSinceLastSameCampaign < 24` |

Facts available to an expression:

| Expression | Type | Meaning |
|---|---|---|
| `user.id`, `user.name`, `user.timezone`, `user.city`, `user.hubId`, `user.orderCount` | long / String / Integer / int | User profile |
| `message.type`, `message.templateCode`, `message.campaignCode` | String (`campaignCode` may be null) | The message being decided |
| `variables['key']` | any, may be null | The request's `variables` |
| `localHour`, `localDayOfWeek` | int 0-23, `MONDAY`..`SUNDAY` | Now, in the user's timezone |
| `marketingSentToday` | long | Marketing pushes sent since the user's local midnight (queried only if read) |
| `hoursSinceLastSameCampaign` | double | Hours since this campaign was last sent to the user; `1e9` if never or no campaign |
| `hoursSinceLastMarketing` | double | Hours since the last marketing push; `1e9` if never |

Recipes:

```text
ALLOW  priority 1   message.templateCode == 'WELCOME_OFFER' and user.orderCount == 0   welcome offer skips caps for new users
BLOCK  priority 5   {1,2}.contains(user.hubId) and localHour < 10    hubs 1 and 2 open late
BLOCK  priority 15  hoursSinceLastMarketing < 2                      at least 2h between marketing pushes
BLOCK  priority 25  variables['cartValue'] != null and variables['cartValue'] < 199
BLOCK  priority 25  localDayOfWeek == 'SUNDAY' and message.campaignCode == 'WEEKEND_DEALS'
```

**Null-guard optional variables.** SpEL treats `null < 199` as **true**, so always write `variables['x'] != null and ...`.

- `variables['tier'] == 'GOLD'` is already null-safe.
- `variables['name']?.length()` uses safe navigation.
- An unguarded `variables['name'].length() > 3` fails the dry-run and is rejected with 400.

**Sandbox.** Expressions run in a read-only `SimpleEvaluationContext`, so there is no `T(...)`, no `new`, no bean references and no assignment.

- Method calls go through an allowlist resolver: only `contains`, `containsKey`, `startsWith`, `endsWith`, `equals`, `equalsIgnoreCase`, `isEmpty`, `size`, `length`, `toLowerCase`, `toUpperCase` and `trim` resolve. Anything else (`repeat`, `replaceAll`, `matches()`, `split`, `getClass`) fails, both at validation and at runtime.
- The `matches` regex **operator** is rejected when the rule is parsed (no ReDoS).
- Expressions are capped at 1000 characters.

## Template variables

Write `{{name}}` in the title, body or deep link (`{{ name }}` with spaces also works; names match `[A-Za-z0-9_]+`).

| Variable | Source |
|---|---|
| `user_name`, `user_city`, `user_id` | The user record. A request can't override them |
| anything else | The request's `variables` map |

- Any placeholder without a value returns 400 `MISSING_TEMPLATE_VARIABLE`, before any rule runs.
- A rendered text longer than its column returns 400 `RENDERED_MESSAGE_TOO_LONG`. It is never silently truncated.
- In the deep link, each value is percent-encoded, so `1/../settings` stays one path segment (`1%2F..%2Fsettings`). Title and body are inserted as-is.
- Output is plain text. Pages display it with `textContent`.

## Production: the `mysql` profile

```bash
export MYSQL_URL='jdbc:mysql://db-host:3306/pronto?serverTimezone=UTC'
export MYSQL_USER=pronto
export MYSQL_PASSWORD=...        # from your secret store; there are no defaults
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
# or: java -jar target/pronto-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

- Flyway applies the schema and the system rules only. Sample users and templates are not loaded, so create them through the API.
- The demo is off (`pronto.demo.enabled: false`): `/api/v1/demo/*` returns 404 and `/demo` says it is disabled.
- The H2 console is off.
- Timestamps are stored as UTC `DATETIME(6)`.
- Verified against a local MySQL 9.4 (the SQL is MySQL 8 compatible):
  - Flyway migration and Hibernate `validate` pass, and the 3 system rules are seeded.
  - Transactional bypass, campaign dedupe, idempotent replay and a custom rule all behave as expected.
  - A full-size `variables` row inserts fine.
  - 10 parallel decides for one user give exactly 3 ALLOWED (InnoDB row lock).

## Running tests

```bash
mvn verify
```

This runs the unit and integration tests against an in-memory H2 (no MySQL needed). It also runs Checkstyle's `MethodLength` check (60 lines max), which fails the build on a violation. The concurrency tests use real committed transactions across 10 threads.

## Add when needed (not built)

- Cap and dedupe counters → Redis `INCR`/`EXPIRE`, once index scans on `notifications` become the bottleneck.
- `notification_decisions` → monthly partitions or an archive, once it gets large.
- Dashboard auth → keep it behind SSO/ingress for now; add Spring Security once it is exposed.
- Event-driven template selection, a delivery adapter (FCM) with delivery status, and per-user rule overrides → when a real consumer asks.
