package ai.servlet.passenger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Kafka配置类
 */
public class KafkaConfig {

    // 从环境变量或配置文件读取，如果没有则使用默认值
    public static final String BOOTSTRAP_SERVERS = System.getenv("KAFKA_BOOTSTRAP_SERVERS") != null ?
            System.getenv("KAFKA_BOOTSTRAP_SERVERS") : "20.17.39.79:9092,20.17.39.80:9092,20.17.39.81:9092";

    // 车辆GPS与到离站放同一主题（通过pktType区分：3=gps, 4=到离站）
    public static final String BUS_GPS_TOPIC = "bus_gps";

    // 票务主题（刷卡数据）
    public static final String TICKET_TOPIC = "bus_card_swipe_data";

    // 客流分析OD主题（输出OD结果到Kafka）
    public static final String PASSENGER_FLOW_TOPIC = "passenger_flow_topic";

    // 消费者组ID
    public static final String CONSUMER_GROUP_ID = "passenger_flow_group";

    // bus_no到车牌号的映射关系
    public static final Map<String, String> BUS_NO_TO_PLATE_MAP = new HashMap<>();
    
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
    }

    /**
     * 根据bus_no获取对应的车牌号
     * @param busNo 车辆编号
     * @return 车牌号，如果未找到则返回原bus_no
     */
    public static String getPlateNumber(String busNo) {
        return BUS_NO_TO_PLATE_MAP.getOrDefault(busNo, busNo);
    }

    /**
     * 根据车牌号获取对应的bus_no
     * @param plateNumber 车牌号
     * @return bus_no，如果未找到则返回null
     */
    public static String getBusNoByPlate(String plateNumber) {
        for (Map.Entry<String, String> entry : BUS_NO_TO_PLATE_MAP.entrySet()) {
            if (entry.getValue().equals(plateNumber)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 获取Kafka消费者配置
     */
    public static Properties getConsumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // 消费者配置
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // 从最新偏移量开始消费
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true"); // 自动提交偏移量
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000"); // 自动提交间隔
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000"); // 会话超时时间
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "10000"); // 心跳间隔

        return props;
    }

    /**
     * 获取Kafka生产者配置
     */
    public static Properties getProducerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // 生产者配置
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // 等待所有副本确认
        props.put(ProducerConfig.RETRIES_CONFIG, "3"); // 重试次数
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384"); // 批处理大小
        props.put(ProducerConfig.LINGER_MS_CONFIG, "1"); // 延迟时间
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432"); // 缓冲区大小

        return props;
    }
}
