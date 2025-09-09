package ai.servlet.passenger;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 刷卡数据测试工具类
 */
public class BusCardSwipeTestUtil {

    private static final Logger logger = LoggerFactory.getLogger(BusCardSwipeTestUtil.class);

    public static void main(String[] args) {
        // 测试JSON解析
        String jsonData = "{\"busSelfNo\":\"6-6445\",\"cardNo\":\"00003100000131025531\",\"cardType\":\"1500\",\"childCardType\":\"1500\",\"onOff\":\"\",\"tradeNo\":\"HZ1750168520250906123845553102\",\"tradeTime\":\"2025-09-06 12:38:45\"}";

        Gson gson = new Gson();
        BusCardSwipeData cardData = gson.fromJson(jsonData, BusCardSwipeData.class);

        logger.info("解析结果:");
        logger.info("车辆自编号: " + cardData.getBusSelfNo());
        logger.info("卡号: " + cardData.getCardNo());
        logger.info("卡类型: " + cardData.getCardType());
        logger.info("子卡类型: " + cardData.getChildCardType());
        logger.info("上下车: " + cardData.getOnOff());
        logger.info("交易号: " + cardData.getTradeNo());
        logger.info("交易时间: " + cardData.getTradeTime());

        // 测试数据库服务
        BusCardSwipeDbService dbService = new BusCardSwipeDbService();
        boolean testConnection = dbService.testConnection();
        logger.info("数据库连接测试: " + (testConnection ? "成功" : "失败"));

        // 测试保存数据
        boolean saved = dbService.saveCardSwipeData(cardData);
        logger.info("数据保存测试: " + (saved ? "成功" : "失败"));

        dbService.close();
    }
}




