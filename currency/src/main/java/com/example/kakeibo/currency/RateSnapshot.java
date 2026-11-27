package com.example.kakeibo.currency;

import java.util.List;

/**
 * 為替画面に表示するレートのまとまりです。
 */
public record RateSnapshot(List<RateView> rates, Source source, String updatedAtLabel) {

    public enum Source {
        /** 外部 API から取得した最新のレート。 */
        LIVE,
        /** 外部 API に接続できず、DB に保存した前回のレートを表示。 */
        CACHE,
        /** DB にもレートがなく、参考の固定値を使用。 */
        FALLBACK
    }

    /** メモ: 画面表示用に、通貨コード・名称・旗・円換算レートをひとまとめにします。 */
    public record RateView(String code, String name, String flag, java.math.BigDecimal rate) {
    }

    /** メモ: 画面で表示元ごとの案内を切り替えるための判定です。 */
    public boolean isLive() {
        return source == Source.LIVE;
    }

    public boolean isCached() {
        return source == Source.CACHE;
    }

    public boolean isFallback() {
        return source == Source.FALLBACK;
    }
}
