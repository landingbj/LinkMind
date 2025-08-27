package ai.servlet.passenger;

import java.util.HashMap;
import java.util.Map;

/**
 * 车辆编号与车牌号映射工具类
 * 提供bus_no和车牌号之间的双向转换功能
 */
public class BusPlateMappingUtil {
    
    // bus_no到车牌号的映射关系
    private static final Map<String, String> BUS_NO_TO_PLATE_MAP = new HashMap<>();
    
    // 车牌号到bus_no的映射关系（反向映射）
    private static final Map<String, String> PLATE_TO_BUS_NO_MAP = new HashMap<>();
    
    static {
        // 初始化映射关系
        BUS_NO_TO_PLATE_MAP.put("2-8091", "浙A05705D");
        BUS_NO_TO_PLATE_MAP.put("2-8089", "浙A03231D");
        BUS_NO_TO_PLATE_MAP.put("2-8117", "浙A02572D");
        BUS_NO_TO_PLATE_MAP.put("2-8116", "浙A05366D");
        BUS_NO_TO_PLATE_MAP.put("2-9050", "浙A06063D");
        BUS_NO_TO_PLATE_MAP.put("2-9059", "浙A05679D");
        BUS_NO_TO_PLATE_MAP.put("8-6161", "浙A33735D");
        BUS_NO_TO_PLATE_MAP.put("8-6162", "浙A05150D");
        BUS_NO_TO_PLATE_MAP.put("8-6173", "浙A00583D");
        BUS_NO_TO_PLATE_MAP.put("8-6172", "浙A30125D");
        BUS_NO_TO_PLATE_MAP.put("8-8065", "浙A00150D");
        BUS_NO_TO_PLATE_MAP.put("8-8062", "浙A01788D");
        
        // 建立反向映射
        for (Map.Entry<String, String> entry : BUS_NO_TO_PLATE_MAP.entrySet()) {
            PLATE_TO_BUS_NO_MAP.put(entry.getValue(), entry.getKey());
        }
    }
    
    /**
     * 根据bus_no获取对应的车牌号
     * @param busNo 车辆编号
     * @return 车牌号，如果未找到则返回原bus_no
     */
    public static String getPlateNumber(String busNo) {
        if (busNo == null || busNo.trim().isEmpty()) {
            return busNo;
        }
        return BUS_NO_TO_PLATE_MAP.getOrDefault(busNo.trim(), busNo);
    }
    
    /**
     * 根据车牌号获取对应的bus_no
     * @param plateNumber 车牌号
     * @return bus_no，如果未找到则返回null
     */
    public static String getBusNoByPlate(String plateNumber) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) {
            return null;
        }
        return PLATE_TO_BUS_NO_MAP.get(plateNumber.trim());
    }
    
    /**
     * 检查给定的bus_no是否在白名单中
     * @param busNo 车辆编号
     * @return 是否在白名单中
     */
    public static boolean isWhitelistedBusNo(String busNo) {
        if (busNo == null || busNo.trim().isEmpty()) {
            return false;
        }
        return BUS_NO_TO_PLATE_MAP.containsKey(busNo.trim());
    }
    
    /**
     * 检查给定的车牌号是否在白名单中
     * @param plateNumber 车牌号
     * @return 是否在白名单中
     */
    public static boolean isWhitelistedPlate(String plateNumber) {
        if (plateNumber == null || plateNumber.trim().isEmpty()) {
            return false;
        }
        return PLATE_TO_BUS_NO_MAP.containsKey(plateNumber.trim());
    }
    
    /**
     * 获取所有白名单车辆信息
     * @return 包含所有映射关系的Map副本
     */
    public static Map<String, String> getAllMappings() {
        return new HashMap<>(BUS_NO_TO_PLATE_MAP);
    }
    
    /**
     * 获取白名单车辆数量
     * @return 白名单车辆总数
     */
    public static int getWhitelistSize() {
        return BUS_NO_TO_PLATE_MAP.size();
    }
    
    /**
     * 打印所有映射关系（用于调试）
     */
    public static void printAllMappings() {
        System.out.println("[BusPlateMappingUtil] 当前白名单车辆映射关系:");
        System.out.println("   ================================================================================");
        for (Map.Entry<String, String> entry : BUS_NO_TO_PLATE_MAP.entrySet()) {
            System.out.println("   " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println("   ================================================================================");
        System.out.println("   总计: " + getWhitelistSize() + " 辆车");
    }
}
