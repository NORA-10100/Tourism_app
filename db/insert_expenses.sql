-- ============================================================
-- レシート家計簿用サンプルデータ（expenses）
-- 先に 01_create_tables.sql と 02_insert_data.sql（為替レート）を実行しておく
-- カテゴリ名はサンプル。Java側・設計書で決めた名前に合わせて直す
-- ============================================================

-- 1日目（2026-10-20）
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', 'ソーキそば', 1200, '食事', DATE '2026-10-20');
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', 'ゆいレール1日乗車券', 800, '交通', DATE '2026-10-20');
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', 'ホテル宿泊（1泊）', 9800, '宿泊', DATE '2026-10-20');
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', '首里城 入場料', 400, '観光', DATE '2026-10-20');

-- 2日目（2026-10-21）
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', '朝食 カフェ', 850, '食事', DATE '2026-10-21');
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', 'レンタカー', 6500, '交通', DATE '2026-10-21');
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', '美ら海水族館 入場券', 2180, '観光', DATE '2026-10-21');
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', 'ちんすこう', 1500, 'お土産', DATE '2026-10-21');

-- 3日目（2026-10-22）
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', '海ぶどう丼', 1600, '食事', DATE '2026-10-22');
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date)
VALUES ('demo-device-001', 'ホテル宿泊（1泊）', 9800, '宿泊', DATE '2026-10-22');

-- 外貨の支出（amount_jpy は 元金額 × レート。USD=150, KRW=0.11）
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date, original_currency, original_amount)
VALUES ('demo-device-001', 'Tシャツ（USD払い）', 3000, 'お土産', DATE '2026-10-21', 'USD', 20);
INSERT INTO expenses (user_id, item_name, amount_jpy, category, expense_date, original_currency, original_amount)
VALUES ('demo-device-001', 'カフェ（KRW払い）', 5500, '食事', DATE '2026-10-22', 'KRW', 50000);

COMMIT;
