INSERT INTO rules (name, description, priority, expression, action, enabled, system_rule, version, created_at, updated_at)
VALUES
  ('QUIET_HOURS', 'No marketing push 22:00-08:00 in the user''s timezone', 10,
   'localHour >= 22 or localHour < 8', 'BLOCK', TRUE, TRUE, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  ('DAILY_MARKETING_CAP', 'Max 3 marketing pushes per user per local day', 20,
   'marketingSentToday >= 3', 'BLOCK', TRUE, TRUE, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  ('DUPLICATE_CAMPAIGN_24H', 'No duplicate campaign within 24h', 30,
   'message.campaignCode != null and hoursSinceLastSameCampaign < 24', 'BLOCK', TRUE, TRUE, 0,
   '2026-01-01 00:00:00', '2026-01-01 00:00:00');
