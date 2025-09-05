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
        BUS_NO_TO_PLATE_MAP.put("2-6764", "浙A07071D");
        BUS_NO_TO_PLATE_MAP.put("2-8087", "浙A06517D");
        BUS_NO_TO_PLATE_MAP.put("2-8110", "浙A07559D");
        BUS_NO_TO_PLATE_MAP.put("2-8091", "浙A05705D");
        BUS_NO_TO_PLATE_MAP.put("2-8089", "浙A03231D");
        BUS_NO_TO_PLATE_MAP.put("2-6796", "浙A03579D");
        BUS_NO_TO_PLATE_MAP.put("2-9181", "浙A07037D");
        BUS_NO_TO_PLATE_MAP.put("2-8198", "浙A06306D");
        BUS_NO_TO_PLATE_MAP.put("2-8119", "浙A06770D");
        BUS_NO_TO_PLATE_MAP.put("2-8118", "浙A02706D");
        BUS_NO_TO_PLATE_MAP.put("2-8117", "浙A02572D");
        BUS_NO_TO_PLATE_MAP.put("2-8116", "浙A05366D");
        BUS_NO_TO_PLATE_MAP.put("2-8115", "浙A03601D");
        BUS_NO_TO_PLATE_MAP.put("2-6769", "浙A05703D");
        BUS_NO_TO_PLATE_MAP.put("2-6761", "浙A07558D");
        BUS_NO_TO_PLATE_MAP.put("2-6766", "浙A00280D");
        BUS_NO_TO_PLATE_MAP.put("2-6763", "浙A06434D");
        BUS_NO_TO_PLATE_MAP.put("2-6765", "浙A02319D");
        BUS_NO_TO_PLATE_MAP.put("2-6713", "浙A08959D");
        BUS_NO_TO_PLATE_MAP.put("2-9049", "浙A01149D");
        BUS_NO_TO_PLATE_MAP.put("2-9050", "浙A06063D");
        BUS_NO_TO_PLATE_MAP.put("2-8241", "浙A2H400");
        BUS_NO_TO_PLATE_MAP.put("2-8249", "浙A2H408");
        BUS_NO_TO_PLATE_MAP.put("2-9059", "浙A05679D");
        BUS_NO_TO_PLATE_MAP.put("2-9058", "浙A07311D");
        BUS_NO_TO_PLATE_MAP.put("2-9057", "浙A08700D");
        BUS_NO_TO_PLATE_MAP.put("2-8113", "浙A02899D");
        BUS_NO_TO_PLATE_MAP.put("2-8114", "浙A00581D");
        BUS_NO_TO_PLATE_MAP.put("2-8107", "浙A02781D");
        BUS_NO_TO_PLATE_MAP.put("2-8112", "浙A06077D");
        BUS_NO_TO_PLATE_MAP.put("8-9116", "浙A03322D");
        BUS_NO_TO_PLATE_MAP.put("8-9117", "浙A09706D");
        BUS_NO_TO_PLATE_MAP.put("8-6161", "浙A33735D");
        BUS_NO_TO_PLATE_MAP.put("8-6162", "浙A05150D");
        BUS_NO_TO_PLATE_MAP.put("8-6163", "浙A06027D");
        BUS_NO_TO_PLATE_MAP.put("8-6164", "浙A06797D");
        BUS_NO_TO_PLATE_MAP.put("8-9118", "浙A00110D");
        BUS_NO_TO_PLATE_MAP.put("8-6178", "浙A31058D");
        BUS_NO_TO_PLATE_MAP.put("8-6177", "浙A02368D");
        BUS_NO_TO_PLATE_MAP.put("8-6176", "浙A00683D");
        BUS_NO_TO_PLATE_MAP.put("8-6175", "浙A00921D");
        BUS_NO_TO_PLATE_MAP.put("8-6174", "浙A05822D");
        BUS_NO_TO_PLATE_MAP.put("8-6173", "浙A00583D");
        BUS_NO_TO_PLATE_MAP.put("8-6172", "浙A30125D");
        BUS_NO_TO_PLATE_MAP.put("8-6171", "浙A05761D");
        BUS_NO_TO_PLATE_MAP.put("8-6170", "浙A02179D");
        BUS_NO_TO_PLATE_MAP.put("8-6169", "浙A07735D");
        BUS_NO_TO_PLATE_MAP.put("8-6168", "浙A31732D");
        BUS_NO_TO_PLATE_MAP.put("8-6062", "浙A31562D");
        BUS_NO_TO_PLATE_MAP.put("8-9081", "浙A01890D");
        BUS_NO_TO_PLATE_MAP.put("8-6053", "浙A09943D");
        BUS_NO_TO_PLATE_MAP.put("8-9070", "浙A09167D");
        BUS_NO_TO_PLATE_MAP.put("8-8065", "浙A00150D");
        BUS_NO_TO_PLATE_MAP.put("8-8062", "浙A01788D");
        BUS_NO_TO_PLATE_MAP.put("8-8060", "浙A00159D");
        BUS_NO_TO_PLATE_MAP.put("8-6195", "浙A06802D");
        BUS_NO_TO_PLATE_MAP.put("8-6194", "浙A05651D");
        BUS_NO_TO_PLATE_MAP.put("8-6193", "浙A00269D");
        BUS_NO_TO_PLATE_MAP.put("8-6192", "浙A05325D");
        BUS_NO_TO_PLATE_MAP.put("8-6191", "浙A05731D");

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
