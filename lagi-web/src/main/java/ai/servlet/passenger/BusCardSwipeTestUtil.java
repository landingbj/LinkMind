package ai.servlet.passenger;

import com.google.gson.Gson;

/**
 * 刷卡数据测试工具类
 */
public class BusCardSwipeTestUtil {

    public static void main(String[] args) {
        // 测试JSON解析
        String jsonData = "{\"busSelfNo\":\"6-6445\",\"cardNo\":\"00003100000131025531\",\"cardType\":\"1500\",\"childCardType\":\"1500\",\"onOff\":\"\",\"tradeNo\":\"HZ1750168520250906123845553102\",\"tradeTime\":\"2025-09-06 12:38:45\"}";

        Gson gson = new Gson();
        BusCardSwipeData cardData = gson.fromJson(jsonData, BusCardSwipeData.class);

        System.out.println("解析结果:");
        System.out.println("车辆自编号: " + cardData.getBusSelfNo());
        System.out.println("卡号: " + cardData.getCardNo());
        System.out.println("卡类型: " + cardData.getCardType());
        System.out.println("子卡类型: " + cardData.getChildCardType());
        System.out.println("上下车: " + cardData.getOnOff());
        System.out.println("交易号: " + cardData.getTradeNo());
        System.out.println("交易时间: " + cardData.getTradeTime());

        // 测试数据库服务
        BusCardSwipeDbService dbService = new BusCardSwipeDbService();
        boolean testConnection = dbService.testConnection();
        System.out.println("数据库连接测试: " + (testConnection ? "成功" : "失败"));

        // 测试保存数据
        boolean saved = dbService.saveCardSwipeData(cardData);
        System.out.println("数据保存测试: " + (saved ? "成功" : "失败"));

        dbService.close();
    }
}




