package ai.servlet.passenger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * 简化版启动器，启动 Kafka 消费者
 * WebSocket 通过注解方式自动启动
 */
@WebListener
public class SimpleStartupListener implements ServletContextListener {

	private KafkaConsumerService kafkaConsumerService;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		System.out.println("正在启动应用服务...");

		try {
			// 启动Kafka消费者服务，消费多个主题
			System.out.println("正在启动Kafka消费者服务...");
			kafkaConsumerService = new KafkaConsumerService();
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

		System.out.println("应用服务已关闭");
	}
}