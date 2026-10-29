-- ============================================================
-- レシート家計簿用の検索SQL（グラフ・一覧の元データ）
-- 1文ずつ、カーソルを置いて Ctrl + Enter で実行する
-- ============================================================

-- 1) 支出の一覧（日付順）
SELECT expense_date, item_name, category, amount_jpy, original_currency, original_amount
FROM expenses
WHERE user_id = 'demo-device-001'
ORDER BY expense_date, id;

-- 2) 日別の合計（折れ線・棒グラフ用）
SELECT expense_date, SUM(amount_jpy) AS total_jpy
FROM expenses
WHERE user_id = 'demo-device-001'
GROUP BY expense_date
ORDER BY expense_date;

-- 3) カテゴリ別の合計（円グラフ用）
SELECT category, SUM(amount_jpy) AS total_jpy
FROM expenses
WHERE user_id = 'demo-device-001'
GROUP BY category
ORDER BY total_jpy DESC;

-- 4) 全体の合計
SELECT SUM(amount_jpy) AS total_jpy
FROM expenses
WHERE user_id = 'demo-device-001';

-- 5) 外貨の支出と為替レートの突き合わせ（換算の確認用）
SELECT e.item_name, e.original_currency, e.original_amount,
       r.rate_to_jpy, e.original_amount * r.rate_to_jpy AS calc_jpy, e.amount_jpy
FROM expenses e
JOIN exchange_rates r ON e.original_currency = r.currency_code
WHERE e.original_currency <> 'JPY';
