import cn.liqing.BLiveClient;
import cn.liqing.DanmuHandler;
import cn.liqing.model.Danmu;
import cn.liqing.model.Gift;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BLiveClientTest {
    static final Logger LOGGER = LoggerFactory.getLogger(BLiveClientTest.class);

    @Test
    public void test() {
        var client = new BLiveClient(34348);
        client.danmuHandlers.add(new DanmuHandler() {
            @Override
            public boolean onDanmu(Danmu danmu) {
                if (danmu.user.fansMedal != null) {
                    LOGGER.info("[{}|{}]{}: {}",
                            danmu.user.fansMedal.name,
                            danmu.user.fansMedal.level,
                            danmu.user.name, danmu.body);
                } else {
                    LOGGER.info("{}:{}",
                            danmu.user.name, danmu.body);
                }
                return false;
            }

            @Override
            public boolean onGift(Gift gift) {
                LOGGER.info("{}:[礼物] {}*{} ￥{}",
                        gift.user.name, gift.name, gift.num, gift.price / 1000);
                return false;
            }
        });
    }
}
