package ai.servlet.passenger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * 应用启动监听器
 * 负责启动Kafka消费者服务和Redis清理任务
 */
@WebListener
public class simpleStartupListener implements ServletContextListener {

    private KafkaConsumerService kafkaConsumerService;
    private RedisCleanupUtil redisCleanupUtil;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            System.out.println("=== 应用启动监听器开始初始化 ===");
            System.out.println("[SimpleStartupListener] 正在启动服务...");
            System.out.println("[SimpleStartupListener] 日志配置 - LOG_INFO=" + Config.LOG_INFO + ", LOG_DEBUG=" + Config.LOG_DEBUG + ", LOG_ERROR=" + Config.LOG_ERROR);
            
            // 启动Redis清理工具
            System.out.println("[SimpleStartupListener] 正在启动Redis清理工具...");
            redisCleanupUtil = new RedisCleanupUtil();
            if (Config.LOG_INFO) {
                System.out.println("[SimpleStartupListener] Redis清理工具启动成功");
            }

            // 启动Kafka消费者服务
            System.out.println("[SimpleStartupListener] 正在启动Kafka消费者服务...");
            kafkaConsumerService = new KafkaConsumerService();
            System.out.println("[SimpleStartupListener] KafkaConsumerService实例创建成功，正在调用start()方法...");
            kafkaConsumerService.start();
            if (Config.LOG_INFO) {
                System.out.println("[SimpleStartupListener] Kafka消费者服务启动成功");
            }
            
            System.out.println("=== 应用启动监听器初始化完成 ===");

        } catch (Exception e) {
            System.err.println("[SimpleStartupListener] 启动服务失败: " + e.getMessage());
            e.printStackTrace();
            if (Config.LOG_ERROR) {
                System.err.println("[SimpleStartupListener] Failed to start services: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (Config.LOG_INFO) {
            System.out.println("[SimpleStartupListener] Application context is being destroyed, stopping services...");
        }
        
        try {
            // 停止Kafka消费者服务
            if (kafkaConsumerService != null) {
                if (Config.LOG_INFO) {
                    System.out.println("[SimpleStartupListener] Stopping Kafka consumer service...");
                }
                kafkaConsumerService.stop();
                if (Config.LOG_INFO) {
                    System.out.println("[SimpleStartupListener] Kafka consumer service stopped");
                }
            }

            // 停止Redis清理工具
            if (redisCleanupUtil != null) {
                if (Config.LOG_INFO) {
                    System.out.println("[SimpleStartupListener] Stopping Redis cleanup utility...");
                }
                redisCleanupUtil.shutdown();
                if (Config.LOG_INFO) {
                    System.out.println("[SimpleStartupListener] Redis cleanup utility stopped");
                }
            }

            // 等待一段时间确保所有资源都被释放
            try {
                Thread.sleep(Config.APP_SHUTDOWN_WAIT_MS);
                if (Config.LOG_INFO) {
                    System.out.println("[SimpleStartupListener] Waited " + (Config.APP_SHUTDOWN_WAIT_MS / 1000) + " seconds for resource cleanup");
                }
            } catch (InterruptedException e) {
                if (Config.LOG_ERROR) {
                    System.err.println("[SimpleStartupListener] Interrupted while waiting for resource cleanup: " + e.getMessage());
                }
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[SimpleStartupListener] Error stopping services: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            if (Config.LOG_INFO) {
                System.out.println("[SimpleStartupListener] All services stopped, context destruction complete");
            }
        }
    }
}
