package ai.servlet.passenger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * 简化版启动器，启动 Kafka 消费者和 WebSocket 客户端
 * WebSocket 通过注解方式自动启动
 */
@WebListener
public class SimpleStartupListener implements ServletContextListener {

    private KafkaConsumerService kafkaConsumerService;
    private WsClientHandler wsClientHandler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("正在启动应用服务...");

        try {
            // 启动WebSocket客户端连接CV系统
            System.out.println("正在启动WebSocket客户端...");
            wsClientHandler = new WsClientHandler();
            wsClientHandler.connectBlocking();
            sce.getServletContext().setAttribute("wsClientHandler", wsClientHandler);
            System.out.println("WebSocket客户端启动成功！");

            // 启动Kafka消费者服务，消费多个主题
            System.out.println("正在启动Kafka消费者服务...");
            kafkaConsumerService = new KafkaConsumerService(wsClientHandler);
            kafkaConsumerService.start();
            sce.getServletContext().setAttribute("kafkaConsumerService", kafkaConsumerService);
            System.out.println("Kafka消费者服务启动成功！");

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

        // 停止Kafka消费者服务
        if (kafkaConsumerService != null) {
            try {
                kafkaConsumerService.stop();
                System.out.println("Kafka消费者服务已关闭");
            } catch (Exception e) {
                System.err.println("关闭Kafka消费者服务时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 关闭WebSocket客户端
        if (wsClientHandler != null) {
            try {
                wsClientHandler.closeBlocking();
                System.out.println("WebSocket客户端已关闭");
            } catch (Exception e) {
                System.err.println("关闭WebSocket客户端时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("应用服务已关闭");
    }
}