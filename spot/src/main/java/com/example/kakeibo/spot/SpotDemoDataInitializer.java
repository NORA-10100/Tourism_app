package com.example.kakeibo.spot;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 開発・デモ用のスポット初期データです。スポットが1件もないときだけ投入します。
 *
 * <p>本番（prod プロファイル）では実行しません。無効にする場合は
 * {@code vita.demo-data.enabled=false} を指定します。</p>
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "vita.demo-data", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SpotDemoDataInitializer implements ApplicationRunner {

    private final SpotRepository spotRepository;

    public SpotDemoDataInitializer(SpotRepository spotRepository) {
        this.spotRepository = spotRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // メモ: 手動登録データがある環境へ、デモ用スポットを重複投入しません。
        if (spotRepository.count() > 0) {
            return;
        }
        spotRepository.saveAll(spots());
    }

    private static List<Spot> spots() {
        return List.of(
                spot(names("首里城公園", "Shuri Castle Park", "首里城公园", "首里城公園", "슈리성 공원"),
                        "文化", "那覇市", "26.217000", "127.719700",
                        "8:30-18:00", "ゆいレール首里駅から徒歩約15分", 70, "4.3"),
                spot(names("斎場御嶽", "Sefa Utaki", "斋场御岳", "齋場御嶽", "세파 우타키"),
                        "文化", "南城市", "26.174400", "127.826900",
                        "9:00-17:30", "那覇バスターミナルからバスで約1時間", 40, "4.1"),
                spot(names("古宇利島", "Kouri Island", "古宇利岛", "古宇利島", "고우리섬"),
                        "自然", "今帰仁村", "26.696800", "128.024000",
                        "24時間", "那覇空港から車で約1時間45分", 35, "4.5"),
                spot(names("備瀬のフクギ並木", "Bise Fukugi Tree Road", "备濑福木林道", "備瀬福木林道", "비세 후쿠기 가로수길"),
                        "自然", "本部町", "26.705000", "127.879000",
                        "24時間", "沖縄美ら海水族館から徒歩約10分", 30, "4.4"),
                spot(names("万座毛", "Cape Manza", "万座毛", "萬座毛", "만자모"),
                        "自然", "恩納村", "26.505000", "127.851000",
                        "24時間", "那覇空港から車で約1時間", 55, "4.2"),
                spot(names("残波岬", "Cape Zanpa", "残波岬", "殘波岬", "잔파곶"),
                        "自然", "読谷村", "26.439000", "127.713000",
                        "24時間", "那覇空港から車で約1時間10分", 25, "4.3"),
                spot(names("美浜アメリカンビレッジ", "Mihama American Village", "美滨美国村", "美濱美國村", "미하마 아메리칸 빌리지"),
                        "グルメ", "北谷町", "26.316000", "127.754000",
                        "10:00-22:00", "那覇空港から車で約40分", 75, "4.2"),
                spot(names("国際通り", "Kokusai Street", "国际通", "國際通", "국제거리"),
                        "グルメ", "那覇市", "26.216000", "127.689000",
                        "24時間", "ゆいレール県庁前駅から徒歩約3分", 80, "4.0"),
                spot(names("瀬長島ウミカジテラス", "Senagajima Umikaji Terrace", "濑长岛海风露台", "瀬長島海風露台", "세나가섬 우미카지 테라스"),
                        "グルメ", "豊見城市", "26.176000", "127.646000",
                        "10:00-21:00", "那覇空港から車で約15分", 65, "4.4"),
                spot(names("やちむんの里", "Yachimun no Sato", "陶器之乡", "陶器之里", "야치문의 마을"),
                        "体験", "読谷村", "26.402000", "127.744000",
                        "9:00-18:00", "那覇空港から車で約1時間", 20, "4.5"),
                spot(names("琉球ガラス村", "Ryukyu Glass Village", "琉球玻璃村", "琉球玻璃村", "류큐 유리 마을"),
                        "体験", "糸満市", "26.109000", "127.664000",
                        "9:00-18:00", "那覇空港から車で約30分", 35, "4.2"),
                spot(names("勝連城跡", "Katsuren Castle Ruins", "胜连城迹", "勝連城跡", "가쓰렌성 터"),
                        "文化", "うるま市", "26.329000", "127.879000",
                        "9:00-18:00", "那覇空港から車で約50分", 30, "4.4"));
    }

    private static String names(String ja, String en, String zhCn, String zhTw, String ko) {
        // メモ: LocalizedText が読む言語コードをキーにしたJSONを作ります。
        return "{\"ja\":\"" + ja + "\",\"en\":\"" + en
                + "\",\"zh-CN\":\"" + zhCn + "\",\"zh-TW\":\"" + zhTw
                + "\",\"ko\":\"" + ko + "\"}";
    }

    private static Spot spot(String names, String category, String area,
            String latitude, String longitude, String businessHours,
            String accessInfo, int crowdScore, String reviewScore) {
        return new Spot(names, category, area,
                new BigDecimal(latitude), new BigDecimal(longitude),
                businessHours, accessInfo, crowdScore, new BigDecimal(reviewScore));
    }
}
