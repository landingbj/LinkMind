package ai.servlet.passenger;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

/**
 * 简化版启动器，仅启动 Kafka 消费者
 * WebSocket 通过注解方式自动启动
 */
@WebListener
public class SimpleStartupListener implements ServletContextListener {

    private KafkaDoorStatusConsumer kafkaConsumer;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("正在启动应用服务...");

        try {
            // 启动Kafka消费者，不再需要传递WebSocket服务器实例
            System.out.println("正在启动Kafka消费者...");
            kafkaConsumer = new KafkaDoorStatusConsumer(
                    KafkaConfig.DOOR_STATUS_TOPIC,
                    KafkaConfig.BOOTSTRAP_SERVERS,
                    KafkaConfig.CONSUMER_GROUP_ID
            );
            kafkaConsumer.start();
            System.out.println("Kafka消费者启动成功！");

            // 将Kafka消费者实例存储到ServletContext中
            sce.getServletContext().setAttribute("kafkaConsumer", kafkaConsumer);

            System.out.println("WebSocket 端点已通过注解自动配置");
            System.out.println("WebSocket 连接地址: ws://localhost:8081/passengerflow");
            System.out.println("外部连接地址: ws://20.17.39.66:8081/passengerflow");

        } catch (Exception e) {
            System.err.println("应用服务启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("正在关闭应用服务...");

        // 停止Kafka消费者
        if (kafkaConsumer != null) {
            try {
                kafkaConsumer.stop();
                System.out.println("Kafka消费者已关闭");
            } catch (Exception e) {
                System.err.println("关闭Kafka消费者时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("应用服务已关闭");
    }
}