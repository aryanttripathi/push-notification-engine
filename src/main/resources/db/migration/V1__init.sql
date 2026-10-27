CREATE TABLE users (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  timezone    VARCHAR(64)  NOT NULL,
  city        VARCHAR(64),
  hub_id      BIGINT,
  order_count INT          NOT NULL DEFAULT 0,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL
);

CREATE TABLE campaigns (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(64)  NOT NULL UNIQUE,
  name       VARCHAR(200) NOT NULL,
  city       VARCHAR(64),
  active     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at DATETIME(6)  NOT NULL
);

CREATE TABLE message_templates (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(64)   NOT NULL UNIQUE,
  type        VARCHAR(20)   NOT NULL,
  campaign_id BIGINT,
  title       VARCHAR(200)  NOT NULL,
  body        VARCHAR(1000) NOT NULL,
  deep_link   VARCHAR(500),
  created_at  DATETIME(6)   NOT NULL,
  CONSTRAINT fk_tpl_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE TABLE rules (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100)  NOT NULL UNIQUE,
  description VARCHAR(500),
  priority    INT           NOT NULL,
  expression  VARCHAR(1000) NOT NULL,
  action      VARCHAR(10)   NOT NULL,
  enabled     BOOLEAN       NOT NULL,
  system_rule BOOLEAN       NOT NULL,
  version     BIGINT        NOT NULL,
  created_at  DATETIME(6)   NOT NULL,
  updated_at  DATETIME(6)   NOT NULL
);

CREATE TABLE notification_decisions (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  idempotency_key VARCHAR(100) UNIQUE,
  user_id         BIGINT       NOT NULL,
  template_id     BIGINT       NOT NULL,
  message_type    VARCHAR(20)  NOT NULL,
  decision        VARCHAR(10)  NOT NULL,
  rule_id         BIGINT,
  rule_name       VARCHAR(100),
  reason          VARCHAR(500) NOT NULL,
  variables       VARCHAR(2000),
  trace           VARCHAR(8000),
  decided_at      DATETIME(6)  NOT NULL,
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
  campaign_id  BIGINT,
  message_type VARCHAR(20)   NOT NULL,
  title        VARCHAR(300)  NOT NULL,
  body         VARCHAR(2000) NOT NULL,
  deep_link    VARCHAR(1000),
  sent_at      DATETIME(6)   NOT NULL,
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
