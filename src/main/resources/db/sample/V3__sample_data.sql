INSERT INTO users (name, timezone, city, hub_id, order_count, created_at, updated_at)
VALUES
  ('Aryant', 'Asia/Kolkata', 'Bengaluru', 1, 12, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
  ('Maya', 'America/New_York', 'New York', 7, 3, '2026-01-01 00:00:00', '2026-01-01 00:00:00');

INSERT INTO campaigns (code, name, city, active, created_at)
VALUES
  ('DIWALI_26', 'Diwali 2026', NULL, TRUE, '2026-01-01 00:00:00'),
  ('FLASH_SALE_OCT', 'October flash sale', NULL, TRUE, '2026-01-01 00:00:00'),
  ('WEEKEND_DEALS', 'Weekend deals', NULL, TRUE, '2026-01-01 00:00:00');

INSERT INTO message_templates (code, type, campaign_id, title, body, deep_link, created_at)
VALUES
  ('ORDER_SHIPPED', 'TRANSACTIONAL', NULL,
   'Hi {{user_name}}, your order is on the way', 'Order #{{order_id}} will reach you by {{eta}}.',
   'app://orders/{{order_id}}', '2026-01-01 00:00:00'),
  ('ORDER_DELIVERED', 'TRANSACTIONAL', NULL,
   'Order #{{order_id}} delivered', 'Enjoy, {{user_name}}! Rate your order in the app.',
   'app://orders/{{order_id}}', '2026-01-01 00:00:00'),
  ('DIWALI_OFFER', 'MARKETING', (SELECT id FROM campaigns WHERE code = 'DIWALI_26'),
   'Hi {{user_name}}, Diwali sale is live!', 'Flat {{discount}} off on your next order in {{user_city}}.',
   'app://offers/diwali', '2026-01-01 00:00:00'),
  ('FLASH_SALE', 'MARKETING', (SELECT id FROM campaigns WHERE code = 'FLASH_SALE_OCT'),
   'Flash sale for 2 hours', '{{user_name}}, grab deals before {{ends_at}}.',
   'app://offers/flash-sale', '2026-01-01 00:00:00'),
  ('WEEKEND_DEALS', 'MARKETING', (SELECT id FROM campaigns WHERE code = 'WEEKEND_DEALS'),
   'Weekend deals', 'Fresh picks for {{user_city}} this weekend.',
   'app://offers/weekend', '2026-01-01 00:00:00'),
  ('NEW_ARRIVALS', 'MARKETING', NULL,
   'New arrivals', '{{user_name}}, check what''s new today.',
   'app://new-arrivals', '2026-01-01 00:00:00');
