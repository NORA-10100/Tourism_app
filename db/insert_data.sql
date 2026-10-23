-- ============================================================
-- デモ用テストデータ（サンプル値。緯度経度・営業時間は概算なので、本番前に確認する）
-- 01_create_tables.sql を実行したあとに、スクリプトとして全部実行する
-- ============================================================

-- スポット（nameは多言語対応のJSON形式）
INSERT INTO spots (name, category, area, latitude, longitude, business_hours, access_info, crowd_score, review_score)
VALUES ('{"ja":"首里城公園","en":"Shuri Castle Park"}', '文化', '那覇市', 26.217000, 127.719700, '8:30-18:00', 'ゆいレール首里駅から徒歩約15分', 70, 4.3);

INSERT INTO spots (name, category, area, latitude, longitude, business_hours, access_info, crowd_score, review_score)
VALUES ('{"ja":"斎場御嶽","en":"Sefa Utaki"}', '文化', '南城市', 26.174400, 127.826900, '9:00-17:30', '那覇バスターミナルからバスで約1時間', 40, 4.1);

INSERT INTO spots (name, category, area, latitude, longitude, business_hours, access_info, crowd_score, review_score)
VALUES ('{"ja":"古宇利島","en":"Kouri Island"}', '自然', '今帰仁村', 26.696800, 128.024000, '24時間', '那覇空港から車で約1時間45分', 35, 4.5);

INSERT INTO spots (name, category, area, latitude, longitude, business_hours, access_info, crowd_score, review_score)
VALUES ('{"ja":"国際通り","en":"Kokusai Street"}', 'グルメ', '那覇市', 26.214700, 127.686500, '店舗により異なる', 'ゆいレール牧志駅から徒歩約5分', 85, 4.0);

INSERT INTO spots (name, category, area, latitude, longitude, business_hours, access_info, crowd_score, review_score)
VALUES ('{"ja":"シュノーケリング体験","en":"Snorkeling Experience"}', '体験', '恩納村', 26.505000, 127.850000, '9:00-17:00', '那覇空港から車で約1時間', 50, 4.4);

-- 為替レート（サンプル値）
INSERT INTO exchange_rates (currency_code, rate_to_jpy) VALUES ('USD', 150.0000);
INSERT INTO exchange_rates (currency_code, rate_to_jpy) VALUES ('EUR', 162.0000);
INSERT INTO exchange_rates (currency_code, rate_to_jpy) VALUES ('CNY', 21.0000);
INSERT INTO exchange_rates (currency_code, rate_to_jpy) VALUES ('KRW', 0.1100);

-- テスト用ユーザー（password_hash はダミー。本物のハッシュはJava側で作る）
INSERT INTO users (username, email, password_hash)
VALUES ('testuser', 'test@example.com', 'dummy_hash_for_demo');

COMMIT;
