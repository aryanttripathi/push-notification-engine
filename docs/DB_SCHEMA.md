# DB Schema

One Flyway script set runs on both **H2 (MODE=MySQL)** and **MySQL 8**. Timestamps are UTC `DATETIME(6)`, mapped to `java.time.Instant` (`hibernate.jdbc.time_zone=UTC`).

```mermaid
erDiagram
  users ||--o{ notification_decisions : "asked for"
  users ||--o{ notifications : receives
  campaigns ||--o{ message_templates : groups
  message_templates ||--o{ notification_decisions : uses
  message_templates ||--o{ notifications : "rendered from"
  notification_decisions ||--o| notifications : "ALLOWED produces"
  rules ||--o{ notification_decisions : "decided by (soft ref)"

  users { bigint id PK; varchar name; varchar timezone; varchar city; bigint hub_id; int order_count }
  campaigns { bigint id PK; varchar code UK; varchar name; varchar city; boolean active }
  message_templates { bigint id PK; varchar code UK; varchar type; bigint campaign_id FK; varchar title; varchar body; varchar deep_link }
  rules { bigint id PK; varchar name UK; int priority; varchar expression; varchar action; boolean enabled; boolean system_rule; bigint version }
  notification_decisions { bigint id PK; varchar idempotency_key UK; bigint user_id FK; bigint template_id FK; varchar decision; bigint rule_id; varchar reason; varchar trace; datetime decided_at }
  notifications { bigint id PK; bigint decision_id FK_UK; bigint user_id FK; bigint template_id FK; bigint campaign_id FK; varchar message_type; varchar title; varchar body; varchar deep_link; datetime sent_at }
```

| Table | Holds | Written when |
|---|---|---|
| `notification_decisions` | **every** decide call: the verdict, reason, rule trace, variables | each call (ALLOWED and BLOCKED) |
| `notifications` | **sent** messages: the exact rendered text the user receives | ALLOWED only (1:1 with its decision) |

Caps and dedupe count `notifications`. Blocked attempts never reached the user.

## DDL (V1__init.sql)

Use table-level `FOREIGN KEY` clauses; MySQL silently ignores column-level `REFERENCES`.

