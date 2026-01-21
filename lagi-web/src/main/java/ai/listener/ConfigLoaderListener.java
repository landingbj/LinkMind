package ai.listener;

import ai.config.ContextLoader;
import ai.config.UploadConfig;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@Slf4j
@WebListener
public class ConfigLoaderListener implements ServletContextListener {

    /**
     * Web应用启动时执行：加载配置文件并存入ServletContext
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext servletContext = sce.getServletContext();
        // 核心修改1：添加全局try-catch，捕获所有异常并记录详细日志
        try {
            servletContext.log("开始加载应用配置...");
            ContextLoader.loadContext();
            UploadConfig uploadConfig = ContextLoader.loadConfig("upload-config.yml", UploadConfig.class);
            ContextLoader.registerBean(UploadConfig.class, uploadConfig);
        } catch (Exception e) {
            // 捕获所有异常，记录详细日志并抛出，让Tomcat明确感知监听器启动失败
            String errorMsg = "ConfigLoaderListener监听器初始化失败";
            servletContext.log(errorMsg + "：" + e.getMessage());
            log.error(errorMsg, e); // 使用slf4j打印完整异常栈，便于排查
        }
    }

    /**
     * Web应用销毁时执行：清理资源（可选）
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext servletContext = sce.getServletContext();
        try {
            // 移除配置对象（可选，应用销毁时容器会自动清理）
            // servletContext.removeAttribute("appConfig");
            servletContext.log("配置资源清理完成");
            log.info("ConfigLoaderListener监听器销毁，配置资源已清理");
        } catch (Exception e) {
            log.error("销毁ConfigLoaderListener时发生异常", e);
            servletContext.log("清理配置资源失败：" + e.getMessage());
        }
    }
}