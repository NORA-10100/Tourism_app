package com.example.kakeibo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.kakeibo.service.TextractService;

class ReceiptOcrServiceTests {

    private ReceiptOcrService service(List<String> lines) {
        return new ReceiptOcrService(new TextractService(null) {
            @Override
            public List<String> readText(byte[] imageBytes) {
                return lines;
            }
        });
    }

    private ReceiptOcrService.ReceiptData read(List<String> lines) {
        return service(lines).read(new byte[] { 1 }, "image/jpeg");
    }

    @Test
    void readsSevenElevenReceiptWhereTotalIsRecognizedAsKeiOnly() {
        List<String> lines = List.of(
                "SEVEN&IHOLDINGS",
                "了i",
                "セブンーイレブン",
                "那覇泊 2 丁目店 丁",
                "沖縄県那覇市泊 2 丁目 6 番 7 号",
                "電話: 098-866-2900",
                "レジ#2",
                "事業者登録番号T9810215781499",
                "2026年11月06日 (金) 13:10 責099",
                "領 収 書",
                "7P コーンスナック チーズピザ味",
                "*89",
                "フルタ セコイヤチョコレート ミルク",
                "*60",
                "小 計 (税抜 8%)",
                "\\149",
                "消費税等 ( 8% )",
                "\\11",
                "言十",
                "\\160",
                "(税率 8%対象",
                "\\160)",
                "(内消費税等 8%",
                "\\11)");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("160"), data.amount());
        assertEquals(LocalDate.of(2026, 11, 6), data.date());
        assertEquals("7P コーンスナック チーズピザ味", data.itemName());
        assertEquals("食事", data.category());
    }

    @Test
    void readsReceiptWhereTotalLabelLostTheFirstKanji() {
        List<String> lines = List.of(
                "LAWSON",
                "神戸新開地四丁目店",
                "登録番号; T5810898647474",
                "兵庫県神戸市兵庫区新開地 4-6",
                "-17",
                "電話; 018-521-5556 店コード; 287332",
                "2020年 4月24日 (?) 19:10",
                "レジ #1 98199",
                "貴;大塚",
                "【領収証】",
                "タイランド FJ サバ ミズニ 120G",
                "108軽",
                "言十",
                "\\1, 187",
                "(内消費税等",
                "\\93)");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("1187"), data.amount());
        assertEquals(LocalDate.of(2020, 4, 24), data.date());
        assertEquals("タイランド FJ サバ ミズニ 120G", data.itemName());
    }

    @Test
    void ignoresTaxRateWhenTotalAndTaxRateShareALine() {
        List<String> lines = List.of(
                "SHOP",
                "お買上ありがとうございます。",
                "食パン",
                "\\111",
                "合計 (税込 8%) \\1,160",
                "お預かり \\2,000",
                "お釣り \\840");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("1160"), data.amount());
    }

    @Test
    void readsFullWidthAmountAndJapaneseDate() {
        List<String> lines = List.of(
                "SHOP",
                "食パン",
                "\\111",
                "合計 ￥１，１８０",
                "2026年 11月 6日");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("1180"), data.amount());
        assertEquals(LocalDate.of(2026, 11, 6), data.date());
    }

    @Test
    void doesNotUseSubtotalAsTotal() {
        List<String> lines = List.of(
                "SHOP",
                "食パン",
                "\\111",
                "小計 \\111",
                "消費税 \\8",
                "合計 \\119");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("119"), data.amount());
    }

    @Test
    void readsReceiptWhenTotalLabelIsSplitAcrossTwoLines() {
        List<String> lines = List.of(
                "SHOP",
                "食パン",
                "\\111",
                "合",
                "計",
                "\\120");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("120"), data.amount());
    }

    @Test
    void readsTotalWhenTaxRateFollowsAmountOnSameLine() {
        List<String> lines = List.of(
                "SHOP",
                "食パン",
                "\\111",
                "合計 \\160 8%");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("160"), data.amount());
    }

    @Test
    void readsTotalWhenAmountFollowsKeiOnSameLine() {
        List<String> lines = List.of(
                "SHOP",
                "食パン",
                "108軽",
                "言十 \\1,187",
                "(内消費税等 \\93)");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("1187"), data.amount());
    }

    @Test
    void readsTotalWhenTaxRateIsJoinedToAmount() {
        // 実際のTextract出力: 「合計 ￥518 8%」が「合計 ¥5188%」になる
        List<String> lines = List.of(
                "テストマート 那覇店",
                "おにぎり ¥150",
                "お茶 ¥110",
                "サンド ¥220",
                "小計 ¥480",
                "消費税 8% ¥38",
                "合計 ¥5188%",
                "お預かり ¥1,000",
                "お釣り ¥482");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("518"), data.amount());
    }

    @Test
    void readsTotalWhenSpaceRemainsBeforePercent() {
        List<String> lines = List.of(
                "SHOP",
                "食パン ¥111",
                "合計 ¥5188 %");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("518"), data.amount());
    }

    @Test
    void usesLargestAmountWhenTotalLabelCannotBeRead() {
        List<String> lines = List.of(
                "SHOP",
                "食パン",
                "\\111",
                "おにぎり",
                "\\160",
                "お茶",
                "\\120",
                "小計 \\391",
                "消費税 \\31",
                "お預かり \\500");

        ReceiptOcrService.ReceiptData data = read(lines);

        assertEquals(new BigDecimal("160"), data.amount());
    }
}
