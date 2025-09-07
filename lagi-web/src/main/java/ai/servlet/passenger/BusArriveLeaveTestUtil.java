package ai.servlet.passenger;

import com.google.gson.Gson;

/**
 * 到离站数据测试工具类
 */
public class BusArriveLeaveTestUtil {

    public static void main(String[] args) {
        // 测试JSON解析 - 模拟到站数据
        String arriveJsonData = "{\"busNo\":\"6-6445\",\"busSelfNo\":\"6-6445\",\"busId\":12345,\"isArriveOrLeft\":\"1\",\"stationId\":\"3301000101243477\",\"stationName\":\"清河坊\",\"nextStationSeqNum\":\"5\",\"routeNo\":\"1001000021\",\"trafficType\":\"4\",\"srcAddr\":\"192.168.1.100\",\"seqNum\":100001,\"packetTime\":1693965525000,\"pktType\":4}";

        // 测试JSON解析 - 模拟离站数据
        String leaveJsonData = "{\"busNo\":\"6-6445\",\"busSelfNo\":\"6-6445\",\"busId\":12345,\"isArriveOrLeft\":\"2\",\"stationId\":\"3301000101243477\",\"stationName\":\"清河坊\",\"nextStationSeqNum\":\"6\",\"routeNo\":\"1001000021\",\"trafficType\":\"4\",\"srcAddr\":\"192.168.1.100\",\"seqNum\":100002,\"packetTime\":1693965590000,\"pktType\":4}";

        Gson gson = new Gson();

        System.out.println("=== 测试到站数据解析 ===");
        BusArriveLeaveData arriveData = gson.fromJson(arriveJsonData, BusArriveLeaveData.class);
        arriveData.setDirection("up"); // 4=上行
        arriveData.setOriginalMessage(arriveJsonData);

        System.out.println("车辆编号: " + arriveData.getBusNo());
        System.out.println("到离站标识: " + arriveData.getIsArriveOrLeft() + " (1=到站)");
        System.out.println("站点名称: " + arriveData.getStationName());
        System.out.println("线路编号: " + arriveData.getRouteNo());
        System.out.println("方向: " + arriveData.getDirection());

        System.out.println("\n=== 测试离站数据解析 ===");
        BusArriveLeaveData leaveData = gson.fromJson(leaveJsonData, BusArriveLeaveData.class);
        leaveData.setDirection("up"); // 4=上行
        leaveData.setOriginalMessage(leaveJsonData);

        System.out.println("车辆编号: " + leaveData.getBusNo());
        System.out.println("到离站标识: " + leaveData.getIsArriveOrLeft() + " (2=离站)");
        System.out.println("站点名称: " + leaveData.getStationName());
        System.out.println("线路编号: " + leaveData.getRouteNo());
        System.out.println("方向: " + leaveData.getDirection());

        // 测试数据库服务
        System.out.println("\n=== 测试数据库连接 ===");
        BusArriveLeaveDbService dbService = new BusArriveLeaveDbService();
        boolean testConnection = dbService.testConnection();
        System.out.println("数据库连接测试: " + (testConnection ? "成功" : "失败"));

        if (testConnection) {
            // 测试保存到站数据
            System.out.println("\n=== 测试保存到站数据 ===");
            boolean savedArrive = dbService.saveArriveLeaveData(arriveData);
            System.out.println("到站数据保存测试: " + (savedArrive ? "成功" : "失败"));

            // 测试保存离站数据
            System.out.println("\n=== 测试保存离站数据 ===");
            boolean savedLeave = dbService.saveArriveLeaveData(leaveData);
            System.out.println("离站数据保存测试: " + (savedLeave ? "成功" : "失败"));
        }

        dbService.close();
        System.out.println("\n测试完成！");
    }
}
