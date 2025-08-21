package ai.servlet.passenger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * 应用启动监听器
 * 负责启动Kafka消费者服务和Redis清理任务
 */
@WebListener
public class impleStartupListener implements ServletContextListener {

    private KafkaConsumerService kafkaConsumerService;
    private RedisCleanupUtil redisCleanupUtil;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            // 启动Redis清理工具
            redisCleanupUtil = new RedisCleanupUtil();
            if (Config.LOG_INFO) {
                System.out.println("[SimpleStartupListener] Redis cleanup utility started");
            }

            // 启动Kafka消费者服务
            kafkaConsumerService = new KafkaConsumerService();
            kafkaConsumerService.start();
            if (Config.LOG_INFO) {
                System.out.println("[SimpleStartupListener] Kafka consumer service started");
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[SimpleStartupListener] Failed to start services: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            // 停止Kafka消费者服务
            if (kafkaConsumerService != null) {
                kafkaConsumerService.stop();
                if (Config.LOG_INFO) {
                    System.out.println("[SimpleStartupListener] Kafka consumer service stopped");
                }
            }

            // 停止Redis清理工具
            if (redisCleanupUtil != null) {
                redisCleanupUtil.shutdown();
                if (Config.LOG_INFO) {
                    System.out.println("[SimpleStartupListener] Redis cleanup utility stopped");
                }
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[SimpleStartupListener] Error stopping services: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
