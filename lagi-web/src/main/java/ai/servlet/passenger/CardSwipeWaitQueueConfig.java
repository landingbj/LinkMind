package ai.servlet.passenger;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 刷卡数据等待队列Redis配置
 * 用于配置等待队列相关的Redis队列和定时任务
 */
@Configuration
@EnableScheduling
public class CardSwipeWaitQueueConfig {

    // 等待队列Redis键前缀
    public static final String WAIT_QUEUE_KEY_PREFIX = "wait_queue_card_swipe:";

    /**
     * 生成等待队列Redis键
     * @param busNo 车辆编号
     * @return Redis键
     */
    public static String generateWaitQueueKey(String busNo) {
        return WAIT_QUEUE_KEY_PREFIX + busNo;
    }

    /**
     * 检查是否为等待队列键
     * @param key Redis键
     * @return 是否为等待队列键
     */
    public static boolean isWaitQueueKey(String key) {
        return key != null && key.startsWith(WAIT_QUEUE_KEY_PREFIX);
    }
}
