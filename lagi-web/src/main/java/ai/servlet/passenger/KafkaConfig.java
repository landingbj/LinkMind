package ai.servlet.passenger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Kafka配置类
 */
public class KafkaConfig {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

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


    /**
     * 映射关系已删除，现在直接使用bus_id
     * @param busNo 车辆编号
     * @return 直接返回busNo
     */
    public static String getPlateNumber(String busNo) {
        return busNo; // 现在直接返回busNo，不再需要映射
    }

    /**
     * 映射关系已删除，现在直接使用bus_id
     * @param plateNumber 车牌号
     * @return 直接返回plateNumber
     */
    public static String getBusNoByPlate(String plateNumber) {
        return plateNumber; // 现在直接返回plateNumber，不再需要映射
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
