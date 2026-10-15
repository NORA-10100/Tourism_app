-- Vita テーブル作成（vita_app_user で、サービス名 XEPDB1 の接続から実行する）
-- 実行順：users → spots → travel_plans → plan_spots → expenses → exchange_rates

-- a. 会員テーブル
CREATE TABLE users (
    id                 NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username           VARCHAR2(50)  NOT NULL,
    email              VARCHAR2(255) NOT NULL UNIQUE,
    password_hash      VARCHAR2(255) NOT NULL,
    preferred_language VARCHAR2(10)  DEFAULT 'ja',
    created_at         TIMESTAMP     DEFAULT SYSTIMESTAMP
);

-- b. スポットテーブル
CREATE TABLE spots (
    id             NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR2(400) NOT NULL,
    category       VARCHAR2(20)  NOT NULL,
    area           VARCHAR2(50)  NOT NULL,
    latitude       NUMBER(9,6)   NOT NULL,
    longitude      NUMBER(9,6)   NOT NULL,
    business_hours VARCHAR2(100),
    access_info    VARCHAR2(200),
    crowd_score    NUMBER        DEFAULT 0,
    review_score   NUMBER(3,1)   DEFAULT 0,
    delete_flag    NUMBER(1)     DEFAULT 0,
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP
);

-- c. 旅行プランテーブル
CREATE TABLE travel_plans (
    id         NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    NUMBER NOT NULL REFERENCES users(id),
    days       NUMBER NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- d. プラン内スポットテーブル
CREATE TABLE plan_spots (
    id           NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id      NUMBER NOT NULL REFERENCES travel_plans(id),
    spot_id      NUMBER NOT NULL REFERENCES spots(id),
    day_number   NUMBER NOT NULL,
    visit_order  NUMBER NOT NULL,
    stay_minutes NUMBER
);

-- e. 支出テーブル
CREATE TABLE expenses (
    id                NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           VARCHAR2(64)  NOT NULL,
    item_name         VARCHAR2(50)  NOT NULL,
    amount_jpy        NUMBER(10,2)  NOT NULL,
    category          VARCHAR2(30)  NOT NULL,
    expense_date      DATE          NOT NULL,
    original_currency VARCHAR2(3)   DEFAULT 'JPY',
    original_amount   NUMBER(10,2),
    created_at        TIMESTAMP     DEFAULT SYSTIMESTAMP
);

-- f. 為替レートテーブル
CREATE TABLE exchange_rates (
    id            NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    currency_code VARCHAR2(3)  NOT NULL UNIQUE,
    rate_to_jpy   NUMBER(10,4) NOT NULL,
    updated_at    TIMESTAMP    DEFAULT SYSTIMESTAMP
);