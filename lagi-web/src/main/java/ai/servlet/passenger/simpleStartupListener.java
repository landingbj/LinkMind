package ai.servlet.passenger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用启动监听器
 * 负责启动Kafka消费者服务和Redis清理任务
 */
@WebListener
public class simpleStartupListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(simpleStartupListener.class);

    private KafkaConsumerService kafkaConsumerService;
    private RedisCleanupUtil redisCleanupUtil;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            logger.info("=== 应用启动监听器开始初始化 ===");
            logger.info("[SimpleStartupListener] 正在启动服务...");
            logger.info("[SimpleStartupListener] 日志配置 - LOG_INFO={}, LOG_DEBUG={}, LOG_ERROR={}", Config.LOG_INFO, Config.LOG_DEBUG, Config.LOG_ERROR);
            
            // 打印Kafka配置信息
            logger.info("[SimpleStartupListener] 📡 Kafka配置信息:");
            logger.info("   Bootstrap Servers: {}", KafkaConfig.BOOTSTRAP_SERVERS);
            logger.info("   GPS主题: {}", KafkaConfig.BUS_GPS_TOPIC);
            logger.info("   票务主题: {}", KafkaConfig.TICKET_TOPIC);
            logger.info("   客流分析主题: {}", KafkaConfig.PASSENGER_FLOW_TOPIC);
            logger.info("   消费者组ID: {}", KafkaConfig.CONSUMER_GROUP_ID);
            
            // 启动Redis清理工具
            logger.info("[SimpleStartupListener] 正在启动Redis清理工具...");
            redisCleanupUtil = new RedisCleanupUtil();
            if (Config.LOG_INFO) {
                logger.info("[SimpleStartupListener] Redis清理工具启动成功");
            }

            // 启动Kafka消费者服务
            logger.info("[SimpleStartupListener] 正在启动Kafka消费者服务...");
            kafkaConsumerService = new KafkaConsumerService();
            logger.info("[SimpleStartupListener] KafkaConsumerService实例创建成功，正在调用start()方法...");
            kafkaConsumerService.start();
            if (Config.LOG_INFO) {
                logger.info("[SimpleStartupListener] Kafka消费者服务启动成功");
            }
            
            logger.info("=== 应用启动监听器初始化完成 ===");

        } catch (Exception e) {
            logger.error("[SimpleStartupListener] 启动服务失败: {}", e.getMessage(), e);
            if (Config.LOG_ERROR) {
                logger.error("[SimpleStartupListener] Failed to start services: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (Config.LOG_INFO) {
            logger.info("[SimpleStartupListener] Application context is being destroyed, stopping services...");
        }
        
        try {
            // 停止Kafka消费者服务
            if (kafkaConsumerService != null) {
                if (Config.LOG_INFO) {
                    logger.info("[SimpleStartupListener] Stopping Kafka consumer service...");
                }
                kafkaConsumerService.stop();
                if (Config.LOG_INFO) {
                    logger.info("[SimpleStartupListener] Kafka consumer service stopped");
                }
            }

            // 停止Redis清理工具
            if (redisCleanupUtil != null) {
                if (Config.LOG_INFO) {
                    logger.info("[SimpleStartupListener] Stopping Redis cleanup utility...");
                }
                redisCleanupUtil.shutdown();
                if (Config.LOG_INFO) {
                    logger.info("[SimpleStartupListener] Redis cleanup utility stopped");
                }
            }

            // 等待一段时间确保所有资源都被释放
            try {
                Thread.sleep(Config.APP_SHUTDOWN_WAIT_MS);
                if (Config.LOG_INFO) {
                    logger.info("[SimpleStartupListener] Waited {} seconds for resource cleanup", (Config.APP_SHUTDOWN_WAIT_MS / 1000));
                }
            } catch (InterruptedException e) {
                if (Config.LOG_ERROR) {
                    logger.error("[SimpleStartupListener] Interrupted while waiting for resource cleanup: {}", e.getMessage(), e);
                }
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[SimpleStartupListener] Error stopping services: {}", e.getMessage(), e);
            }
        } finally {
            if (Config.LOG_INFO) {
                logger.info("[SimpleStartupListener] All services stopped, context destruction complete");
            }
        }
    }
}