```sql
CREATE TABLE users (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  timezone    VARCHAR(64)  NOT NULL,           -- IANA id, e.g. Asia/Kolkata
  city        VARCHAR(64),
  hub_id      BIGINT,
  order_count INT          NOT NULL DEFAULT 0,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL
);

CREATE TABLE campaigns (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(64)  NOT NULL UNIQUE,      -- DIWALI_26
  name       VARCHAR(200) NOT NULL,
  city       VARCHAR(64),
  active     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at DATETIME(6)  NOT NULL
);

CREATE TABLE message_templates (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(64)   NOT NULL UNIQUE,    -- ORDER_SHIPPED
  type        VARCHAR(20)   NOT NULL,           -- TRANSACTIONAL | MARKETING
  campaign_id BIGINT,                           -- dedupe key; null = no campaign
  title       VARCHAR(200)  NOT NULL,           -- 'Hi {{user_name}}, your order is on the way'
  body        VARCHAR(1000) NOT NULL,           -- 'Order #{{order_id}} arrives by {{eta}}'
  deep_link   VARCHAR(500),                     -- 'app://orders/{{order_id}}'
  created_at  DATETIME(6)   NOT NULL,
  CONSTRAINT fk_tpl_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE TABLE rules (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100)  NOT NULL UNIQUE,
  description VARCHAR(500),
  priority    INT           NOT NULL,           -- lower runs first
  expression  VARCHAR(1000) NOT NULL,           -- SpEL boolean over NotificationFacts
  action      VARCHAR(10)   NOT NULL,           -- BLOCK | ALLOW
  enabled     BOOLEAN       NOT NULL,
  system_rule BOOLEAN       NOT NULL,           -- seeded: editable, not deletable
  version     BIGINT        NOT NULL,           -- JPA @Version: optimistic lock + cache key
  created_at  DATETIME(6)   NOT NULL,
  updated_at  DATETIME(6)   NOT NULL
);

CREATE TABLE notification_decisions (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  idempotency_key VARCHAR(100) UNIQUE,          -- nullable; multiple NULLs allowed
  user_id         BIGINT       NOT NULL,
  template_id     BIGINT       NOT NULL,
  message_type    VARCHAR(20)  NOT NULL,
  decision        VARCHAR(10)  NOT NULL,        -- ALLOWED | BLOCKED
  rule_id         BIGINT,                       -- soft ref: rules can be deleted
  rule_name       VARCHAR(100),                 -- snapshot for audit
  reason          VARCHAR(500) NOT NULL,
  variables       VARCHAR(2000),                -- JSON of request variables
  trace           VARCHAR(8000),                -- JSON array of per-rule evaluations
  decided_at      DATETIME(6)  NOT NULL,        -- the "now" used for evaluation
  created_at      DATETIME(6)  NOT NULL,
  CONSTRAINT fk_nd_user     FOREIGN KEY (user_id)     REFERENCES users(id),
  CONSTRAINT fk_nd_template FOREIGN KEY (template_id) REFERENCES message_templates(id)
);
CREATE INDEX idx_nd_user_time ON notification_decisions (user_id, decided_at);

CREATE TABLE notifications (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  decision_id  BIGINT        NOT NULL UNIQUE,
  user_id      BIGINT        NOT NULL,
  template_id  BIGINT        NOT NULL,
  campaign_id  BIGINT,                          -- copied from template: dedupe key
  message_type VARCHAR(20)   NOT NULL,
  title        VARCHAR(300)  NOT NULL,          -- rendered
  body         VARCHAR(2000) NOT NULL,          -- rendered
  deep_link    VARCHAR(1000),                   -- rendered
  sent_at      DATETIME(6)   NOT NULL,          -- = decision.decided_at
  created_at   DATETIME(6)   NOT NULL,
  CONSTRAINT fk_n_decision FOREIGN KEY (decision_id) REFERENCES notification_decisions(id),
  CONSTRAINT fk_n_user     FOREIGN KEY (user_id)     REFERENCES users(id),
  CONSTRAINT fk_n_template FOREIGN KEY (template_id) REFERENCES message_templates(id),
  CONSTRAINT fk_n_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);
-- cap:    COUNT(*) WHERE user_id=? AND message_type='MARKETING' AND sent_at >= localMidnightUtc AND sent_at <= now
CREATE INDEX idx_n_user_type_time     ON notifications (user_id, message_type, sent_at);
-- dedupe: MAX(sent_at) WHERE user_id=? AND campaign_id=? AND sent_at <= now
CREATE INDEX idx_n_user_campaign_time ON notifications (user_id, campaign_id, sent_at);
```

A rendered text longer than its column → 400 `RENDERED_MESSAGE_TOO_LONG`. It is never silently truncated.

## Seed data

| Script | Location | Profiles | Content |
|---|---|---|---|
| `V2__system_rules.sql` | `db/migration` | all (prod too) | 3 system rules |
| `V3__sample_data.sql` | `db/sample` | default (H2) only | sample users, campaigns, templates |

| priority | name | expression | action |
|---|---|---|---|
| 10 | `QUIET_HOURS` | `localHour >= 22 or localHour < 8` | BLOCK |
| 20 | `DAILY_MARKETING_CAP` | `marketingSentToday >= 3` | BLOCK |
| 30 | `DUPLICATE_CAMPAIGN_24H` | `message.campaignCode != null and hoursSinceLastSameCampaign < 24` | BLOCK |

Sample templates:

| code | type | campaign | title | body |
|---|---|---|---|---|
| `ORDER_SHIPPED` | TRANSACTIONAL | — | `Hi {{user_name}}, your order is on the way` | `Order #{{order_id}} will reach you by {{eta}}.` |
| `ORDER_DELIVERED` | TRANSACTIONAL | — | `Order #{{order_id}} delivered` | `Enjoy, {{user_name}}! Rate your order in the app.` |
| `DIWALI_OFFER` | MARKETING | `DIWALI_26` | `Hi {{user_name}}, Diwali sale is live!` | `Flat {{discount}} off on your next order in {{user_city}}.` |
| `FLASH_SALE` | MARKETING | `FLASH_SALE_OCT` | `Flash sale for 2 hours` | `{{user_name}}, grab deals before {{ends_at}}.` |
| `WEEKEND_DEALS` | MARKETING | `WEEKEND_DEALS` | `Weekend deals` | `Fresh picks for {{user_city}} this weekend.` |
| `NEW_ARRIVALS` | MARKETING | — | `New arrivals` | `{{user_name}}, check what's new today.` |
