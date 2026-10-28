# Demo & Test Guide

A hands-on walkthrough (~15 min). You'll see every feature in the browser, exercise each rule with `curl`, and check with SQL exactly which table rows each action writes.

| Part | What you'll see |
|---|---|
| [0. Start](#0-start) | app running, all URLs, test users for the current time of day |
| [1. `/demo`](#1-demo-everything-at-once) | the whole flow at once: 15 scripted steps with expected vs actual, plus every table |
| [2. Rules dashboard](#2-rules-dashboard) | list rules, mark custom rules active/inactive, add a new rule, edit a system rule |
| [3. Templates](#3-templates) | see templates, create one with `{{variables}}` |
| [4. curl walkthrough](#4-curl-walkthrough-every-rule) | quiet hours, daily cap, campaign dedupe, transactional bypass, idempotency, missing variable |
| [5. SQL](#5-sql-where-each-entry-lands) | the queries that show where each entry lands (H2 console or MySQL) |
| [6. Logs](#6-logs) | one request traced through the log file |
| [7. How it's wired](#7-how-its-wired) | request → classes → tables |

---

## 0. Start

Requires Java 21 and Maven. No database to install.

```bash
mvn spring-boot:run
```

Wait for `Started ProntoApplication`.

| URL | What |
|---|---|
| http://localhost:8080/demo | scripted scenario + all tables |
| http://localhost:8080/dashboard | rules list (enable/disable, edit, delete) |
| http://localhost:8080/dashboard/new | add a rule |
| http://localhost:8080/dashboard/templates | templates list + create |
| http://localhost:8080/h2-console | SQL console (see [part 5](#5-sql-where-each-entry-lands)) |
| http://localhost:8080/health | `{"status":"UP","database":"UP"}` |

On first boot, Flyway creates the schema and seeds:

- **3 system rules:** `QUIET_HOURS`, `DAILY_MARKETING_CAP`, `DUPLICATE_CAMPAIGN_24H`
- **2 users:** id 1 Aryant (Asia/Kolkata), id 2 Maya (America/New_York)
- **3 campaigns**
- **6 templates**

To start over from a clean database at any point, stop the app, delete the data and logs, and start it again:

```bash
rm -rf data logs
```

### 0b. Test users for the current time of day

Quiet hours (22:00–08:00) use the **real clock in each user's timezone**, so a seeded user may be in quiet hours right now. Create users in timezones that are day and night for you *now*. Run this in the terminal you'll use for parts 2–4.

First, print the current hour in a few timezones:

```bash
for z in Pacific/Honolulu America/Los_Angeles America/New_York Europe/London Asia/Kolkata Asia/Tokyo Australia/Sydney; do echo "$(TZ=$z date +%H) $z"; done
```

Pick a **DAY** zone (hour 09–20) and a **NIGHT** zone (hour 22–07) from that output. Put them in the export below; the zones shown are only placeholders:

```bash
export DAY_ZONE=Asia/Kolkata NIGHT_ZONE=Europe/London
```

Create three users:

```bash
export UI_USER=$(curl -s -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' -d "{\"name\":\"Uma\",\"timezone\":\"$DAY_ZONE\",\"city\":\"Mumbai\"}" | sed -E 's/.*"id":([0-9]+).*/\1/')
```

```bash
export DAY_USER=$(curl -s -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' -d "{\"name\":\"Dee\",\"timezone\":\"$DAY_ZONE\",\"city\":\"Pune\",\"hubId\":3}" | sed -E 's/.*"id":([0-9]+).*/\1/')
```

```bash
export NIGHT_USER=$(curl -s -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' -d "{\"name\":\"Nyx\",\"timezone\":\"$NIGHT_ZONE\",\"city\":\"Delhi\"}" | sed -E 's/.*"id":([0-9]+).*/\1/')
```

```bash
echo "ui=$UI_USER day=$DAY_USER night=$NIGHT_USER"
```

- `UI_USER` (Uma) is for parts 2 and 3.
- `DAY_USER` (Dee) and `NIGHT_USER` (Nyx) are for part 4. Dee is kept separate so the daily-cap count in part 4 starts at zero.

Last, define a helper that prints one line per decision:

```bash
decide() { curl -s -X POST localhost:8080/api/v1/notifications/decide -H 'Content-Type: application/json' -d "$1" | sed -E 's/.*"decision":"([A-Z]+)","reason":"([^"]*)".*/\1  \2/'; echo; }
```

---

## 1. `/demo`: everything at once

1. Open http://localhost:8080/demo.
2. Click **Run scenario**.

It creates a fresh user (Asia/Kolkata, Bengaluru) and replays two days of notifications through the **real** decision service at simulated times. Each row shows these columns:

| Column | Meaning |
|---|---|
| Local time | the simulated time in the user's timezone |
| Action | the template sent, or the setup step (create user, create or delete rule) |
| Expected / Actual / Result | what the step should do, what happened, and pass/fail |
| Rendered message | the push text after `{{variables}}` are filled. Empty when blocked |
| Rows | the DB row ids created: `decision #`, `notification #`, or `row #` (the user or rule row the step touched) |

What the 15 steps prove:

| Step | Proves |
|---|---|
| 2 | a transactional message at 03:00 passes, ignoring quiet hours |
| 3 | a missing template variable → 400, nothing stored |
| 4, 6, 7 | the first 3 marketing pushes of the day are allowed |
| 5 | the same campaign within 24h is blocked |
| 8 | the 4th marketing push of the day is blocked |
| 9 | transactional messages ignore the cap |
| 10 | marketing at 23:30 is blocked by quiet hours |
| 11–12 | a rule created at runtime (the same code path as the dashboard) blocks immediately |
| 13–14 | dedupe resets after 24h, and the cap resets at the user's local midnight |
| 15 | the runtime rule is deleted again |

Below the steps, the page dumps every table: users, campaigns, templates, rules, decisions (last 100) and notifications (last 100). Click **Run scenario** again: it creates a new user and passes again.

> The demo assumes the 3 system rules as seeded and no other active rules. Two things make some steps differ: a custom rule that matches the demo user (disable it first), or an edited system rule (e.g. the 48h window from 2d makes step 14 fail; set it back to 24).

---

## 2. Rules dashboard

### 2a. See the rules: http://localhost:8080/dashboard

| Column | Notes |
|---|---|
| Priority | lower runs first; the first matching rule decides |
| Name | system rules carry a **system** badge |
| Expression | the SpEL condition |
| Action | `BLOCK` or `ALLOW` |
| Enabled | checkbox = active/inactive. **Greyed out for system rules**: they can't be switched off (that would silently drop "max 3 per day") |
| Edit / Delete | Delete is only shown for custom rules |

### 2b. Add a new rule: http://localhost:8080/dashboard/new

Click **New rule** (or open the URL) and fill in:

| Field | Value |
|---|---|
| Name | `SMALL_CART` |
| Priority (lower runs first) | `5` |
| Action when it matches | `BLOCK` |
| Description | `No promos when the cart is under 199` |
| Expression | `variables['cartValue'] != null and variables['cartValue'] < 199` |

1. Click **Validate**. You should see *Valid: compiles and returns a boolean.*
2. Click **Save**. You land back on `/dashboard`, and `SMALL_CART` is first in the list (priority 5), enabled.

The facts panel on the right lists everything an expression can read: `user.*`, `message.*`, `variables['...']`, `localHour`, `marketingSentToday` and so on.

Next, see what the sandbox rejects. Each of these expressions shows an inline error on **Validate**, and **Save** refuses it too:

| Expression | Why it fails |
|---|---|
| `T(java.lang.Runtime).getRuntime().exec('id') != null` | type references are blocked |
| `''.getClass() != null` | the method is not on the allowlist |
| `variables['x'] matches 'a+'` | the regex operator is not allowed |
| `localHour + 1` | not a boolean |
| `variables['name'].length() > 3` | not null-guarded (fails the dry run) |

### 2c. See the rule take effect, then mark it inactive

This uses `UI_USER` from [0b](#0b-test-users-for-the-current-time-of-day), whose timezone is in daytime, so quiet hours don't interfere.

```bash
curl -s -X POST localhost:8080/api/v1/notifications/decide -H 'Content-Type: application/json' -d "{\"userId\":$UI_USER,\"templateCode\":\"NEW_ARRIVALS\",\"variables\":{\"cartValue\":150}}"
```

Result: `"decision":"BLOCKED"`, `"ruleName":"SMALL_CART"`. The `trace` shows that only `SMALL_CART` was evaluated, since priority 5 runs first and the first match wins.

Now, on `/dashboard`, **untick Enabled** for `SMALL_CART`. The row greys out. Run the same curl again: `"decision":"ALLOWED"`, `"reason":"DEFAULT_ALLOW"`, with a `message` and a `notificationId`. Tick it again to reactivate it, or click **Delete** to remove it.

### 2d. Edit a system rule

On `/dashboard`, click **Edit** on `DUPLICATE_CAMPAIGN_24H`. Name, action and enabled are locked. Change the expression to `message.campaignCode != null and hoursSinceLastSameCampaign < 48` and save. The dedupe window is now 48h, with no restart. Change it back to `24` when done.

---

## 3. Templates

Open http://localhost:8080/dashboard/templates. Each row lists the code, type, campaign, title, body and deep link. **Caller sends** lists the variables a caller must supply. `user_name`, `user_city` and `user_id` are filled from the DB and never sent.

Create one with the **New template** form:

| Field | Value |
|---|---|
| Code | `CART_REMINDER` |
| Type | `MARKETING` |
| Campaign (dedupe key) | `none` |
| Title | `Hi {{user_name}}, you left {{item_count}} items` |
| Body | `They're still waiting in your {{user_city}} cart.` |
| Deep link | `app://cart/{{cart_id}}` |

Click **Create template**. It appears in the list with **Caller sends** = `item_count, cart_id`.

Use it:

```bash
curl -s -X POST localhost:8080/api/v1/notifications/decide -H 'Content-Type: application/json' -d "{\"userId\":$UI_USER,\"templateCode\":\"CART_REMINDER\",\"variables\":{\"item_count\":3,\"cart_id\":\"c-9\"}}"
```

Expect:

- `message.title` = `Hi Uma, you left 3 items`
- `message.body` = `They're still waiting in your Mumbai cart.`
- `message.deepLink` = `app://cart/c-9`

`CART_REMINDER` is a marketing template, so quiet hours and the cap still apply. Deep links must start with `app://` or `https://`; `javascript:` is rejected with a 400.

---

## 4. curl walkthrough: every rule

### 4a. Setup

This part uses `DAY_USER`, `NIGHT_USER` and the `decide` helper from [0b](#0b-test-users-for-the-current-time-of-day). In a new terminal, rerun 0b first. Shell variables don't carry over, and rerunning creates fresh users, so the daily cap starts at zero.

### 4b. Quiet hours and the transactional bypass (NIGHT user)

```bash
decide "{\"userId\":$NIGHT_USER,\"templateCode\":\"NEW_ARRIVALS\"}"
```

Expect `BLOCKED  QUIET_HOURS`.

```bash
decide "{\"userId\":$NIGHT_USER,\"templateCode\":\"ORDER_SHIPPED\",\"variables\":{\"order_id\":\"A1\",\"eta\":\"9am\"}}"
```

Expect `ALLOWED  TRANSACTIONAL_BYPASS`. Order updates always pass.

### 4c. Campaign dedupe (DAY user)

```bash
decide "{\"userId\":$DAY_USER,\"templateCode\":\"DIWALI_OFFER\",\"variables\":{\"discount\":\"40%\"}}"
```

Expect `ALLOWED  DEFAULT_ALLOW` (1 of 3).

```bash
decide "{\"userId\":$DAY_USER,\"templateCode\":\"DIWALI_OFFER\",\"variables\":{\"discount\":\"50%\"}}"
```

Expect `BLOCKED  DUPLICATE_CAMPAIGN_24H`.

### 4d. Daily cap (DAY user)

```bash
decide "{\"userId\":$DAY_USER,\"templateCode\":\"WEEKEND_DEALS\"}"
```

Expect `ALLOWED` (2 of 3).

```bash
decide "{\"userId\":$DAY_USER,\"templateCode\":\"FLASH_SALE\",\"variables\":{\"ends_at\":\"6pm\"}}"
```

Expect `ALLOWED` (3 of 3).

```bash
decide "{\"userId\":$DAY_USER,\"templateCode\":\"NEW_ARRIVALS\"}"
```

Expect `BLOCKED  DAILY_MARKETING_CAP`.

```bash
decide "{\"userId\":$DAY_USER,\"templateCode\":\"ORDER_DELIVERED\",\"variables\":{\"order_id\":\"A2\"}}"
```

Expect `ALLOWED  TRANSACTIONAL_BYPASS`. The cap never applies to transactional messages.

### 4e. Missing variable (nothing is stored)

```bash
curl -s -w '\nHTTP %{http_code}\n' -X POST localhost:8080/api/v1/notifications/decide -H 'Content-Type: application/json' -d "{\"userId\":$DAY_USER,\"templateCode\":\"ORDER_SHIPPED\"}"
```

Expect HTTP 400, `{"code":"MISSING_TEMPLATE_VARIABLE",...,"missing":["order_id","eta"]}`.

### 4f. Idempotent retry

Run this twice:

```bash
curl -s -X POST localhost:8080/api/v1/notifications/decide -H 'Content-Type: application/json' -d "{\"userId\":$NIGHT_USER,\"templateCode\":\"ORDER_DELIVERED\",\"idempotencyKey\":\"retry-1\",\"variables\":{\"order_id\":\"A3\"}}"
```

Both responses carry the same `decisionId` and `notificationId`. The retry stores nothing new.

### 4g. Read the history back

```bash
curl -s "localhost:8080/api/v1/notifications/decisions?userId=$DAY_USER"
```

This is every attempt, newest first, with the rule trace.

```bash
curl -s "localhost:8080/api/v1/notifications/sent?userId=$DAY_USER"
```

This is only what was sent, with the rendered text. Expect 4 rows: 3 marketing and 1 transactional.

---

## 5. SQL: where each entry lands

### Which action writes which table

| Action | Table: rows written |
|---|---|
| any `decide` call (allowed **or** blocked) | `notification_decisions`: 1 row (verdict, reason, deciding rule, variables, per-rule trace) |
| `decide` → ALLOWED | `notifications`: 1 more row (the exact rendered title, body and deep link) |
| `decide` → 400 (missing variable) or 404 (unknown user/template) | nothing |
| idempotent retry | nothing (returns the stored row) |
| dashboard **Save** / toggle / **Delete** | `rules` (insert / update with `version` + 1 / delete) |
| **Create template** | `message_templates` |
| `POST /api/v1/users` | `users` |

### Connect

- **H2 (default profile).** While the app runs, open http://localhost:8080/h2-console with:
  - JDBC URL: `jdbc:h2:file:./data/pronto;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;LOCK_TIMEOUT=10000`
  - User: `sa`
  - Password: empty

  Then click **Connect**.
- **MySQL (`mysql` profile).** Run `mysql -u <user> -p <database>`, e.g. `mysql -u root -p pronto`.

Every query below runs unchanged on both.

### Queries

```sql
-- 1. Rules in evaluation order (watch `version` go up when you edit or toggle on the dashboard)
SELECT id, priority, name, action, enabled, system_rule, version, expression
FROM rules ORDER BY priority, id;

-- 2. Templates with their campaign (the dedupe key)
SELECT t.id, t.code, t.type, c.code AS campaign, t.title, t.body, t.deep_link
FROM message_templates t LEFT JOIN campaigns c ON c.id = t.campaign_id
ORDER BY t.id;

-- 3. Users
SELECT id, name, timezone, city, hub_id, order_count FROM users ORDER BY id;

-- 4. Audit log: every decide attempt, newest first
SELECT id, user_id, message_type, decision, rule_name, reason, decided_at
FROM notification_decisions ORDER BY id DESC LIMIT 20;

-- 5. Sent notifications: what users actually received
SELECT id, decision_id, user_id, message_type, title, body, deep_link, sent_at
FROM notifications ORDER BY id DESC LIMIT 20;

-- 6. Each attempt next to what was sent (NULL title = blocked)
SELECT d.id AS decision_id, u.name, t.code AS template, d.decision, d.rule_name, n.id AS notification_id, n.title
FROM notification_decisions d
JOIN users u ON u.id = d.user_id
JOIN message_templates t ON t.id = d.template_id
LEFT JOIN notifications n ON n.decision_id = d.id
ORDER BY d.id DESC LIMIT 20;

-- 7. Which rule blocks the most
SELECT rule_name, COUNT(*) AS blocked
FROM notification_decisions WHERE decision = 'BLOCKED'
GROUP BY rule_name ORDER BY blocked DESC;

-- 8. Cap input: marketing sent per user per UTC day
--    (the engine counts from the user's LOCAL midnight, so for non-UTC users the boundary shifts)
SELECT user_id, CAST(sent_at AS DATE) AS utc_day, COUNT(*) AS marketing_sent
FROM notifications
WHERE message_type = 'MARKETING'
GROUP BY user_id, CAST(sent_at AS DATE)
ORDER BY utc_day DESC, user_id;

-- 9. Dedupe input: last send per user per campaign
SELECT n.user_id, c.code AS campaign, MAX(n.sent_at) AS last_sent
FROM notifications n JOIN campaigns c ON c.id = n.campaign_id
GROUP BY n.user_id, c.code ORDER BY last_sent DESC;

-- 10. Why was the latest decision made? Variables + per-rule trace (JSON). Swap in WHERE id = <n> for a specific one
SELECT id, decision, reason, variables, trace FROM notification_decisions ORDER BY id DESC LIMIT 1;

-- 11. Idempotency keys in use
SELECT id, idempotency_key, user_id, decision FROM notification_decisions
WHERE idempotency_key IS NOT NULL ORDER BY id DESC;

-- 12. Schema migrations applied by Flyway
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

Timestamps are stored in **UTC**.

---

## 6. Logs

Every line goes to `logs/pronto.log` and the console. Each line carries the request id, which you can set yourself:

```bash
curl -s -X POST localhost:8080/api/v1/notifications/decide -H 'Content-Type: application/json' -H 'X-Request-Id: trace-me-1' -d '{"userId":2,"templateCode":"ORDER_DELIVERED","variables":{"order_id":"Z9"}}' -o /dev/null -D - | grep -i x-request-id
```

```bash
grep trace-me-1 logs/pronto.log
```

You'll see a line like this:

```
INFO [trace-me-1] ... DecisionService : decision userId=2 template=ORDER_DELIVERED result=ALLOWED rule=null decisionId=.. notificationId=..
```

Rule changes are logged too (`rule created id=.. name=..`, `rule updated ..`). If you don't send `X-Request-Id`, a UUID is generated and echoed back in the response header.

---

## 7. How it's wired

```mermaid
flowchart TB
  subgraph Callers
    SVC[Upstream service]
    UI["Dashboard pages"]
    DEMO["Demo page"]
  end
  SVC -->|POST /api/v1/notifications/decide| NC[NotificationController]
  UI -->|/api/v1/rules, /api/v1/templates| RC[RuleController / CatalogController]
  DEMO -->|POST /api/v1/demo/run| DC[DemoController]
  RID[RequestIdFilter<br/>MDC requestId] -.-> NC & RC & DC
  NC --> DS
  RC --> RS[RuleService / CatalogService]
  DC --> DMS[DemoService] --> DS & RS
  DS[DecisionService<br/>@Transactional] -->|1 lock user row| UR[(users)]
  DS -->|2 idempotency replay?| NDR[(notification_decisions)]
  DS -->|3 render| TR[TemplateRenderer]
  DS -->|4 MARKETING only: enabled rules by priority| RR[(rules)]
  DS -->|5 evaluate| RE[RuleEngine]
  RE --> EC[ExpressionCompiler<br/>SpEL sandbox + cache]
  RE --> NF[NotificationFacts<br/>lazy: sent today, last campaign]
  NF -->|count / max sent_at| NR[(notifications)]
  DS -->|6 write audit row| NDR
  DS -->|7 if ALLOWED write sent row| NR
  RS -->|validate = compile + dry run| EC
  RS --> RR
```

1. The **upstream service** calls `POST /decide` with `{userId, templateCode, variables, idempotencyKey}`.
2. **`DecisionService.decide`** runs in one transaction:
   - Locks the user's row (`SELECT … FOR UPDATE`), so parallel requests for that user queue up. It reads the clock **after** the lock.
   - If the `idempotencyKey` was seen before, it returns the stored decision.
   - **`TemplateRenderer`** fills `{{variables}}` (`user_*` from the DB, the rest from the request). A missing one → 400, nothing written.
   - A `TRANSACTIONAL` message → ALLOWED immediately (`TRANSACTIONAL_BYPASS`). Rules are never consulted.
   - A `MARKETING` message → `DecisionService` loads the enabled rules by priority, and **`RuleEngine`** evaluates each through **`ExpressionCompiler`** (sandboxed SpEL) against **`NotificationFacts`**. Facts that need the DB (`marketingSentToday`, `hoursSinceLast…`) are queried from `notifications` only if a rule reads them.
   - The first matching rule decides. No match → ALLOWED. A rule error → BLOCKED (fail-closed).
   - One row goes into `notification_decisions`. If ALLOWED, a second row goes into `notifications`. The response carries the decision, the trace and the rendered `message`.
3. **Dashboard** → `RuleService` validates every create/edit (compile + dry run) and enforces the system-rule locks and the optimistic `version`, then writes `rules`. The **next** `decide` reads the new rule set; there is no cache to flush.
4. **`/demo`** → `DemoService` drives the same `DecisionService` and `RuleService` with simulated timestamps. Every demo row is a real DB row.
